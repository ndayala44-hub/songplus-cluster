package com.songplus.cluster

import android.app.Application
import com.songplus.cluster.data.LayoutRepository
import com.songplus.cluster.data.SettingsStore
import com.songplus.cluster.data.TripStore
import com.songplus.cluster.media.MediaRepository
import com.songplus.cluster.vehicle.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-written dependency container (no DI library, to keep the dependency count low). */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val layouts = LayoutRepository(app)
    val trips = TripStore(app)
    val vehicle = VehicleRepository(app, settings, scope, trips)
    val media = MediaRepository(app)
}

class ClusterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
