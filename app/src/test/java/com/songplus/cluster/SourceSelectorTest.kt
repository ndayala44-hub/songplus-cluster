package com.songplus.cluster

import com.songplus.cluster.data.DataMode
import com.songplus.cluster.vehicle.SourceSelector
import com.songplus.cluster.vehicle.SourceSelector.Chosen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSelectorTest {

    // ---- Automatic: prefers BYD, then OBD2, then labelled simulation ----
    @Test fun autoPrefersBydWhenAvailable() {
        val d = SourceSelector.choose(DataMode.AUTO, bydOk = true, obd2Ok = true)
        assertEquals(Chosen.BYD, d.source); assertTrue(d.real); assertFalse(d.useSim); assertFalse(d.viaObd2)
    }

    @Test fun autoFallsBackToObd2WhenNoByd() {
        val d = SourceSelector.choose(DataMode.AUTO, bydOk = false, obd2Ok = true)
        assertEquals(Chosen.OBD2, d.source); assertTrue(d.real); assertFalse(d.useSim); assertTrue(d.viaObd2)
    }

    @Test fun autoSimulatesOnlyWhenNoRealSource() {
        val d = SourceSelector.choose(DataMode.AUTO, bydOk = false, obd2Ok = false)
        assertEquals(Chosen.SIMULATION, d.source); assertTrue(d.useSim); assertFalse(d.real)
    }

    // ---- BYD only: never simulates ----
    @Test fun bydOnlyNeverSimulates() {
        val ok = SourceSelector.choose(DataMode.BYD_ONLY, bydOk = true, obd2Ok = false)
        assertEquals(Chosen.BYD, ok.source); assertFalse(ok.useSim); assertTrue(ok.real)
        val none = SourceSelector.choose(DataMode.BYD_ONLY, bydOk = false, obd2Ok = true)
        assertEquals(Chosen.NONE, none.source); assertFalse(none.useSim); assertFalse(none.real)
    }

    // ---- OBD2 mode: never simulates, never silently uses BYD ----
    @Test fun obd2ModeNeverSimulatesAndNeverUsesByd() {
        val ok = SourceSelector.choose(DataMode.OBD2, bydOk = true, obd2Ok = true)
        assertEquals(Chosen.OBD2, ok.source); assertFalse(ok.useSim); assertTrue(ok.real)
        val none = SourceSelector.choose(DataMode.OBD2, bydOk = true, obd2Ok = false)
        assertEquals(Chosen.NONE, none.source); assertFalse(none.useSim); assertFalse(none.real)
    }

    // ---- Simulation mode: always simulation, never real ----
    @Test fun simulationModeAlwaysSim() {
        val d = SourceSelector.choose(DataMode.SIMULATION, bydOk = true, obd2Ok = true)
        assertEquals(Chosen.SIMULATION, d.source); assertTrue(d.useSim); assertFalse(d.real)
    }

    // ---- Data integrity: a non-sim decision is only ever REAL, never SIMULATION mislabelled ----
    @Test fun realDecisionsAreNeverSimAndSimIsNeverReal() {
        for (mode in DataMode.values()) for (b in listOf(true, false)) for (o in listOf(true, false)) {
            val d = SourceSelector.choose(mode, b, o)
            if (d.real) { assertFalse("real must not be labelled sim ($mode,$b,$o)", d.useSim); assertFalse(d.source == Chosen.SIMULATION) }
            if (d.source == Chosen.SIMULATION) assertFalse("sim is never real", d.real)
            if (d.source == Chosen.NONE) assertFalse(d.real)
        }
    }

    // ---- Freshness: stale or missing readings are not treated as live ----
    @Test fun freshnessWindow() {
        val now = 100_000L
        assertTrue(SourceSelector.fresh(present = true, lastOkMs = now - 500, now = now))
        assertFalse("stale beyond window", SourceSelector.fresh(true, now - 5000, now))
        assertFalse("absent value", SourceSelector.fresh(false, now - 100, now))
        assertFalse("never updated", SourceSelector.fresh(true, 0L, now))
        assertFalse("future timestamp ignored", SourceSelector.fresh(true, now + 1000, now))
    }

    // ---- Reconnect backoff: only retry on error, and only after the backoff elapses ----
    @Test fun reconnectBackoff() {
        val now = 50_000L
        assertFalse("no retry when healthy", SourceSelector.shouldReconnect(isError = false, lastAttemptMs = 0, now = now))
        assertTrue("retry on error after backoff", SourceSelector.shouldReconnect(true, now - 5000, now))
        assertFalse("hold within backoff", SourceSelector.shouldReconnect(true, now - 1000, now))
    }
}
