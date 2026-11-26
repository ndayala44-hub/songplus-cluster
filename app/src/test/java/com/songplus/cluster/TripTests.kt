package com.songplus.cluster

import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.data.Source
import com.songplus.cluster.data.SpeedDisplayMode
import com.songplus.cluster.data.TripExport
import com.songplus.cluster.data.TripRecord
import com.songplus.cluster.data.TripSort
import com.songplus.cluster.data.Units
import com.songplus.cluster.data.VehicleState
import com.songplus.cluster.data.filterSortTrips
import com.songplus.cluster.data.real
import com.songplus.cluster.data.sim
import com.songplus.cluster.vehicle.RangeProjection
import com.songplus.cluster.vehicle.SpeedPlausibility
import com.songplus.cluster.vehicle.TripConfig
import com.songplus.cluster.vehicle.TripRecorder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTests {
    private fun st(speed: Float, soc: Float = 80f, simulated: Boolean = false, mode: DriveMode = DriveMode.NORMAL, odo: Float? = null): VehicleState {
        val u = VehicleState.unavailable()
        return u.copy(
            speedKmh = if (simulated) sim(speed) else real(speed), socPct = real(soc), mode = real(mode),
            odoKm = if (odo != null) real(odo) else u.odoKm
        )
    }

    private fun trip(id: Long, km: Float, kwh: Float?, sim: Boolean = false) = TripRecord(
        id, id, id + 600_000, km, 600, 50f, 90f, 80f, 70f, kwh, 0, 600, 0, sim, listOf(10, 50, 60)
    )

    /** Drives at [kmh] for [seconds] with 1 s steps, soc falling linearly. */
    private fun drive(r: TripRecorder, t0: Long, kmh: Float, seconds: Int, socStart: Float, socEnd: Float, sim: Boolean = false): Long {
        var t = t0
        for (i in 0..seconds) {
            r.feed(st(kmh, socStart + (socEnd - socStart) * i / seconds, sim), t)
            t += 1000
        }
        return t
    }

    @Test fun tripIsRecordedWithDistanceAndEnergy() {
        val out = ArrayList<TripRecord>()
        val r = TripRecorder({ TripConfig(autoStopSec = 60) }, { out.add(it) })
        var t = drive(r, 1_000_000L, 72f, 600, 80f, 78f)   // 72 km/h for 10 min = 12 km
        assertTrue(r.isActive)
        for (i in 0..70) { r.feed(st(0f, 78f), t); t += 1000 }   // stand still > 60 s
        assertFalse(r.isActive)
        assertEquals(1, out.size)
        val tr = out[0]
        assertEquals(12f, tr.distanceKm, 0.1f)
        assertEquals(2f / 100f * 71.8f, tr.energyKwh!!, 0.05f)
        assertEquals(72f, tr.avgKmh, 1f)
        assertEquals(12f * 0 + (tr.energyKwh!! / tr.distanceKm * 100f), tr.kwhPer100!!, 0.01f)
        assertFalse(tr.simulated)
    }

    @Test fun shortTripIsDropped() {
        val out = ArrayList<TripRecord>()
        val r = TripRecorder({ TripConfig(autoStopSec = 30, minKm = 0.5f) }, { out.add(it) })
        var t = drive(r, 0L, 30f, 10, 80f, 80f)
        for (i in 0..40) { r.feed(st(0f), t); t += 1000 }
        assertEquals(0, out.size)
    }

    @Test fun simulatedTripsOnlyWhenEnabled() {
        val off = ArrayList<TripRecord>()
        val r1 = TripRecorder({ TripConfig() }, { off.add(it) })
        drive(r1, 0L, 60f, 120, 80f, 79f, sim = true); r1.flush(200_000L)
        assertEquals(0, off.size)
        val on = ArrayList<TripRecord>()
        val r2 = TripRecorder({ TripConfig(recordSim = true) }, { on.add(it) })
        drive(r2, 0L, 60f, 120, 80f, 79f, sim = true); r2.flush(200_000L)
        assertEquals(1, on.size)
        assertTrue(on[0].simulated)
    }

    @Test fun changeOfSourceEndsTheTrip() {
        val out = ArrayList<TripRecord>()
        val r = TripRecorder({ TripConfig(recordSim = true) }, { out.add(it) })
        val t = drive(r, 0L, 60f, 120, 80f, 79f, sim = true)
        r.feed(st(60f, 79f, simulated = false), t + 1000)   // real data appears: sim trip must close
        assertEquals(1, out.size)
        assertTrue(out[0].simulated)
    }

    @Test fun dataGapIsNotIntegrated() {
        val out = ArrayList<TripRecord>()
        val r = TripRecorder({ TripConfig(minKm = 0f) }, { out.add(it) })
        r.feed(st(100f), 0L); r.feed(st(100f), 1000L)
        r.feed(st(100f), 3_600_000L)       // one hour later: a gap, not 100 km
        r.flush(3_601_000L)
        assertTrue(out[0].distanceKm < 1f)
    }

    @Test fun unavailableSpeedEndsTripWithoutCrashing() {
        val r = TripRecorder({ TripConfig(minKm = 0f) }, { })
        r.feed(st(50f), 0L)
        r.feed(VehicleState.unavailable(), 1000L)
        assertFalse(r.isActive)
    }

    @Test fun projectionUsesRecentConsumption() {
        val trips = listOf(trip(3, 60f, 9f), trip(2, 60f, 12f), trip(1, 60f, 30f))   // newest = 3
        val p = RangeProjection.project(trips, 100f, 50f, 70f, 250f)!!
        // newest 100 km: 60 km @ 9 kWh + 40 km @ 8 kWh = 17 kWh / 100 km
        assertEquals(17f, p.rateKwhPer100, 0.01f)
        assertEquals(100f, p.basisKm, 0.01f)
        assertEquals(35f / 0.17f, p.rangeKm, 0.5f)
        assertEquals(p.rangeKm - 250f, p.deltaKm!!, 0.01f)
    }

    @Test fun projectionIsNullWithoutHistory() {
        assertEquals(null, RangeProjection.project(emptyList(), 100f, 50f, 70f, null))
        assertEquals(null, RangeProjection.project(listOf(trip(1, 2f, 0.3f)), 100f, 50f, 70f, null))
        assertEquals(null, RangeProjection.project(listOf(trip(1, 50f, 8f, sim = true)), 100f, 50f, 70f, null))
        assertTrue(RangeProjection.project(listOf(trip(1, 50f, 8f, sim = true)), 100f, 50f, 70f, null, includeSimulated = true) != null)
    }

    @Test fun projectionRejectsImplausibleRates() {
        assertEquals(null, RangeProjection.project(listOf(trip(1, 50f, 0.5f)), 100f, 50f, 70f, null))   // 1 kWh/100 km
    }

    @Test fun stuckZeroSpeedWhileOdometerAdvancesIsSuspect() {
        val g = SpeedPlausibility(stallSec = 60)
        assertTrue(g.check(0f, 1000f, 0L))
        assertTrue(g.check(0f, 1000f, 30_000L))
        assertTrue(g.check(0f, 1001f, 50_000L))        // odo moved but not yet 60 s
        assertFalse(g.check(0f, 1002f, 65_000L))       // 60 s of zero and odo +2 km
        assertTrue(g.check(30f, 1002f, 66_000L))       // recovers on non-zero
    }

    @Test fun genuineStandstillIsNeverSuspect() {
        val g = SpeedPlausibility(stallSec = 60)
        for (i in 0..200) assertTrue(g.check(0f, 5000f, i * 1000L))
    }

    @Test fun speedDisplayCalibration() {
        assertEquals(100f, Units.displaySpeedKmh(100f, SpeedDisplayMode.REAL, 4.5f, 5f), 0.01f)
        assertEquals(104.5f, Units.displaySpeedKmh(100f, SpeedDisplayMode.PERCENT, 4.5f, 5f), 0.01f)
        assertEquals(105f, Units.displaySpeedKmh(100f, SpeedDisplayMode.PLUS_KMH, 4.5f, 5f), 0.01f)
        assertEquals(0f, Units.displaySpeedKmh(0f, SpeedDisplayMode.PLUS_KMH, 4.5f, 5f), 0.01f)
    }

    @Test fun tripJsonAndCsvRoundTrip() {
        val t = trip(1_700_000_000_000L, 12.5f, 2.1f)
        val b = TripRecord.fromJson(JSONObject(t.toJson().toString()))
        assertEquals(t.distanceKm, b.distanceKm, 0.001f)
        assertEquals(t.energyKwh!!, b.energyKwh!!, 0.001f)
        assertEquals(t.speeds, b.speeds)
        val noEnergy = TripRecord.fromJson(JSONObject(trip(5, 3f, null).toJson().toString()))
        assertEquals(null, noEnergy.energyKwh)
        val csv = TripExport.csv(listOf(t, trip(2, 3f, null)))
        assertEquals(3, csv.trim().lines().size)
        assertTrue(csv.lines()[0].startsWith("id,start_iso"))
        assertEquals(csv.lines()[0].split(",").size, csv.lines()[1].split(",").size)
        assertTrue(TripExport.json(listOf(t)).contains("songplus-cluster-trips"))
    }

    @Test fun sortingAndFiltering() {
        val a = trip(1, 10f, 2f); val b = trip(2, 30f, 4f); val c = trip(3, 5f, null); val d = trip(4, 20f, 3f, sim = true)
        val all = listOf(a, b, c, d)
        assertEquals(listOf(4L, 3L, 2L, 1L), filterSortTrips(all, 0f, true, TripSort.DATE, false).map { it.id })
        assertEquals(listOf(2L, 4L, 1L, 3L), filterSortTrips(all, 0f, true, TripSort.DISTANCE, false).map { it.id })
        assertEquals(listOf(1L, 2L), filterSortTrips(all, 8f, false, TripSort.DATE, true).map { it.id })
        assertEquals(3L, filterSortTrips(all, 0f, true, TripSort.EFFICIENCY, true).last().id)   // no value goes last
    }
}
