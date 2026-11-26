package com.songplus.cluster

import androidx.compose.ui.graphics.Color
import com.songplus.cluster.data.AppSettings
import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.data.ModeColors
import com.songplus.cluster.ui.theme.Brand
import com.songplus.cluster.ui.theme.isRedHue
import com.songplus.cluster.ui.theme.modePalette
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModePaletteTest {
    private fun colors(m: DriveMode, dark: Boolean): List<Color> {
        val p = modePalette(m, dark)
        return listOf(p.accent, p.accent2, p.bgTop, p.bgBottom, p.alert, p.brake)
    }

    @Test fun redOnlyInSport() {
        for (dark in listOf(true, false)) {
            for (m in listOf(DriveMode.ECO, DriveMode.NORMAL)) {
                for (c in colors(m, dark)) assertFalse("red found in $m dark=$dark: $c", isRedHue(c))
            }
            val sport = modePalette(DriveMode.SPORT, dark)
            assertTrue("Sport accent must be red", isRedHue(sport.accent))
            assertTrue("Sport alert must be red", isRedHue(sport.alert))
        }
    }

    @Test fun brandColoursAreNotRedExceptSport() {
        assertFalse(isRedHue(Brand.BLUE)); assertFalse(isRedHue(Brand.CYAN)); assertFalse(isRedHue(Brand.MINT)); assertFalse(isRedHue(Brand.AMBER))
        assertTrue(isRedHue(Brand.SPORT_RED))
        for (c in Brand.CALM_CHOICES) assertFalse(isRedHue(Color(c)))
    }

    @Test fun defaultAccents() {
        assertFalse(isRedHue(Color(ModeColors.ECO)))
        assertFalse(isRedHue(Color(ModeColors.NORMAL)))
        assertTrue(isRedHue(Color(ModeColors.SPORT)))
    }

    @Test fun oldDefaultsMigrateToPalette() {
        val old = JSONObject().put("accentEco", 0xFF2FE3A0.toInt()).put("accentNormal", 0xFF4FA3FF.toInt()).put("accentSport", 0xFFFF5A3C.toInt())
        val s = AppSettings.fromJson(old)
        assertEquals(ModeColors.ECO, s.accentEco)
        assertEquals(ModeColors.NORMAL, s.accentNormal)
        assertEquals(ModeColors.SPORT, s.accentSport)
        val custom = AppSettings.fromJson(JSONObject().put("accentNormal", 0xFF123456.toInt()))
        assertEquals(0xFF123456.toInt(), custom.accentNormal)
    }

    @Test fun cameraSettingsRoundTrip() {
        val a = AppSettings().copy(camUrl = "http://192.168.1.50:8080/video", camId = "2", camMirror = true)
        val b = AppSettings.fromJson(JSONObject(a.toJson().toString()))
        assertEquals(a.camUrl, b.camUrl); assertEquals("2", b.camId); assertTrue(b.camMirror); assertTrue(b.reverseCamera)
    }
}
