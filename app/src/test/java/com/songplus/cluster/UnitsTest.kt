package com.songplus.cluster

import com.songplus.cluster.data.DistUnit
import com.songplus.cluster.data.SpeedUnit
import com.songplus.cluster.data.TempUnit
import com.songplus.cluster.data.Units
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test fun kmhToMph() = assertEquals(62, Units.speed(100f, SpeedUnit.MPH))
    @Test fun kmhIdentity() = assertEquals(100, Units.speed(100f, SpeedUnit.KMH))
    @Test fun neverNegative() = assertEquals(0, Units.speed(-3f, SpeedUnit.KMH))
    @Test fun distance() = assertEquals(62.137f, Units.distance(100f, DistUnit.MI), 0.01f)
    @Test fun temperature() {
        assertEquals(68, Units.temp(20f, TempUnit.F))
        assertEquals(20, Units.temp(20f, TempUnit.C))
    }
    @Test fun labels() {
        assertEquals("mph", Units.speedLabel(SpeedUnit.MPH))
        assertEquals("kWh/100 mi", Units.consumptionLabel(DistUnit.MI))
    }
}
