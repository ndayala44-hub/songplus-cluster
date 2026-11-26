package com.songplus.cluster.vehicle

import com.songplus.cluster.data.TripRecord

data class Projection(
    /** Projected remaining range from the recent consumption rate. */
    val rangeKm: Float,
    val rateKwhPer100: Float,
    /** Distance of driving history the rate is based on. */
    val basisKm: Float,
    val bmsRangeKm: Float?,
) {
    /** Projection minus the car's own estimate (positive = projection is more optimistic). */
    val deltaKm: Float? get() = bmsRangeKm?.let { rangeKm - it }
}

/**
 * Remaining range = energy left in the battery / recent consumption, where recent consumption is measured over the
 * last [windowKm] km of recorded trips (newest first, across trips). Returns null when there is not enough history,
 * so the UI shows "--" rather than a guess.
 */
object RangeProjection {
    const val MIN_BASIS_KM = 3f

    fun project(
        trips: List<TripRecord>,
        windowKm: Float,
        socPct: Float,
        capacityKwh: Float,
        bmsRangeKm: Float?,
        includeSimulated: Boolean = false
    ): Projection? {
        if (windowKm <= 0f || capacityKwh <= 0f || socPct <= 0f || socPct > 100f) return null
        var km = 0f
        var kwh = 0f
        for (t in trips.sortedByDescending { it.startMs }) {
            if (t.simulated && !includeSimulated) continue
            val e = t.energyKwh ?: continue
            if (t.distanceKm < TripRecord.MIN_KM_FOR_RATE) continue
            val remaining = windowKm - km
            if (remaining <= 0f) break
            val take = minOf(t.distanceKm, remaining)
            km += take
            kwh += e * (take / t.distanceKm)
        }
        if (km < MIN_BASIS_KM || kwh <= 0f) return null
        val rate = kwh / km * 100f
        if (rate < 5f || rate > 60f) return null
        val usable = socPct / 100f * capacityKwh
        return Projection(usable / (rate / 100f), rate, km, bmsRangeKm)
    }
}
