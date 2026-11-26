package com.songplus.cluster.data

import org.json.JSONArray
import org.json.JSONObject

/** Every widget the cluster can show. */
enum class WidgetId { AURORA, SPEED, POWER, BATTERY, RANGE, GEAR, MODE, TELLTALES, CAR, TRIP, TYRES, CLOCK, WARNINGS, CONSUMPTION, PROJECTION, MUSIC }

enum class GaugeStyle { RING, BAR, DIGITAL, DIAL }
enum class BgStyle { SOLID, GRADIENT, GRID }
enum class FontStyle { SANS, LIGHT, MONO }
enum class AccentSource { MODE, FIXED }

/** Position and size as fractions (0..1) of the cluster canvas (1920 x 720 reference). */
data class WidgetSpec(val visible: Boolean, val x: Float, val y: Float, val w: Float, val h: Float) {
    fun clamped(): WidgetSpec {
        val nw = w.coerceIn(MIN_SIZE, 1f)
        val nh = h.coerceIn(MIN_SIZE, 1f)
        return copy(x = x.coerceIn(0f, 1f - nw), y = y.coerceIn(0f, 1f - nh), w = nw, h = nh)
    }

    companion object {
        const val MIN_SIZE = 0.05f
    }
}

data class ClusterConfig(
    val id: String,
    val name: String,
    val builtin: Boolean,
    val widgets: Map<WidgetId, WidgetSpec>,
    val gauge: GaugeStyle = GaugeStyle.RING,
    val background: BgStyle = BgStyle.GRADIENT,
    val glow: Boolean = true,
    val font: FontStyle = FontStyle.LIGHT,
    val digitScale: Float = 1f,
    val darkPalette: Int = 0,
    val lightPalette: Int = 0,
    val accentSource: AccentSource = AccentSource.MODE,
    val fixedAccent: Int = ModeColors.NORMAL
) {
    fun spec(id: WidgetId): WidgetSpec = widgets[id] ?: WidgetSpec(false, 0.4f, 0.4f, 0.2f, 0.2f)

    fun withSpec(id: WidgetId, spec: WidgetSpec): ClusterConfig =
        copy(widgets = widgets.toMutableMap().also { it[id] = spec.clamped() })

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("builtin", builtin)
        put("gauge", gauge.name); put("background", background.name); put("glow", glow)
        put("font", font.name); put("digitScale", digitScale.toDouble())
        put("darkPalette", darkPalette); put("lightPalette", lightPalette)
        put("accentSource", accentSource.name); put("fixedAccent", fixedAccent)
        val arr = JSONArray()
        for ((wid, s) in widgets) {
            arr.put(JSONObject().apply {
                put("id", wid.name); put("visible", s.visible)
                put("x", s.x.toDouble()); put("y", s.y.toDouble()); put("w", s.w.toDouble()); put("h", s.h.toDouble())
            })
        }
        put("widgets", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): ClusterConfig {
            val base = BuiltinLayouts.classic()
            val map = base.widgets.toMutableMap()
            val arr = o.optJSONArray("widgets")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val w = arr.optJSONObject(i) ?: continue
                    val wid = WidgetId.values().firstOrNull { it.name == w.optString("id") } ?: continue
                    map[wid] = WidgetSpec(
                        w.optBoolean("visible", true),
                        w.optDouble("x", 0.0).toFloat(), w.optDouble("y", 0.0).toFloat(),
                        w.optDouble("w", 0.2).toFloat(), w.optDouble("h", 0.2).toFloat()
                    ).clamped()
                }
            }
            return ClusterConfig(
                id = o.optString("id", "layout"),
                name = o.optString("name", "Layout"),
                builtin = false,
                widgets = map,
                gauge = o.enumOr("gauge", GaugeStyle.RING),
                background = o.enumOr("background", BgStyle.GRADIENT),
                glow = o.optBoolean("glow", true),
                font = o.enumOr("font", FontStyle.LIGHT),
                digitScale = o.optDouble("digitScale", 1.0).toFloat().coerceIn(0.6f, 1.4f),
                darkPalette = o.optInt("darkPalette", 0).coerceIn(0, 2),
                lightPalette = o.optInt("lightPalette", 0).coerceIn(0, 2),
                accentSource = o.enumOr("accentSource", AccentSource.MODE),
                fixedAccent = o.optInt("fixedAccent", ModeColors.NORMAL)
            )
        }
    }
}

/** Professionally arranged default layouts. Built-in layouts are read-only; editing one creates a copy. */
object BuiltinLayouts {
    const val CLASSIC_ID = "builtin_classic"
    const val FOCUS_ID = "builtin_focus"
    const val MINIMAL_ID = "builtin_minimal"

    private fun w(v: Boolean, x: Float, y: Float, w: Float, h: Float) = WidgetSpec(v, x, y, w, h)

