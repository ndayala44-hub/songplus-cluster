package com.songplus.cluster

import com.songplus.cluster.data.Gear
import com.songplus.cluster.data.Source
import com.songplus.cluster.data.VehicleState
import com.songplus.cluster.vehicle.AutoDrive
import com.songplus.cluster.vehicle.SignalInput
import com.songplus.cluster.vehicle.SimEngine
import com.songplus.cluster.vehicle.SimInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimEngineTest {
    private fun run(e: SimEngine, inp: SimInputs, seconds: Float): VehicleState {
        var s = e.step(inp, 0.1f)
        var t = 0f
        while (t < seconds) { s = e.step(inp, 0.1f); t += 0.1f }
        return s
    }

    @Test fun reachesTargetSpeed() {
        val s = run(SimEngine(), SimInputs(targetSpeedKmh = 80f), 40f)
        assertEquals(80f, s.speedKmh.value, 0.5f)
    }

    @Test fun everySimulatedValueIsLabelledSim() {
        val s = SimEngine().step(SimInputs(), 0.1f)
        assertEquals(Source.SIM, s.speedKmh.src)
        assertEquals(Source.SIM, s.socPct.src)
        assertEquals(Source.SIM, s.tyreRL.src)
        assertTrue(s.anySim)
        assertFalse(s.anyReal)
    }

    @Test fun unavailableStateHasNoRealOrSimValues() {
        val s = VehicleState.unavailable()
        assertFalse(s.speedKmh.available)
        assertFalse(s.anySim)
        assertFalse(s.anyReal)
    }

    @Test fun socDropsWhileDriving() {
        val e = SimEngine(startSoc = 80f)
        run(e, SimInputs(targetSpeedKmh = 100f), 60f)
        assertTrue(e.soc < 80f)
    }

    @Test fun chargingOnlyWhenParked() {
        val e = SimEngine(startSoc = 50f)
        val parked = e.step(SimInputs(gear = Gear.P, charging = true), 1f)
        assertTrue(parked.charging.value)
        val driving = e.step(SimInputs(gear = Gear.D, charging = true, targetSpeedKmh = 40f), 1f)
        assertFalse(driving.charging.value)
    }

    @Test fun hazardLightsBothSides() {
        val s = SimEngine().step(SimInputs(signal = SignalInput.HAZARD), 0.1f)
        assertTrue(s.leftSignal.value && s.rightSignal.value && s.hazard.value)
    }

    @Test fun autoDriveMovesTheCar() {
        val e = SimEngine()
        var t = 0f
        var max = 0f
        while (t < 120f) {
            max = maxOf(max, e.step(AutoDrive.inputsAt(t, SimInputs()), 0.1f).speedKmh.value)
            t += 0.1f
        }
        assertTrue(max > 60f)
        assertTrue(e.trip > 0.5f)
    }

    @Test fun reverseIsSpeedLimited() {
        val s = run(SimEngine(), SimInputs(gear = Gear.R, targetSpeedKmh = 100f), 30f)
        assertTrue(s.speedKmh.value <= 12.5f)
    }
}
