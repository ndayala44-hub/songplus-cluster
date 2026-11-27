package com.songplus.cluster

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.songplus.cluster.data.AppSettings
import com.songplus.cluster.data.Lang
import com.songplus.cluster.data.OrientationPref
import com.songplus.cluster.data.Startup
import com.songplus.cluster.ui.cluster.ClusterCanvas
import com.songplus.cluster.ui.cluster.IntroOverlay
import com.songplus.cluster.ui.designer.DesignerScreen
import com.songplus.cluster.ui.settings.SettingsScreen
import com.songplus.cluster.ui.trips.TripsScreen
import com.songplus.cluster.ui.theme.AppTheme
import com.songplus.cluster.ui.theme.accentFor
import com.songplus.cluster.ui.theme.resolveDark
import kotlinx.coroutines.delay

private enum class Screen { CLUSTER, TRIPS, DESIGNER, SETTINGS }

class MainActivity : AppCompatActivity() {
    private val vm: ClusterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val startup = vm.settings.value.startup
        // Debug builds only: lets automated tests open a given screen and seed clearly labelled demo trips.
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val openScreen = if (debuggable) intent.getStringExtra("screen") else null
        val openCat = if (debuggable) intent.getIntExtra("cat", 0) else 0
        if (debuggable && intent.getBooleanExtra("seed_demo", false)) vm.seedDemoTrips()
        if (debuggable) {
            // test hooks: choose drive mode / gear in the simulator (this switches auto-drive off)
            val dm = intent.getStringExtra("mode")
            val gr = intent.getStringExtra("gear")
            if (dm != null || gr != null) {
                vm.updateSettings { it.copy(simAutoDrive = false) }
                vm.updateSim { i ->
                    i.copy(
                        mode = when (dm) { "eco" -> com.songplus.cluster.data.DriveMode.ECO; "sport" -> com.songplus.cluster.data.DriveMode.SPORT; "normal" -> com.songplus.cluster.data.DriveMode.NORMAL; else -> i.mode },
                        gear = when (gr) { "R" -> com.songplus.cluster.data.Gear.R; "D" -> com.songplus.cluster.data.Gear.D; "P" -> com.songplus.cluster.data.Gear.P; else -> i.gear },
                        targetSpeedKmh = intent.getFloatExtra("speed", 0f)
                    )
                }
            }
            if (intent.getBooleanExtra("test_media", false)) com.songplus.cluster.debug.TestMediaSession.start(this)
            intent.getStringExtra("cam_url")?.let { u -> vm.updateSettings { it.copy(camSource = com.songplus.cluster.data.CamSource.MJPEG_URL, camUrl = u) } }
        }
        if (debuggable) when (intent.getStringExtra("theme")) {
            "light" -> vm.updateSettings { it.copy(themeMode = com.songplus.cluster.data.ThemeMode.LIGHT) }
            "dark" -> vm.updateSettings { it.copy(themeMode = com.songplus.cluster.data.ThemeMode.DARK) }
        }
        setContent {
            val settings by vm.settings.collectAsState()
            val sysDark = isSystemInDarkTheme()
            val tick by produceState(0) { while (true) { delay(30_000); value += 1 } }
            val dark = remember(settings.themeMode, settings.autoDarkFromHour, settings.autoDarkToHour, sysDark, tick) {
                resolveDark(settings, sysDark)
            }
            ApplyWindowSettings(settings)
            AppTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(startup, dark, settings, openScreen, openCat)
                }
            }
        }
    }

    // Cluster-projection cooperation: when DashCast moves this app to the cluster display (or that display
    // disconnects), Android fires display callbacks. The Compose canvas re-renders from flows and the activity
    // handles config changes without recreation, so we only log here — but registering the listener guarantees we
    // react to a late-appearing or disconnecting cluster display without stale state, and never crash on it.
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { android.util.Log.i("SongPlusCluster", "display added: $displayId") }
        override fun onDisplayRemoved(displayId: Int) { android.util.Log.i("SongPlusCluster", "display removed: $displayId") }
        override fun onDisplayChanged(displayId: Int) { /* size/density change handled by configChanges + fractional layout */ }
    }

    override fun onStart() {
        super.onStart()
        vm.startVehicle()
        vm.refreshMedia()
        try {
            (getSystemService(DISPLAY_SERVICE) as? android.hardware.display.DisplayManager)
                ?.registerDisplayListener(displayListener, null)
        } catch (t: Throwable) { android.util.Log.w("SongPlusCluster", "display listener: ${t.message}") }
    }

    override fun onStop() {
        vm.stopVehicle()
        try {
            (getSystemService(DISPLAY_SERVICE) as? android.hardware.display.DisplayManager)
                ?.unregisterDisplayListener(displayListener)
        } catch (t: Throwable) { /* ignore */ }
        super.onStop()
    }

    @Composable
    private fun ApplyWindowSettings(s: AppSettings) {
        LaunchedEffect(s.language) {
            val wanted = if (s.language == Lang.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(s.language.tag)
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
                AppCompatDelegate.setApplicationLocales(wanted)
            }
        }
        LaunchedEffect(s.orientation) {
            requestedOrientation =
                if (s.orientation == OrientationPref.LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        LaunchedEffect(s.keepScreenOn) {
            if (s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        LaunchedEffect(s.brightness) {
            val lp = window.attributes
            lp.screenBrightness = if (s.brightness > 0f) s.brightness.coerceIn(0.1f, 1f) else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }

    private fun setBarsHidden(hidden: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (hidden) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    @Composable
    private fun AppRoot(startup: Startup, dark: Boolean, settings: AppSettings, openScreen: String?, openCat: Int) {
        var screen by remember {
            mutableStateOf(
                when (openScreen) {
                    "trips" -> Screen.TRIPS
                    "designer" -> Screen.DESIGNER
                    "settings" -> Screen.SETTINGS
                    "cluster" -> Screen.CLUSTER
                    else -> if (startup == Startup.DESIGNER) Screen.DESIGNER else Screen.CLUSTER
                }
            )
        }
        var chromeHidden by remember { mutableStateOf(startup == Startup.FULLSCREEN) }
        var menuVisible by remember { mutableStateOf(false) }
        var menuStamp by remember { mutableIntStateOf(0) }
        var intro by remember { mutableStateOf(settings.playIntro && openScreen == null) }

        LaunchedEffect(screen, settings.hideSystemBars, chromeHidden) {
            setBarsHidden(screen == Screen.CLUSTER && (settings.hideSystemBars || chromeHidden))
        }
        LaunchedEffect(menuStamp) {
            if (menuVisible) {
                delay(6_000)
                menuVisible = false
            }
        }
        BackHandler(enabled = screen != Screen.CLUSTER) { screen = Screen.CLUSTER }

        Box(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.CLUSTER -> ClusterFullScreen(
                    dark, settings,
                    onTap = { if (!chromeHidden) { menuVisible = true; menuStamp++ } },
                    onLongPress = { menuVisible = true; menuStamp++ }
                )
                else -> Row(Modifier.fillMaxSize()) {
                    Rail(screen) { screen = it }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (screen) {
                            Screen.DESIGNER -> DesignerScreen(vm)
                            Screen.TRIPS -> TripsScreen(vm)
                            else -> SettingsScreen(vm, onOpenDesigner = { screen = Screen.DESIGNER }, initialCategory = openCat)
                        }
                    }
                }
            }
            if (screen == Screen.CLUSTER && menuVisible) {
                Surface(
                    Modifier.align(Alignment.CenterStart).padding(12.dp),
                    shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, shadowElevation = 8.dp
                ) {
                    Rail(screen, compact = true) { screen = it; menuVisible = false }
                }
            }
            if (intro && screen == Screen.CLUSTER) {
                val cfg = remember(settings.activeLayoutId) { vm.activeLayout() }
                val mode by vm.vehicle.collectAsState()
                IntroOverlay(accentFor(settings, cfg, mode.mode.value, dark), dark) { intro = false }
            }
        }
    }

    @Composable
    private fun ClusterFullScreen(dark: Boolean, settings: AppSettings, onTap: () -> Unit, onLongPress: () -> Unit) {
        val state by vm.vehicle.collectAsState()
        val status by vm.status.collectAsState()
        val users by vm.userLayouts.collectAsState()
        val projection by vm.projection.collectAsState()
        val media by vm.media.collectAsState()
        val mediaAccess by vm.mediaAccess.collectAsState()
        val cfg = remember(settings.activeLayoutId, users) { vm.activeLayout() }
        val gestures = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
        }
        if (settings.factoryPanelOnly) {
            // Draws nothing, so the car's own cluster (or a black screen on a normal display) stays visible.
            Box(gestures.background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.BottomCenter) {
                Text(
                    stringResource(R.string.factory_panel_active),
                    color = androidx.compose.ui.graphics.Color(0xFF8C98A6),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            ClusterCanvas(state, cfg, settings, status, dark, gestures, projection = projection, media = media, mediaAccess = mediaAccess, liveCamera = true)
        }
    }

    @Composable
    private fun Rail(current: Screen, compact: Boolean = false, onSelect: (Screen) -> Unit) {
        Column(
            Modifier.then(if (compact) Modifier else Modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface)).width(if (compact) 150.dp else 130.dp).padding(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            val items = listOf(Screen.CLUSTER to R.string.nav_cluster, Screen.TRIPS to R.string.nav_trips, Screen.DESIGNER to R.string.nav_designer, Screen.SETTINGS to R.string.nav_settings)
            for ((s, label) in items) {
                TextButton(onClick = { onSelect(s) }, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        stringResource(label),
                        color = if (s == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}
