package com.songplus.cluster.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * One recorded trip. Energy is an ESTIMATE from the state-of-charge change times the configured pack capacity
 * (no kWh counter is read from the car), so short trips are coarse. [simulated] trips come from the simulator.
 */
data class TripRecord(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Float,
    val movingSec: Int,
    val avgKmh: Float,
    val maxKmh: Float,
    val socStart: Float?,
    val socEnd: Float?,
    val energyKwh: Float?,
    val ecoSec: Int,
    val normalSec: Int,
    val sportSec: Int,
    val simulated: Boolean,
    /** Speed samples (km/h) taken evenly across the trip, at most [MAX_SAMPLES]. */
    val speeds: List<Int>
) {
    val durationSec: Int get() = ((endMs - startMs) / 1000L).toInt().coerceAtLeast(0)

    val kwhPer100: Float?
        get() = if (energyKwh != null && distanceKm >= MIN_KM_FOR_RATE && energyKwh > 0f) energyKwh / distanceKm * 100f else null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("startMs", startMs); put("endMs", endMs)
        put("distanceKm", distanceKm.toDouble()); put("movingSec", movingSec)
        put("avgKmh", avgKmh.toDouble()); put("maxKmh", maxKmh.toDouble())
        socStart?.let { put("socStart", it.toDouble()) }
        socEnd?.let { put("socEnd", it.toDouble()) }
        energyKwh?.let { put("energyKwh", it.toDouble()) }
        put("ecoSec", ecoSec); put("normalSec", normalSec); put("sportSec", sportSec)
        put("simulated", simulated)
        val a = JSONArray()
        speeds.forEach { a.put(it) }
        put("speeds", a)
    }

    companion object {
        const val MAX_SAMPLES = 120
        const val MIN_KM_FOR_RATE = 0.5f

        private fun JSONObject.optF(k: String): Float? = if (has(k)) optDouble(k).toFloat() else null

        fun fromJson(o: JSONObject): TripRecord {
            val sp = ArrayList<Int>()
            o.optJSONArray("speeds")?.let { a -> for (i in 0 until a.length()) sp.add((a.opt(i) as? Number)?.toInt() ?: 0) }
            return TripRecord(
                id = (o.opt("id") as? Number)?.toLong() ?: 0L,
                startMs = (o.opt("startMs") as? Number)?.toLong() ?: 0L,
                endMs = (o.opt("endMs") as? Number)?.toLong() ?: 0L,
                distanceKm = o.optDouble("distanceKm", 0.0).toFloat().coerceAtLeast(0f),
                movingSec = o.optInt("movingSec", 0).coerceAtLeast(0),
                avgKmh = o.optDouble("avgKmh", 0.0).toFloat().coerceAtLeast(0f),
                maxKmh = o.optDouble("maxKmh", 0.0).toFloat().coerceAtLeast(0f),
                socStart = o.optF("socStart"), socEnd = o.optF("socEnd"), energyKwh = o.optF("energyKwh"),
                ecoSec = o.optInt("ecoSec", 0), normalSec = o.optInt("normalSec", 0), sportSec = o.optInt("sportSec", 0),
                simulated = o.optBoolean("simulated", false),
                speeds = sp
            )
        }

        fun csvHeader() = "id,start_iso,end_iso,distance_km,duration_s,moving_s,avg_kmh,max_kmh,soc_start,soc_end,energy_kwh_est,kwh_per_100km_est,eco_s,normal_s,sport_s,simulated"
    }

    fun toCsvRow(): String {
        fun f(v: Float?, d: Int = 2) = if (v == null) "" else String.format(Locale.US, "%.${d}f", v)
        fun iso(ms: Long) = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(java.util.Date(ms))
        return listOf(
            id.toString(), iso(startMs), iso(endMs), f(distanceKm), durationSec.toString(), movingSec.toString(), f(avgKmh, 1), f(maxKmh, 1),
            f(socStart, 1), f(socEnd, 1), f(energyKwh), f(kwhPer100, 1), ecoSec.toString(), normalSec.toString(), sportSec.toString(), simulated.toString()
        ).joinToString(",")
    }
}

object TripExport {
    fun csv(trips: List<TripRecord>): String =
        (listOf(TripRecord.csvHeader()) + trips.map { it.toCsvRow() }).joinToString("\n") + "\n"

    fun json(trips: List<TripRecord>): String = JSONObject().apply {
        put("format", "songplus-cluster-trips")
        put("version", 1)
        put("note", "Energy is estimated from state-of-charge change times the configured pack capacity.")
        val a = JSONArray(); trips.forEach { a.put(it.toJson()) }
        put("trips", a)
    }.toString()
}

/** Keeps trips in SharedPreferences as JSON, newest first, capped so storage cannot grow without bound. */
class TripStore(context: Context) {
    private val prefs = context.getSharedPreferences("cluster_trips", Context.MODE_PRIVATE)
    private val _trips = MutableStateFlow(load())
    val trips: StateFlow<List<TripRecord>> = _trips.asStateFlow()

    fun add(t: TripRecord) = persist((listOf(t) + _trips.value).take(MAX_TRIPS))
    fun delete(id: Long) = persist(_trips.value.filter { it.id != id })
    fun clear() = persist(emptyList())
    fun deleteSimulated() = persist(_trips.value.filter { !it.simulated })

    private fun persist(list: List<TripRecord>) {
        _trips.value = list
        val a = JSONArray(); list.forEach { a.put(it.toJson()) }
        prefs.edit().putString(KEY, a.toString()).apply()
    }

    private fun load(): List<TripRecord> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i -> try { a.optJSONObject(i)?.let { TripRecord.fromJson(it) } } catch (e: Exception) { null } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        const val MAX_TRIPS = 500
        private const val KEY = "trips"
    }
}