    private fun classicRaw(): ClusterConfig = ClusterConfig(
        id = CLASSIC_ID, name = "Song Plus – Twin rings", builtin = true,
        widgets = mapOf(
            WidgetId.BATTERY to w(true, 0.02f, 0.07f, 0.27f, 0.78f),
            WidgetId.SPEED to w(true, 0.71f, 0.07f, 0.27f, 0.78f),
            WidgetId.CAR to w(true, 0.32f, 0.16f, 0.36f, 0.47f),
            WidgetId.CLOCK to w(true, 0.35f, 0.02f, 0.30f, 0.07f),
            WidgetId.TELLTALES to w(true, 0.31f, 0.09f, 0.38f, 0.08f),
            WidgetId.POWER to w(true, 0.33f, 0.66f, 0.34f, 0.07f),
            WidgetId.GEAR to w(true, 0.44f, 0.74f, 0.12f, 0.09f),
            WidgetId.WARNINGS to w(true, 0.30f, 0.84f, 0.40f, 0.07f),
            WidgetId.TRIP to w(true, 0.02f, 0.89f, 0.27f, 0.09f),
            WidgetId.MODE to w(true, 0.36f, 0.92f, 0.28f, 0.06f),
            WidgetId.TYRES to w(true, 0.71f, 0.89f, 0.27f, 0.09f),
            WidgetId.RANGE to w(false, 0.05f, 0.10f, 0.20f, 0.20f),
            WidgetId.CONSUMPTION to w(false, 0.05f, 0.70f, 0.20f, 0.10f)
        )
    )

    private fun focusRaw(): ClusterConfig = ClusterConfig(
        id = FOCUS_ID, name = "Focus – centre speed", builtin = true,
        gauge = GaugeStyle.DIGITAL, glow = false, font = FontStyle.SANS,
        widgets = mapOf(
            WidgetId.SPEED to w(true, 0.33f, 0.10f, 0.34f, 0.50f),
            WidgetId.POWER to w(true, 0.28f, 0.62f, 0.44f, 0.07f),
            WidgetId.BATTERY to w(true, 0.03f, 0.10f, 0.24f, 0.30f),
            WidgetId.RANGE to w(true, 0.03f, 0.44f, 0.24f, 0.16f),
            WidgetId.CONSUMPTION to w(true, 0.03f, 0.64f, 0.24f, 0.12f),
            WidgetId.CAR to w(true, 0.73f, 0.12f, 0.25f, 0.44f),
            WidgetId.CLOCK to w(true, 0.73f, 0.60f, 0.25f, 0.07f),
            WidgetId.TELLTALES to w(true, 0.28f, 0.02f, 0.44f, 0.08f),
            WidgetId.GEAR to w(true, 0.44f, 0.72f, 0.12f, 0.09f),
            WidgetId.MODE to w(true, 0.33f, 0.83f, 0.34f, 0.07f),
            WidgetId.WARNINGS to w(true, 0.25f, 0.91f, 0.50f, 0.07f),
            WidgetId.TRIP to w(true, 0.03f, 0.90f, 0.22f, 0.08f),
            WidgetId.TYRES to w(true, 0.76f, 0.90f, 0.22f, 0.08f)
        )
    )

    private fun minimalRaw(): ClusterConfig = ClusterConfig(
        id = MINIMAL_ID, name = "Minimal – speed & charge", builtin = true,
        gauge = GaugeStyle.BAR, glow = false, font = FontStyle.LIGHT, background = BgStyle.SOLID,
        widgets = mapOf(
            WidgetId.SPEED to w(true, 0.30f, 0.18f, 0.40f, 0.38f),
            WidgetId.BATTERY to w(true, 0.30f, 0.60f, 0.40f, 0.12f),
            WidgetId.GEAR to w(true, 0.05f, 0.40f, 0.12f, 0.18f),
            WidgetId.MODE to w(true, 0.80f, 0.40f, 0.15f, 0.12f),
            WidgetId.TELLTALES to w(true, 0.25f, 0.04f, 0.50f, 0.09f),
            WidgetId.WARNINGS to w(true, 0.25f, 0.84f, 0.50f, 0.08f),
            WidgetId.POWER to w(false, 0.33f, 0.74f, 0.34f, 0.06f),
            WidgetId.CAR to w(false, 0.35f, 0.15f, 0.30f, 0.45f),
            WidgetId.RANGE to w(false, 0.05f, 0.10f, 0.20f, 0.20f),
            WidgetId.CLOCK to w(false, 0.80f, 0.05f, 0.15f, 0.07f),
            WidgetId.TRIP to w(false, 0.02f, 0.89f, 0.25f, 0.09f),
            WidgetId.TYRES to w(false, 0.72f, 0.89f, 0.25f, 0.09f),
            WidgetId.CONSUMPTION to w(false, 0.05f, 0.70f, 0.20f, 0.10f)
        )
    )

