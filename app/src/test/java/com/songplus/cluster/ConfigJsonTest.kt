package com.songplus.cluster

import com.songplus.cluster.data.AppSettings
import com.songplus.cluster.data.BgStyle
import com.songplus.cluster.data.BuiltinLayouts
import com.songplus.cluster.data.ClusterConfig
import com.songplus.cluster.data.DataMode
import com.songplus.cluster.data.GaugeStyle
import com.songplus.cluster.data.SpeedUnit
import com.songplus.cluster.data.ThemeMode
import com.songplus.cluster.data.WidgetId
import com.songplus.cluster.data.WidgetSpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigJsonTest {
    @Test fun layoutRoundTrip() {
        val a = BuiltinLayouts.focus().copy(name = "Mine", builtin = false, gauge = GaugeStyle.BAR, background = BgStyle.GRID, digitScale = 1.2f)
            .withSpec(WidgetId.SPEED, WidgetSpec(true, 0.1f, 0.2f, 0.3f, 0.4f))
        val b = ClusterConfig.fromJson(JSONObject(a.toJson().toString()))
        assertEquals(a.name, b.name)
        assertEquals(GaugeStyle.BAR, b.gauge)
        assertEquals(BgStyle.GRID, b.background)
        assertEquals(1.2f, b.digitScale, 0.001f)
        for (id in WidgetId.values()) {
            assertEquals(a.spec(id).visible, b.spec(id).visible)
            assertEquals(a.spec(id).x, b.spec(id).x, 0.0001f)
            assertEquals(a.spec(id).w, b.spec(id).w, 0.0001f)
        }
    }

    @Test fun importedLayoutIsNeverBuiltin() {
        val b = ClusterConfig.fromJson(JSONObject(BuiltinLayouts.classic().toJson().toString()))
        assertFalse(b.builtin)
    }

    @Test fun corruptGeometryIsClamped() {
        val o = BuiltinLayouts.classic().toJson()
        val arr = o.getJSONArray("widgets")
        arr.getJSONObject(0).put("x", 5.0).put("w", 9.0)
        val b = ClusterConfig.fromJson(o)
        for (id in WidgetId.values()) {
            val s = b.spec(id)
            assertTrue(s.x >= 0f && s.x + s.w <= 1.0001f && s.y >= 0f && s.y + s.h <= 1.0001f)
        }
    }

    @Test fun unknownWidgetAndMissingFieldsAreTolerated() {
        val b = ClusterConfig.fromJson(JSONObject("""{"id":"x","name":"n","widgets":[{"id":"NOPE","x":0.1}]}"""))
        assertEquals("n", b.name)
        assertEquals(WidgetId.values().size, b.widgets.size)
    }

    @Test fun builtinLayoutsCoverAllWidgetsAndFitCanvas() {
        for (l in BuiltinLayouts.all()) {
            assertEquals(WidgetId.values().size, l.widgets.size)
            for ((_, s) in l.widgets) assertTrue(s.x >= 0f && s.y >= 0f && s.x + s.w <= 1.0001f && s.y + s.h <= 1.0001f)
        }
    }

    @Test fun settingsRoundTrip() {
        val a = AppSettings(speedUnit = SpeedUnit.MPH, themeMode = ThemeMode.AUTO_TIME, dataMode = DataMode.BYD_ONLY, refreshHz = 20, accentSport = 0xFF123456.toInt())
        val b = AppSettings.fromJson(JSONObject(a.toJson().toString()))
        assertEquals(a, b)
    }

    @Test fun settingsToleratesUnknownEnumAndRange() {
        val b = AppSettings.fromJson(JSONObject("""{"speedUnit":"FURLONGS","refreshHz":9999,"brightness":7}"""))
        assertEquals(SpeedUnit.KMH, b.speedUnit)
        assertEquals(30, b.refreshHz)
        assertEquals(1f, b.brightness, 0.0001f)
    }
}
