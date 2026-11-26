package com.songplus.cluster.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class Lang(val tag: String) { SYSTEM(""), EN("en"), FR("fr") }
enum class SpeedUnit { KMH, MPH }
enum class DistUnit { KM, MI }
enum class TempUnit { C, F }
enum class Startup { CLUSTER, DESIGNER, FULLSCREEN }
enum class OrientationPref { LANDSCAPE, AUTO }
enum class ThemeMode { SYSTEM, LIGHT, DARK, AUTO_TIME }
enum class AnimLevel { FULL, REDUCED, OFF }
enum class DataMode { AUTO, SIMULATION, BYD_ONLY }
enum class BatteryDisplay { PERCENT_RANGE, PERCENT, RANGE }
enum class SpeedDisplayMode { REAL, PERCENT, PLUS_KMH }
enum class BlinkRate(val periodMs: Int) { NORMAL(600), SLOW(900), FAST(450) }

/** Default accent colours per drive mode (dark theme). Light theme uses a darkened variant. */
object ModeColors {
    /** Brand palette: Eco mint, Normal cyan, Sport red. Red is reserved for Sport. */
    const val ECO = 0xFF90FC9C.toInt()
    const val NORMAL = 0xFF00C6FC.toInt()
    const val SPORT = 0xFFE5334B.toInt()

    /** Defaults of earlier versions; saved settings still holding one of these move to the new palette. */
    val OLD_DEFAULTS = setOf(0xFF2FE3A0.toInt(), 0xFF4FA3FF.toInt(), 0xFFFF5A3C.toInt())
}

/** Where the reverse-camera picture comes from. */
enum class CamSource { AUTO, CAMERA2_ID, MJPEG_URL }

