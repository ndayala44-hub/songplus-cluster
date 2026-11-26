package com.songplus.cluster.vehicle

import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.data.Source
import com.songplus.cluster.data.TripRecord
import com.songplus.cluster.data.VehicleState

data class TripConfig(
    val autoStopSec: Int = 180,
    val minKm: Float = 0.5f,
    val capacityKwh: Float = 71.8f,
    val recordSim: Boolean = false,
    val startKmh: Float = 3f
)

/**
 * Detects trips from the speed signal and summarises them. Pure Kotlin (no Android types) so it is unit-tested.
 *
 * - A trip starts when speed reaches [TripConfig.startKmh] and ends after [TripConfig.autoStopSec] without movement.
 * - Distance is the integral of speed; gaps longer than [MAX_GAP_MS] are not integrated (no guessing).
 * - Energy is estimated from state-of-charge change times the configured pack capacity.
 * - A trip never mixes real and simulated data: a change of source ends the trip.
 * - Trips shorter than [TripConfig.minKm] are dropped.
 */
class TripRecorder(private val config: () -> TripConfig, private val onTrip: (TripRecord) -> Unit) {
    private var active = false
    private var simulated = false
    private var startMs = 0L
    private var lastMs = 0L
    private var lastMovingMs = 0L
    private var distKm = 0.0
    private var movingSec = 0.0
    private var maxKmh = 0f
    private var socStart: Float? = null
    private var socEnd: Float? = null
    private var eco = 0.0
    private var normal = 0.0
    private var sport = 0.0
    private var nextSampleMs = 0L
    private val samples = ArrayList<Int>()

    val isActive: Boolean get() = active
    val currentDistanceKm: Float get() = if (active) distKm.toFloat() else 0f

    @Synchronized
    fun feed(s: VehicleState, nowMs: Long) {
        val cfg = config()
        val sp = s.speedKmh
        val usable = sp.available && (sp.src == Source.REAL || (sp.src == Source.SIM && cfg.recordSim))
        if (!usable) {
            if (active) finish(nowMs)
            return
        }
        val sim = sp.src == Source.SIM
        if (active && sim != simulated) finish(nowMs)
        val v = sp.value
        if (!active) {
            if (v >= cfg.startKmh) begin(sim, nowMs, s)
            return
        }
        val dt = nowMs - lastMs
        if (dt in 1..MAX_GAP_MS) {
            val sec = dt / 1000.0
            distKm += v * sec / 3600.0
            if (v > 1f) {
                movingSec += sec
                when (s.mode.value) {
                    DriveMode.ECO -> eco += sec
                    DriveMode.NORMAL -> normal += sec
                    DriveMode.SPORT -> sport += sec
                }
            }
        }
        lastMs = nowMs
        if (v > maxKmh) maxKmh = v
        if (v > 1f) lastMovingMs = nowMs
        if (s.socPct.available) { if (socStart == null) socStart = s.socPct.value; socEnd = s.socPct.value }
        if (nowMs >= nextSampleMs) { samples.add(v.toInt()); nextSampleMs = nowMs + SAMPLE_EVERY_MS }
        if (nowMs - lastMovingMs >= cfg.autoStopSec * 1000L) finish(lastMovingMs)
    }

    /** Ends any running trip (call when polling stops). */
    @Synchronized
    fun flush(nowMs: Long) { if (active) finish(nowMs) }

    private fun begin(sim: Boolean, nowMs: Long, s: VehicleState) {
        active = true; simulated = sim
        startMs = nowMs; lastMs = nowMs; lastMovingMs = nowMs
        distKm = 0.0; movingSec = 0.0; maxKmh = s.speedKmh.value
        socStart = if (s.socPct.available) s.socPct.value else null
        socEnd = socStart
        eco = 0.0; normal = 0.0; sport = 0.0
        samples.clear(); samples.add(s.speedKmh.value.toInt()); nextSampleMs = nowMs + SAMPLE_EVERY_MS
    }

    private fun finish(endMs: Long) {
        active = false
        val cfg = config()
        val km = distKm.toFloat()
        if (km < cfg.minKm) return
        val e = if (socStart != null && socEnd != null) (((socStart!! - socEnd!!).coerceAtLeast(0f)) / 100f * cfg.capacityKwh) else null
        val avg = if (movingSec > 1.0) (distKm / (movingSec / 3600.0)).toFloat() else 0f
        onTrip(
            TripRecord(
                id = startMs, startMs = startMs, endMs = maxOf(endMs, startMs), distanceKm = km, movingSec = movingSec.toInt(),
                avgKmh = avg, maxKmh = maxKmh, socStart = socStart, socEnd = socEnd, energyKwh = e,
                ecoSec = eco.toInt(), normalSec = normal.toInt(), sportSec = sport.toInt(), simulated = simulated,
                speeds = downsample(samples, TripRecord.MAX_SAMPLES)
            )
        )
    }

    companion object {
        const val MAX_GAP_MS = 10_000L
        const val SAMPLE_EVERY_MS = 5_000L

        fun downsample(src: List<Int>, max: Int): List<Int> {
            if (src.size <= max) return src.toList()
            return List(max) { i -> src[(i.toLong() * (src.size - 1) / (max - 1)).toInt()] }
        }
    }
}
