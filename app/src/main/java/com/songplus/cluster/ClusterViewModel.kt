package com.songplus.cluster

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.songplus.cluster.data.AppSettings
import com.songplus.cluster.data.BuiltinLayouts
import com.songplus.cluster.data.ClusterConfig
import com.songplus.cluster.data.ConnectionStatus
import com.songplus.cluster.data.LayoutRepository
import com.songplus.cluster.data.TripExport
import com.songplus.cluster.data.TripRecord
import com.songplus.cluster.vehicle.Projection
import com.songplus.cluster.vehicle.RangeProjection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import com.songplus.cluster.data.VehicleState
import com.songplus.cluster.vehicle.SimInputs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Result of an import attempt, shown to the user. */
sealed class ImportResult {
    data class Ok(val layouts: Int, val settings: Boolean) : ImportResult()
    data class Failed(val reason: String) : ImportResult()
}

class ClusterViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as ClusterApp).container

    val settings: StateFlow<AppSettings> = c.settings.flow
    val userLayouts: StateFlow<List<ClusterConfig>> = c.layouts.userLayouts
    val vehicle: StateFlow<VehicleState> = c.vehicle.state
    val status: StateFlow<ConnectionStatus> = c.vehicle.status
    val simInputs: StateFlow<SimInputs> = c.vehicle.simInputs

    val media = c.media.info
    val mediaAccess = c.media.access
    fun refreshMedia() = c.media.refresh()
    fun hasMediaAccess() = c.media.hasAccess()

    // OBD2 (ELM327 over Bluetooth) — real speed with no vehicle permission
    val obd2State = c.vehicle.obd2State
    val obd2Detail = c.vehicle.obd2Detail
    val obd2Readings = c.vehicle.obd2Readings
    fun obd2Connect(mac: String) = c.vehicle.obd2Connect(mac)
    fun obd2Stop() = c.vehicle.obd2Stop()
    fun obd2Bonded() = c.vehicle.obd2Bonded()
    fun obd2HasPermission() = c.vehicle.obd2HasPermission()

    /** Read-only probe of the declared BYDAUTO permissions (protection level + grant state). Never changes a grant. */
    fun probeBydPermissions() = com.songplus.cluster.vehicle.PermissionProbe.run(getApplication())

    val trips: StateFlow<List<TripRecord>> = c.trips.trips
    val tripActive: StateFlow<Boolean> = c.vehicle.tripActive

    /** Range projection from recent consumption, recomputed when trips, battery state or settings change. */
    val projection: StateFlow<Projection?> = combine(c.trips.trips, c.vehicle.state, c.settings.flow) { t, v, s ->
        if (!v.socPct.available) null
        else RangeProjection.project(
            t, s.rangeWindowKm.toFloat(), v.socPct.value, s.capacityKwh,
            if (v.rangeKm.available) v.rangeKm.value else null, includeSimulated = s.recordSimTrips
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Debug builds only: adds a few trips, all flagged simulated, so screenshots of the Trips screen are not empty. */
    fun seedDemoTrips() {
        if (c.trips.trips.value.isNotEmpty()) return
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val specs = listOf(
            Triple(24.8f, 4.1f, 52f), Triple(8.2f, 1.5f, 31f), Triple(61.3f, 9.8f, 78f),
            Triple(3.9f, 0.8f, 24f), Triple(112.4f, 17.9f, 88f), Triple(15.6f, 2.6f, 45f)
        )
        specs.forEachIndexed { i, (km, kwh, avg) ->
            val start = now - (i + 1) * day - 3_600_000L
            val secs = (km / avg * 3600f).toInt()
            val speeds = List(60) { k -> (avg * (0.4f + 0.9f * kotlin.math.abs(kotlin.math.sin(k / 7.0 + i))).toFloat()).toInt() }
            c.trips.add(
                TripRecord(
                    id = start, startMs = start, endMs = start + secs * 1000L, distanceKm = km, movingSec = (secs * 0.93f).toInt(),
                    avgKmh = avg * 1.05f, maxKmh = avg * 1.7f, socStart = 90f - i * 4f, socEnd = 90f - i * 4f - kwh / c.settings.value.capacityKwh * 100f,
                    energyKwh = kwh, ecoSec = secs / 3, normalSec = secs / 2, sportSec = secs / 6, simulated = true, speeds = speeds
                )
            )
        }
    }

    fun deleteTrip(id: Long) = c.trips.delete(id)
    fun clearTrips() = c.trips.clear()
    fun deleteSimulatedTrips() = c.trips.deleteSimulated()
    fun tripsCsv(list: List<TripRecord>) = TripExport.csv(list)
    fun tripsJson(list: List<TripRecord>) = TripExport.json(list)

    /** The layout being edited in the designer. Changes stay in the draft until saved. */
    private val _draft = MutableStateFlow(c.layouts.resolve(c.settings.value.activeLayoutId))
    val draft: StateFlow<ClusterConfig> = _draft.asStateFlow()
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    fun allLayouts(): List<ClusterConfig> = c.layouts.all()
    fun activeLayout(): ClusterConfig = c.layouts.resolve(c.settings.value.activeLayoutId)

    // lifecycle
    fun startVehicle() = c.vehicle.start()
    fun stopVehicle() = c.vehicle.stop()

    // settings
    fun updateSettings(change: (AppSettings) -> AppSettings) = c.settings.update(change)
    fun resetSettings() {
        c.settings.reset()
        _draft.value = c.layouts.resolve(c.settings.value.activeLayoutId)
        _dirty.value = false
    }

    // simulation
    fun updateSim(change: (SimInputs) -> SimInputs) = c.vehicle.updateSim(change)
    fun resetTrip() = c.vehicle.resetTrip()

    // designer
    fun edit(change: (ClusterConfig) -> ClusterConfig) {
        _draft.value = change(_draft.value)
        _dirty.value = true
    }

    /** Loads [id] into the designer without activating it. */
    fun loadLayout(id: String) {
        _draft.value = c.layouts.resolve(id)
        _dirty.value = false
    }

    /** Makes [id] the layout shown on the full-screen cluster. */
    fun activateLayout(id: String) {
        val cfg = c.layouts.resolve(id)
        c.settings.update { it.copy(activeLayoutId = cfg.id) }
        _draft.value = cfg
        _dirty.value = false
    }

    /** Saves the draft. Built-in presets can't be overwritten, so they are saved as a new user layout. */
    fun saveDraft(newName: String? = null) {
        val d = _draft.value
        val stored = if (d.builtin || c.layouts.find(d.id) == null) c.layouts.saveAs(d, newName ?: d.name + " (mine)")
        else c.layouts.save(if (newName != null) d.copy(name = newName) else d)
        c.settings.update { it.copy(activeLayoutId = stored.id) }
        _draft.value = stored
        _dirty.value = false
    }

    fun saveDraftAs(name: String) {
        val stored = c.layouts.saveAs(_draft.value, name)
        c.settings.update { it.copy(activeLayoutId = stored.id) }
        _draft.value = stored
        _dirty.value = false
    }

    fun renameLayout(id: String, name: String) {
        c.layouts.rename(id, name)
        if (_draft.value.id == id) _draft.value = _draft.value.copy(name = name.trim().ifEmpty { _draft.value.name })
    }

    fun duplicateLayout(id: String) {
        val copy = c.layouts.duplicate(id) ?: return
        _draft.value = copy
        _dirty.value = false
    }

    fun deleteLayout(id: String) {
        c.layouts.delete(id)
        if (c.settings.value.activeLayoutId == id) c.settings.update { it.copy(activeLayoutId = BuiltinLayouts.CLASSIC_ID) }
        if (_draft.value.id == id) loadLayout(c.settings.value.activeLayoutId)
    }

    /** Throws away unsaved edits and, for built-in presets, restores the factory definition. */
    fun restoreDefault() {
        val d = _draft.value
        _draft.value = BuiltinLayouts.byId(d.id) ?: c.layouts.resolve(d.id).takeIf { !d.builtin } ?: BuiltinLayouts.classic()
        _dirty.value = false
    }

    /** Deletes all user layouts and switches back to the Classic preset. */
    fun restoreAllLayouts() {
        c.layouts.deleteAllUser()
        c.settings.update { it.copy(activeLayoutId = BuiltinLayouts.CLASSIC_ID) }
        loadLayout(BuiltinLayouts.CLASSIC_ID)
    }

    // import / export
    fun exportJson(): String = LayoutRepository.exportBundle(c.settings.value, c.layouts.exportJson())

    fun importJson(text: String): ImportResult = try {
        val o = JSONObject(text)
        if (o.optString("format") != "songplus-cluster-config") {
            ImportResult.Failed("Not a Song Plus cluster configuration file")
        } else {
            var s = false
            o.optJSONObject("settings")?.let { c.settings.replace(AppSettings.fromJson(it)); s = true }
            val n = c.layouts.importJson(o.optJSONArray("layouts") ?: JSONArray())
            loadLayout(c.settings.value.activeLayoutId)
            ImportResult.Ok(n, s)
        }
    } catch (e: Exception) {
        ImportResult.Failed(e.message ?: "Invalid JSON")
    }

    override fun onCleared() {
        c.vehicle.stop()
    }
}
