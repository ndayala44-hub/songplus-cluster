package com.songplus.cluster

import com.songplus.cluster.data.Gear
import com.songplus.cluster.vehicle.ProviderVehicleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMapTest {
    @Test fun autoMapMatchesColumnsByName() {
        val m = ProviderVehicleSource.autoMap("com.byd.data.vehicle", listOf("id", "vehicle_speed", "soc_pct", "remain_range", "gear_pos"))
        assertEquals("vehicle_speed", m.speed)
        assertEquals("soc_pct", m.battery)
        assertEquals("remain_range", m.range)
        assertEquals("gear_pos", m.gear)
        assertTrue(m.usable)
    }

    @Test fun autoMapUnusableWhenNothingMatches() {
        val m = ProviderVehicleSource.autoMap("x.y.z", listOf("id", "timestamp", "foo"))
        assertFalse(m.usable)
    }

    @Test fun mapRowParsesTypesAndGear() {
        val map = ProviderVehicleSource.ProviderMap("a", speed = "spd", battery = "soc", range = "rng", gear = "g")
        val p = ProviderVehicleSource.mapRow(mapOf("spd" to "63", "soc" to "77.5", "rng" to "344", "g" to "D"), map)
        assertEquals(63f, p.speed); assertEquals(77.5f, p.battery); assertEquals(344f, p.range); assertEquals(Gear.D, p.gear)
    }

    @Test fun mapRowGearAcceptsNumericCodes() {
        val map = ProviderVehicleSource.ProviderMap("a", gear = "g")
        assertEquals(Gear.R, ProviderVehicleSource.mapRow(mapOf("g" to "1"), map).gear)
        assertEquals(Gear.P, ProviderVehicleSource.mapRow(mapOf("g" to "P"), map).gear)
        assertNull(ProviderVehicleSource.mapRow(mapOf("g" to "weird"), map).gear)
    }

    @Test fun mapRowHandlesMissingAndJunk() {
        val map = ProviderVehicleSource.ProviderMap("a", speed = "spd", battery = "soc")
        val p = ProviderVehicleSource.mapRow(mapOf("spd" to "not_a_number", "soc" to null), map)
        assertNull(p.speed); assertNull(p.battery); assertFalse(p.any)
    }

    @Test fun toVehicleStateMarksOnlyPresentFieldsReal() {
        val p = ProviderVehicleSource.Parsed(speed = 50f, battery = null, range = null, gear = null)
        val v = ProviderVehicleSource.toVehicleState(p)
        assertTrue(v.speedKmh.available); assertEquals(50f, v.speedKmh.value)
        assertFalse(v.socPct.available) // untouched metrics stay unavailable, never faked
    }
}
