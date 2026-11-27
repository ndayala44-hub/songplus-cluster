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
    private val appCtx = context.applicationContext
    private val byd = BydAutoSource(appCtx)
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
    private var lastObd2AttemptMs = 0L

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

                // OBD2 adapter: hold the serial socket only when it could be used — explicit OBD2 mode, or Automatic
                // with an adapter configured (so Automatic can fall back to OBD2 before simulating).
                val wantObd2 = s.dataMode == DataMode.OBD2 ||
                    (s.dataMode == DataMode.AUTO && s.obd2Enabled && s.obd2Mac.isNotBlank())
                if (wantObd2) {
                    if (obd2.state.value == Obd2Source.State.IDLE && s.obd2Mac.isNotBlank()) {
                        lastObd2AttemptMs = System.currentTimeMillis(); obd2.connect(s.obd2Mac)
                    }
                } else if (obd2.state.value != Obd2Source.State.IDLE) {
                    obd2.stop()
                }

                // Recover a dropped OBD2 link: retry after a backoff rather than hammering the adapter.
                if (wantObd2 && SourceSelector.shouldReconnect(obd2.state.value == Obd2Source.State.ERROR, lastObd2AttemptMs, System.currentTimeMillis())) {
                    lastObd2AttemptMs = System.currentTimeMillis()
                    if (s.obd2Mac.isNotBlank()) obd2.connect(s.obd2Mac)
                }

                // BYD API candidate (not in explicit OBD2 or Simulation mode).
                val bydRaw: VehicleState? =
                    if (s.dataMode != DataMode.OBD2 && s.dataMode != DataMode.SIMULATION && byd.classesPresent) byd.read() else null
                // A speed that sticks at zero while the odometer advances is not believable: treat it as unavailable.
                var bydChecked = bydRaw
                var speedSuspect = false
                if (bydRaw != null && bydRaw.speedKmh.available) {
                    val odo = if (bydRaw.odoKm.available) bydRaw.odoKm.value else null
                    if (!plausibility.check(bydRaw.speedKmh.value, odo, System.currentTimeMillis())) {
                        bydChecked = bydRaw.copy(speedKmh = na(0f))
                        speedSuspect = true
                    }
                }
                val bydApiOk = bydChecked != null && byd.hasCoreData(bydChecked)

                // Legitimate ContentProvider route (read-only), if one has been configured from the component scan.
                val providerMap = ProviderVehicleSource.ProviderMap(
                    s.providerAuthority,
                    s.providerSpeedCol.ifBlank { null }, s.providerBatteryCol.ifBlank { null },
                    s.providerRangeCol.ifBlank { null }, s.providerGearCol.ifBlank { null }
                )
                val providerCand: VehicleState? =
                    if (s.dataMode != DataMode.OBD2 && s.dataMode != DataMode.SIMULATION && providerMap.usable)
                        ProviderVehicleSource.read(appCtx, providerMap) else null
                val providerHasSpeed = providerCand?.speedKmh?.available == true

                // BYD-family real = genuine BYD API if available, else the clean provider route.
                val bydFamilyCand: VehicleState? = if (bydApiOk) bydChecked else providerCand
                val bydOk = bydApiOk || providerHasSpeed
                val realViaProvider = !bydApiOk && providerHasSpeed

                // OBD2 candidate (explicit OBD2 mode, or Automatic fallback).
                val obd2Cand: VehicleState? = if (wantObd2) obd2State(obd2.readings.value) else null
                val obd2Ok = obd2Cand != null && obd2Cand.speedKmh.available

                // Pick the source with the pure, unit-tested selector (Automatic: BYD -> OBD2 -> labelled simulation).
                val decision = SourceSelector.choose(s.dataMode, bydOk, obd2Ok)
                val real2: VehicleState? = when (decision.source) {
                    SourceSelector.Chosen.BYD -> bydFamilyCand
                    SourceSelector.Chosen.OBD2 -> obd2Cand
                    SourceSelector.Chosen.NONE -> bydFamilyCand ?: obd2Cand
                    SourceSelector.Chosen.SIMULATION -> null
                }
                val realOk = decision.real
                val realViaObd2 = decision.viaObd2
                val useSim = decision.useSim
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
                    message = describe(s.dataMode, effective, byd.classesPresent) +
                        (if (realViaObd2 && effective == EffectiveSource.REAL && s.dataMode == DataMode.AUTO) " · via OBD2" else "") +
                        (if (realViaProvider && effective == EffectiveSource.REAL) " · via provider" else "") +
                        (if (speedSuspect) " · speed signal looks stuck at 0, hidden" else ""),
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
        val fresh = SourceSelector.fresh(r.speedKmh != null, r.lastOkMs, System.currentTimeMillis())
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
