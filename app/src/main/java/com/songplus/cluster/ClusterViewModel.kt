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
