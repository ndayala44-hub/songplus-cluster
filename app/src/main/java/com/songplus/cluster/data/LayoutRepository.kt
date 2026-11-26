package com.songplus.cluster.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Stores user layouts (JSON in SharedPreferences) next to the read-only built-in presets. */
class LayoutRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cluster_layouts", Context.MODE_PRIVATE)
    private val _user = MutableStateFlow(load())

    /** User-created layouts only. */
    val userLayouts: StateFlow<List<ClusterConfig>> = _user.asStateFlow()

    fun all(): List<ClusterConfig> = BuiltinLayouts.all() + _user.value

    fun find(id: String): ClusterConfig? = all().firstOrNull { it.id == id }

    /** Returns the layout with [id], or the Classic default when it no longer exists. */
    fun resolve(id: String): ClusterConfig = find(id) ?: BuiltinLayouts.classic()

    /** Saves [config]. Built-in or unknown ids are stored as a new user layout; the stored layout is returned. */
    fun save(config: ClusterConfig): ClusterConfig {
        val existing = _user.value.any { it.id == config.id }
        val stored = if (existing) config.copy(builtin = false)
        else config.copy(id = newId(), builtin = false)
        val list = if (existing) _user.value.map { if (it.id == stored.id) stored else it } else _user.value + stored
        persist(list)
        return stored
    }

    fun saveAs(config: ClusterConfig, name: String): ClusterConfig {
        val copy = config.copy(id = newId(), name = name.trim().ifEmpty { "My layout" }, builtin = false)
        persist(_user.value + copy)
        return copy
    }

    fun duplicate(id: String): ClusterConfig? {
        val src = find(id) ?: return null
        return saveAs(src, src.name + " copy")
    }

    fun rename(id: String, name: String) {
        persist(_user.value.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })
    }

    fun delete(id: String) {
        persist(_user.value.filter { it.id != id })
    }

    fun deleteAllUser() = persist(emptyList())

    fun exportJson(): JSONArray = JSONArray().also { arr -> _user.value.forEach { arr.put(it.toJson()) } }

    /** Imports layouts from [arr]; ids are regenerated to avoid collisions. Returns how many were added. */
    fun importJson(arr: JSONArray): Int {
        val added = ArrayList<ClusterConfig>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            try {
                added.add(ClusterConfig.fromJson(o).copy(id = newId(), builtin = false))
            } catch (e: Exception) {
                // skip malformed entry
            }
        }
        if (added.isNotEmpty()) persist(_user.value + added)
        return added.size
    }

    private fun newId() = "user_" + UUID.randomUUID().toString().take(8)

    private fun persist(list: List<ClusterConfig>) {
        _user.value = list
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<ClusterConfig> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    arr.optJSONObject(i)?.let { ClusterConfig.fromJson(it).copy(builtin = false) }
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY = "layouts"

        /** Bundles settings and layouts for export. */
        fun exportBundle(settings: AppSettings, layouts: JSONArray): String = JSONObject().apply {
            put("format", "songplus-cluster-config")
            put("version", 1)
            put("settings", settings.toJson())
            put("layouts", layouts)
        }.toString(2)
    }
}
