package com.songplus.cluster.vehicle

import android.content.Context
import com.songplus.cluster.data.ConnectionStatus
import com.songplus.cluster.data.DataMode
import com.songplus.cluster.data.EffectiveSource
import com.songplus.cluster.data.SettingsStore
import com.songplus.cluster.data.TripStore
import com.songplus.cluster.data.na
import com.songplus.cluster.data.VehicleState
import com.songplus.cluster.data.real
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single source of truth for vehicle data.
 *
 * - SIMULATION: only [SimEngine] values (all tagged SIM).
 * - BYD_ONLY: only BYD device values (REAL or UNAVAILABLE, never SIM).
 * - AUTO: BYD values when the core signal (speed) can be read, otherwise the simulator. Values are never mixed:
 *   the data is either entirely real or entirely simulated, and the status says which.
 */
class VehicleRepository(
    context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val tripStore: TripStore,
    private val obd2: Obd2Source
) {
    private val byd = BydAutoSource(context.applicationContext)
    private val engine = SimEngine()

    private val _state = MutableStateFlow(VehicleState.unavailable())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val _status = MutableStateFlow(ConnectionStatus.initial)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _simInputs = MutableStateFlow(SimInputs())
    val simInputs: StateFlow<SimInputs> = _simInputs.asStateFlow()

    private val plausibility = SpeedPlausibility()
    private val recorder = TripRecorder(
        config = {
            val s = settings.value
            TripConfig(s.tripAutoStopSec, s.tripMinKm, s.capacityKwh, s.recordSimTrips)
        },
        onTrip = { tripStore.add(it) }
    )

    private val _tripActive = MutableStateFlow(false)
    val tripActive: StateFlow<Boolean> = _tripActive.asStateFlow()

    private var job: Job? = null
    private var simTime = 0f

    fun updateSim(change: (SimInputs) -> SimInputs) {
        _simInputs.value = change(_simInputs.value)
    }

    fun resetTrip() = engine.resetTrip()

    /** Start polling. Safe to call repeatedly. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            var last = System.nanoTime()
            var count = 0
            var windowStart = last
            var hz = 0f
            while (isActive) {
                val s = settings.value
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.5f)
                last = now

                // OBD2 adapter: connect/disconnect to match the selected mode, so the serial socket is only held when used.
                if (s.dataMode == DataMode.OBD2) {
                    if (obd2.state.value == Obd2Source.State.IDLE && s.obd2Mac.isNotBlank()) obd2.connect(s.obd2Mac)
                } else if (obd2.state.value != Obd2Source.State.IDLE) {
                    obd2.stop()
                }

                val real: VehicleState? =
                    if (s.dataMode == DataMode.OBD2) obd2State(obd2.readings.value)
                    else if (s.dataMode != DataMode.SIMULATION && byd.classesPresent) byd.read() else null
                // A speed that sticks at zero while the odometer advances is not believable: treat it as unavailable.
                var checked = real
                var speedSuspect = false
                if (real != null && real.speedKmh.available) {
                    val odo = if (real.odoKm.available) real.odoKm.value else null
                    if (!plausibility.check(real.speedKmh.value, odo, System.currentTimeMillis())) {
                        checked = real.copy(speedKmh = na(0f))
                        speedSuspect = true
                    }
                }
                val real2 = checked
                val realOk = when (s.dataMode) {
                    DataMode.OBD2 -> real2 != null && real2.speedKmh.available
                    else -> real2 != null && byd.hasCoreData(real2)
                }

                val useSim = when (s.dataMode) {
                    DataMode.SIMULATION -> true
                    DataMode.BYD_ONLY -> false
                    DataMode.OBD2 -> false
                    DataMode.AUTO -> !realOk
                }
                val out: VehicleState
                val effective: EffectiveSource
                if (useSim) {
                    simTime += dt
                    val base = _simInputs.value
                    val inp = if (s.simAutoDrive) AutoDrive.inputsAt(simTime, base) else base
                    out = engine.step(inp, dt)
                    effective = EffectiveSource.SIMULATION
                } else {
                    out = real2 ?: VehicleState.unavailable()
                    effective = if (realOk) EffectiveSource.REAL else EffectiveSource.NONE
                }

                count++
                if (now - windowStart >= 1_000_000_000L) {
                    hz = count * 1_000_000_000f / (now - windowStart)
                    count = 0
                    windowStart = now
                }
                _state.value = out
                if (settings.value.tripsEnabled) recorder.feed(out, System.currentTimeMillis()) else recorder.flush(System.currentTimeMillis())
                _tripActive.value = recorder.isActive
                _status.value = ConnectionStatus(
                    effective = effective,
                    bydClassesPresent = byd.classesPresent,
                    message = describe(s.dataMode, effective, byd.classesPresent) + if (speedSuspect) " · speed signal looks stuck at 0, hidden" else "",
                    devices = byd.probes(),
                    lastUpdateMs = System.currentTimeMillis(),
                    updatesPerSecond = hz
                )
                delay((1000L / s.refreshHz.coerceIn(1, 30)).coerceAtLeast(33L))
            }
        }
    }

    fun stop() {
        recorder.flush(System.currentTimeMillis())
        job?.cancel()
        job = null
    }

    /** Build a vehicle state from OBD2 readings: speed is real when the adapter answers, everything else unavailable. */
    private fun obd2State(r: Obd2Source.Readings): VehicleState {
        val base = VehicleState.unavailable()
        val fresh = r.speedKmh != null && System.currentTimeMillis() - r.lastOkMs < 3000L
        return if (fresh) base.copy(speedKmh = real(r.speedKmh!!.toFloat())) else base
    }

    val obd2State: StateFlow<Obd2Source.State> get() = obd2.state
    val obd2Detail: StateFlow<String> get() = obd2.detail
    val obd2Readings: StateFlow<Obd2Source.Readings> get() = obd2.readings
    fun obd2Connect(mac: String) = obd2.connect(mac)
    fun obd2Stop() = obd2.stop()
    fun obd2Bonded() = obd2.bondedDevices()
    fun obd2HasPermission() = obd2.hasBtPermission()

    private fun describe(mode: DataMode, eff: EffectiveSource, present: Boolean): String = when {
        mode == DataMode.OBD2 && eff == EffectiveSource.REAL -> "REAL: speed from OBD2 adapter"
        mode == DataMode.OBD2 -> "OBD2: " + obd2.detail.value.ifBlank { "not connected" }
        eff == EffectiveSource.SIMULATION && mode == DataMode.SIMULATION -> "SIMULATION: simulation mode selected"
        eff == EffectiveSource.SIMULATION && !present -> "SIMULATION: BYD vehicle classes not found on this device"
        eff == EffectiveSource.SIMULATION -> "SIMULATION: BYD classes found but no speed reading (permission or firmware)"
        eff == EffectiveSource.REAL -> "REAL: reading BYD device APIs"
        !present -> "NO DATA: BYD classes not found and simulation is off"
        else -> "NO DATA: BYD classes found but no readings are available"
    }
}