    const val SPORT_ID = "builtin_sport"
    const val TRIPS_ID = "builtin_trips"
    const val AURORA_ID = "builtin_aurora"

    /** Adds any widget a layout does not mention as hidden, so every layout covers every widget. */
    private fun full(c: ClusterConfig): ClusterConfig =
        c.copy(widgets = WidgetId.values().associateWith { c.widgets[it] ?: WidgetSpec(false, 0.4f, 0.4f, 0.2f, 0.2f) })

    fun classic(): ClusterConfig = full(classicRaw())
    fun focus(): ClusterConfig = full(focusRaw())
    fun minimal(): ClusterConfig = full(minimalRaw())

    /** Analogue needle speedometer on the left, battery ring on the right. */
    fun sport(): ClusterConfig = full(ClusterConfig(
        id = SPORT_ID, name = "Sport – analogue dial", builtin = true,
        gauge = GaugeStyle.DIAL, glow = true, font = FontStyle.SANS, darkPalette = 1, lightPalette = 1,
        widgets = mapOf(
            WidgetId.SPEED to w(true, 0.02f, 0.05f, 0.42f, 0.80f),
            WidgetId.BATTERY to w(true, 0.62f, 0.07f, 0.36f, 0.62f),
            WidgetId.CAR to w(true, 0.42f, 0.14f, 0.22f, 0.40f),
            WidgetId.TELLTALES to w(true, 0.28f, 0.01f, 0.44f, 0.08f),
            WidgetId.POWER to w(true, 0.40f, 0.60f, 0.26f, 0.07f),
            WidgetId.GEAR to w(true, 0.45f, 0.70f, 0.12f, 0.09f),
            WidgetId.MODE to w(true, 0.62f, 0.72f, 0.36f, 0.07f),
            WidgetId.WARNINGS to w(true, 0.30f, 0.86f, 0.40f, 0.07f),
            WidgetId.TRIP to w(true, 0.02f, 0.90f, 0.27f, 0.08f),
            WidgetId.TYRES to w(true, 0.71f, 0.90f, 0.27f, 0.08f)
        )
    ))

    /** Trip-oriented layout: speed, battery and the range projection from recent consumption. */
    fun trips(): ClusterConfig = full(ClusterConfig(
        id = TRIPS_ID, name = "Trip – range projection", builtin = true,
        gauge = GaugeStyle.RING, glow = false, font = FontStyle.LIGHT, darkPalette = 2, lightPalette = 2,
        widgets = mapOf(
            WidgetId.SPEED to w(true, 0.03f, 0.07f, 0.30f, 0.72f),
            WidgetId.PROJECTION to w(true, 0.36f, 0.12f, 0.28f, 0.34f),
            WidgetId.BATTERY to w(true, 0.67f, 0.07f, 0.30f, 0.72f),
            WidgetId.CONSUMPTION to w(true, 0.38f, 0.50f, 0.24f, 0.14f),
            WidgetId.TELLTALES to w(true, 0.28f, 0.01f, 0.44f, 0.08f),
            WidgetId.GEAR to w(true, 0.44f, 0.68f, 0.12f, 0.09f),
            WidgetId.MODE to w(true, 0.36f, 0.78f, 0.28f, 0.06f),
            WidgetId.WARNINGS to w(true, 0.30f, 0.86f, 0.40f, 0.07f),
            WidgetId.TRIP to w(true, 0.03f, 0.90f, 0.27f, 0.08f),
            WidgetId.TYRES to w(true, 0.71f, 0.90f, 0.27f, 0.08f)
        )
    ))

    /** Slick reactive scene: ring gauges, moving road, glow that follows power, braking, lights and mode. */
    fun aurora(): ClusterConfig = full(ClusterConfig(
        id = AURORA_ID, name = "Aurora – reactive scene", builtin = true,
        gauge = GaugeStyle.RING, glow = true, font = FontStyle.LIGHT, background = BgStyle.SOLID,
        darkPalette = 1, lightPalette = 1,
        accentSource = AccentSource.MODE,
        widgets = mapOf(
            WidgetId.AURORA to w(true, 0f, 0f, 1f, 1f),
            WidgetId.CAR to w(true, 0.37f, 0.45f, 0.26f, 0.37f),
            WidgetId.TELLTALES to w(true, 0.33f, 0.115f, 0.34f, 0.07f),
            WidgetId.WARNINGS to w(true, 0.32f, 0.835f, 0.36f, 0.065f),
            WidgetId.MUSIC to w(true, 0.31f, 0.835f, 0.38f, 0.108f)
        )
    ))

    fun all(): List<ClusterConfig> = listOf(aurora(), classic(), focus(), minimal(), sport(), trips())

    fun byId(id: String): ClusterConfig? = all().firstOrNull { it.id == id }
}
