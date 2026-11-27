package com.songplus.cluster.vehicle

import android.content.Context
import android.net.Uri
import com.songplus.cluster.data.Gear
import com.songplus.cluster.data.VehicleState
import com.songplus.cluster.data.real

/**
 * Reads genuine vehicle data from a BYD-exposed ContentProvider that does NOT require a signature permission — the one
 * legitimate IPC route the component scan looks for. It only ever queries (read-only); it never writes or calls a
 * vehicle API. If no such provider exists on the unit, this source stays inert and the app falls back as normal.
 *
 * The mapping (which column holds speed, battery, …) is discovered from the provider's own column names, so nothing is
 * hardcoded to a specific firmware.
 */
object ProviderVehicleSource {

    data class ProviderMap(
        val authority: String,
        val speed: String? = null,
        val battery: String? = null,
        val range: String? = null,
        val gear: String? = null
    ) {
        val configured: Boolean get() = authority.isNotBlank()
        val usable: Boolean get() = configured && (speed != null || battery != null || range != null || gear != null)
    }

    /** Heuristically match a provider's column names to metrics. Pure + unit-tested. */
    fun autoMap(authority: String, columns: List<String>): ProviderMap {
        fun find(vararg keys: String): String? =
            columns.firstOrNull { col -> keys.any { col.lowercase().contains(it) } }
        return ProviderMap(
            authority = authority,
            speed = find("speed", "vehicle_speed", "spd", "velocity"),
            battery = find("soc", "battery", "elec_percent", "charge_level", "bat_pct"),
            range = find("range", "remain", "mileage", "dte", "endurance"),
            gear = find("gear", "shift", "prnd", "gearbox")
        )
    }

    data class Parsed(val speed: Float?, val battery: Float?, val range: Float?, val gear: Gear?) {
        val any: Boolean get() = speed != null || battery != null || range != null || gear != null
    }

    /** Map one row's raw string values to typed metrics. Pure + unit-tested. */
    fun mapRow(values: Map<String, String?>, map: ProviderMap): Parsed {
        fun num(col: String?): Float? = col?.let { values[it]?.trim()?.toFloatOrNull() }
        val gear = map.gear?.let { values[it]?.trim()?.uppercase() }?.let { g ->
            when {
                g.startsWith("P") || g == "0" -> Gear.P
                g.startsWith("R") || g == "1" -> Gear.R
                g.startsWith("N") || g == "2" -> Gear.N
                g.startsWith("D") || g == "3" -> Gear.D
                else -> null
            }
        }
        return Parsed(num(map.speed), num(map.battery), num(map.range), gear)
    }

    fun toVehicleState(p: Parsed): VehicleState {
        var v = VehicleState.unavailable()
        if (p.speed != null) v = v.copy(speedKmh = real(p.speed))
        if (p.battery != null) v = v.copy(socPct = real(p.battery))
        if (p.range != null) v = v.copy(rangeKm = real(p.range))
        if (p.gear != null) v = v.copy(gear = real(p.gear))
        return v
    }

    /** Read-only query of the provider's first row. Returns null on any error, empty result, or no mapped values. */
    fun read(ctx: Context, map: ProviderMap): VehicleState? {
        if (!map.usable) return null
        return try {
            ctx.contentResolver.query(Uri.parse("content://" + map.authority), null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return null
                val vals = HashMap<String, String?>()
                for (name in c.columnNames) {
                    val i = c.getColumnIndex(name)
                    vals[name] = if (i >= 0) try { c.getString(i) } catch (t: Throwable) { null } else null
                }
                val parsed = mapRow(vals, map)
                if (parsed.any) toVehicleState(parsed) else null
            }
        } catch (t: Throwable) {
            null
        }
    }
}
