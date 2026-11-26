package com.songplus.cluster.vehicle

/**
 * Guards against a stuck speed signal. Some head units have been seen to keep returning 0.0 while the car moves
 * (reported for other makes); if the speed has read exactly zero for [stallSec] seconds while the odometer has
 * advanced by at least [minOdoKm], the zero is not believable and the speed should be shown as unavailable.
 * Clears as soon as a non-zero speed is read. Pure Kotlin.
 */
class SpeedPlausibility(private val stallSec: Int = 60, private val minOdoKm: Float = 1f) {
    private var zeroSinceMs = -1L
    private var odoAtZero: Float? = null
    var suspect: Boolean = false
        private set

    /** Returns true when the speed reading is believable. */
    fun check(speedKmh: Float, odoKm: Float?, nowMs: Long): Boolean {
        if (speedKmh >= 0.1f) {
            zeroSinceMs = -1L; odoAtZero = null; suspect = false
            return true
        }
        if (zeroSinceMs < 0L) { zeroSinceMs = nowMs; odoAtZero = odoKm }
        val base = odoAtZero
        if (odoKm != null) {
            if (base == null) odoAtZero = odoKm
            else if (nowMs - zeroSinceMs >= stallSec * 1000L && odoKm - base >= minOdoKm) suspect = true
        }
        return !suspect
    }

    fun reset() { zeroSinceMs = -1L; odoAtZero = null; suspect = false }
}
