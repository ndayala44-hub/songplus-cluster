package com.songplus.cluster.data

enum class TripSort { DATE, DISTANCE, DURATION, EFFICIENCY, AVG_SPEED, ENERGY }

/** Filters then sorts trips. Trips without a value for the sort key go last. Pure, so it is unit-tested. */
fun filterSortTrips(
    trips: List<TripRecord>, minKm: Float, showSimulated: Boolean, sort: TripSort, ascending: Boolean
): List<TripRecord> {
    val f = trips.filter { it.distanceKm >= minKm && (showSimulated || !it.simulated) }
    val key: (TripRecord) -> Double? = when (sort) {
        TripSort.DATE -> ({ t -> t.startMs.toDouble() })
        TripSort.DISTANCE -> ({ t -> t.distanceKm.toDouble() })
        TripSort.DURATION -> ({ t -> t.durationSec.toDouble() })
        TripSort.EFFICIENCY -> ({ t -> t.kwhPer100?.toDouble() })
        TripSort.AVG_SPEED -> ({ t -> t.avgKmh.toDouble() })
        TripSort.ENERGY -> ({ t -> t.energyKwh?.toDouble() })
    }
    val (withKey, without) = f.partition { key(it) != null }
    val sorted = withKey.sortedBy { key(it)!! }
    return (if (ascending) sorted else sorted.reversed()) + without
}

