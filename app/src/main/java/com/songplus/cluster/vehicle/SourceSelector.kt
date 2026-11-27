package com.songplus.cluster.vehicle

import com.songplus.cluster.data.DataMode

/**
 * Pure, testable rules for which data source the cluster shows. Keeping this free of Android types lets the source
 * selection, fallback order, staleness and reconnect behaviour be unit-tested on the JVM without a device.
 */
object SourceSelector {

    enum class Chosen { BYD, OBD2, SIMULATION, NONE }

    data class Decision(
        val source: Chosen,
        val useSim: Boolean,
        val viaObd2: Boolean,
        /** True when the chosen source is genuine (real) vehicle data. */
        val real: Boolean
    )

    /**
     * Automatic prefers genuine BYD data, then real OBD2, then clearly-labelled simulation.
     * BYD only and OBD2 never simulate: when their source is unavailable the result is NONE (shows "--").
     */
    fun choose(mode: DataMode, bydOk: Boolean, obd2Ok: Boolean): Decision = when (mode) {
        DataMode.SIMULATION -> Decision(Chosen.SIMULATION, useSim = true, viaObd2 = false, real = false)
        DataMode.BYD_ONLY ->
            if (bydOk) Decision(Chosen.BYD, false, false, true) else Decision(Chosen.NONE, false, false, false)
        DataMode.OBD2 ->
            if (obd2Ok) Decision(Chosen.OBD2, false, true, true) else Decision(Chosen.NONE, false, true, false)
        DataMode.AUTO -> when {
            bydOk -> Decision(Chosen.BYD, false, false, true)
            obd2Ok -> Decision(Chosen.OBD2, false, true, true)
            else -> Decision(Chosen.SIMULATION, true, false, false)
        }
    }

    /** A real reading is usable only if present and recent — guards against showing stale telemetry as live. */
    fun fresh(present: Boolean, lastOkMs: Long, now: Long, windowMs: Long = 3000L): Boolean =
        present && lastOkMs > 0L && (now - lastOkMs) in 0..windowMs

    /** Whether to retry an OBD2 connection after an error, with a simple backoff so we don't hammer the adapter. */
    fun shouldReconnect(isError: Boolean, lastAttemptMs: Long, now: Long, backoffMs: Long = 4000L): Boolean =
        isError && (now - lastAttemptMs) >= backoffMs
}