data class AppSettings(
    val language: Lang = Lang.SYSTEM,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val distUnit: DistUnit = DistUnit.KM,
    val tempUnit: TempUnit = TempUnit.C,
    val startup: Startup = Startup.CLUSTER,
    val playIntro: Boolean = true,
    val orientation: OrientationPref = OrientationPref.LANDSCAPE,
    val hideSystemBars: Boolean = true,
    val keepScreenOn: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val autoDarkFromHour: Int = 18,
    val autoDarkToHour: Int = 6,
    val accentEco: Int = ModeColors.ECO,
    val accentNormal: Int = ModeColors.NORMAL,
    val accentSport: Int = ModeColors.SPORT,
    val animation: AnimLevel = AnimLevel.FULL,
    /** 0 = leave brightness to the system, otherwise 0.1..1.0 applied to this app's window. */
    val brightness: Float = 0f,
    val activeLayoutId: String = BuiltinLayouts.AURORA_ID,
    val batteryDisplay: BatteryDisplay = BatteryDisplay.PERCENT_RANGE,
    val blinkRate: BlinkRate = BlinkRate.NORMAL,
    val showHazardBanner: Boolean = true,
    val dataMode: DataMode = DataMode.AUTO,
    val refreshHz: Int = 10,
    val simAutoDrive: Boolean = true,
    val debugOverlay: Boolean = false,
    val reverseCamera: Boolean = true,
    val camSource: CamSource = CamSource.AUTO,
    val camId: String = "",
    val camUrl: String = "",
    val camMirror: Boolean = false,
    val camGuides: Boolean = true,
    val speedMode: SpeedDisplayMode = SpeedDisplayMode.REAL,
    val speedPercent: Float = 4.5f,
    val speedPlusKmh: Float = 5f,
    val showGear: Boolean = true,
    val factoryPanelOnly: Boolean = false,
    val capacityKwh: Float = 71.8f,
    val rangeWindowKm: Int = 100,
    val tripsEnabled: Boolean = true,
    val tripAutoStopSec: Int = 180,
    val tripMinKm: Float = 0.5f,
    val recordSimTrips: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("language", language.name); put("speedUnit", speedUnit.name); put("distUnit", distUnit.name)
        put("tempUnit", tempUnit.name); put("startup", startup.name); put("playIntro", playIntro)
        put("orientation", orientation.name); put("hideSystemBars", hideSystemBars); put("keepScreenOn", keepScreenOn)
        put("themeMode", themeMode.name); put("autoDarkFromHour", autoDarkFromHour); put("autoDarkToHour", autoDarkToHour)
        put("accentEco", accentEco); put("accentNormal", accentNormal); put("accentSport", accentSport)
        put("animation", animation.name); put("brightness", brightness.toDouble())
        put("activeLayoutId", activeLayoutId); put("batteryDisplay", batteryDisplay.name)
        put("blinkRate", blinkRate.name); put("showHazardBanner", showHazardBanner)
        put("reverseCamera", reverseCamera); put("camSource", camSource.name); put("camId", camId); put("camUrl", camUrl)
        put("camMirror", camMirror); put("camGuides", camGuides)
        put("dataMode", dataMode.name); put("refreshHz", refreshHz); put("simAutoDrive", simAutoDrive)
        put("debugOverlay", debugOverlay)
        put("speedMode", speedMode.name); put("speedPercent", speedPercent.toDouble()); put("speedPlusKmh", speedPlusKmh.toDouble())
        put("showGear", showGear); put("factoryPanelOnly", factoryPanelOnly)
        put("capacityKwh", capacityKwh.toDouble()); put("rangeWindowKm", rangeWindowKm)
        put("tripsEnabled", tripsEnabled); put("tripAutoStopSec", tripAutoStopSec)
        put("tripMinKm", tripMinKm.toDouble()); put("recordSimTrips", recordSimTrips)
    }

    companion object {
        fun fromJson(o: JSONObject): AppSettings {
            val d = AppSettings()
            return AppSettings(
                language = o.enumOr("language", d.language),
                speedUnit = o.enumOr("speedUnit", d.speedUnit),
                distUnit = o.enumOr("distUnit", d.distUnit),
                tempUnit = o.enumOr("tempUnit", d.tempUnit),
                startup = o.enumOr("startup", d.startup),
                playIntro = o.optBoolean("playIntro", d.playIntro),
                orientation = o.enumOr("orientation", d.orientation),
                hideSystemBars = o.optBoolean("hideSystemBars", d.hideSystemBars),
                keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
                themeMode = o.enumOr("themeMode", d.themeMode),
                autoDarkFromHour = o.optInt("autoDarkFromHour", d.autoDarkFromHour).coerceIn(0, 23),
                autoDarkToHour = o.optInt("autoDarkToHour", d.autoDarkToHour).coerceIn(0, 23),
                accentEco = o.optInt("accentEco", d.accentEco).let { if (it in ModeColors.OLD_DEFAULTS) d.accentEco else it },
                accentNormal = o.optInt("accentNormal", d.accentNormal).let { if (it in ModeColors.OLD_DEFAULTS) d.accentNormal else it },
                accentSport = o.optInt("accentSport", d.accentSport).let { if (it in ModeColors.OLD_DEFAULTS) d.accentSport else it },
                reverseCamera = o.optBoolean("reverseCamera", d.reverseCamera),
                camSource = o.enumOr("camSource", d.camSource),
                camId = o.optString("camId", d.camId),
                camUrl = o.optString("camUrl", d.camUrl),
                camMirror = o.optBoolean("camMirror", d.camMirror),
                camGuides = o.optBoolean("camGuides", d.camGuides),
                animation = o.enumOr("animation", d.animation),
                brightness = o.optDouble("brightness", d.brightness.toDouble()).toFloat().coerceIn(0f, 1f),
                activeLayoutId = o.optString("activeLayoutId", d.activeLayoutId),
                batteryDisplay = o.enumOr("batteryDisplay", d.batteryDisplay),
                blinkRate = o.enumOr("blinkRate", d.blinkRate),
                showHazardBanner = o.optBoolean("showHazardBanner", d.showHazardBanner),
                dataMode = o.enumOr("dataMode", d.dataMode),
                refreshHz = o.optInt("refreshHz", d.refreshHz).coerceIn(1, 30),
                simAutoDrive = o.optBoolean("simAutoDrive", d.simAutoDrive),
                debugOverlay = o.optBoolean("debugOverlay", d.debugOverlay),
                speedMode = o.enumOr("speedMode", d.speedMode),
                speedPercent = o.optDouble("speedPercent", d.speedPercent.toDouble()).toFloat().coerceIn(0f, 10f),
                speedPlusKmh = o.optDouble("speedPlusKmh", d.speedPlusKmh.toDouble()).toFloat().coerceIn(0f, 10f),
                showGear = o.optBoolean("showGear", d.showGear),
                factoryPanelOnly = o.optBoolean("factoryPanelOnly", d.factoryPanelOnly),
                capacityKwh = o.optDouble("capacityKwh", d.capacityKwh.toDouble()).toFloat().coerceIn(20f, 200f),
                rangeWindowKm = o.optInt("rangeWindowKm", d.rangeWindowKm).let { if (it in listOf(10, 100, 500)) it else d.rangeWindowKm },
                tripsEnabled = o.optBoolean("tripsEnabled", d.tripsEnabled),
                tripAutoStopSec = o.optInt("tripAutoStopSec", d.tripAutoStopSec).coerceIn(30, 3600),
                tripMinKm = o.optDouble("tripMinKm", d.tripMinKm.toDouble()).toFloat().coerceIn(0f, 20f),
                recordSimTrips = o.optBoolean("recordSimTrips", d.recordSimTrips)
            )
        }
    }
}

/** Reads an enum by name, falling back to [default] when missing or unknown (forward/backward compatible). */
inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, default: E): E {
    val name = optString(key, "")
    return enumValues<E>().firstOrNull { it.name == name } ?: default
}

/** Persistent settings, stored as one JSON document in SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cluster_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = _flow.asStateFlow()
    val value: AppSettings get() = _flow.value

    private fun load(): AppSettings {
        val raw = prefs.getString(KEY, null) ?: return AppSettings()
        return try {
            AppSettings.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(_flow.value)
        _flow.value = next
        prefs.edit().putString(KEY, next.toJson().toString()).apply()
    }

    fun replace(settings: AppSettings) = update { settings }

    fun reset() = replace(AppSettings())

    companion object {
        private const val KEY = "json"
    }
}
