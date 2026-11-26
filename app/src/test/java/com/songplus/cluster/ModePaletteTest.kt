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

    @Test fun redIsNeverAModeColour() {
        for (dark in listOf(true, false)) {
            for (m in DriveMode.values()) {
                val p = modePalette(m, dark)
                for (c in listOf(p.accent, p.accent2, p.bgTop, p.bgBottom, p.edge)) assertFalse("red mode colour in $m dark=$dark: $c", isRedHue(c))
                assertTrue("alert must stay a warning red", isRedHue(p.alert))
            }
        }
    }

    @Test fun brandPaletteHasNoRedAccents() {
        for (c in listOf(Brand.BLUE, Brand.CYAN, Brand.MINT, Brand.VIOLET, Brand.MAGENTA, Brand.SPORT, Brand.AMBER)) assertFalse(isRedHue(c))
        assertTrue(isRedHue(Brand.CORAL))
        for (c in Brand.CALM_CHOICES + Brand.SPORT_CHOICES) assertFalse(isRedHue(Color(c)))
    }

    @Test fun defaultAccents() {
        assertFalse(isRedHue(Color(ModeColors.ECO)))
        assertFalse(isRedHue(Color(ModeColors.NORMAL)))
        assertFalse(isRedHue(Color(ModeColors.SPORT)))
    }

    @Test fun oldDefaultsMigrateToPalette() {
        val old = JSONObject().put("accentEco", 0xFF2FE3A0.toInt()).put("accentNormal", 0xFF4FA3FF.toInt()).put("accentSport", 0xFFE5334B.toInt())
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
