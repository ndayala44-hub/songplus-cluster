package com.songplus.cluster.ui.theme

import androidx.compose.ui.graphics.Color
import com.songplus.cluster.data.DriveMode
import kotlin.math.max
import kotlin.math.min

/**
 * The brand palette (taken from the supplied colour reference) and the per-drive-mode colour system.
 *
 * Rule: red is used only in Sport mode. Eco is green, Normal is blue/cyan. Safety alerts in Eco and Normal are
 * amber instead of red, so the red of Sport keeps its meaning.
 */
object Brand {
    val BLUE = Color(0xFF2A72B4)
    val CYAN = Color(0xFF00C6FC)
    val MINT = Color(0xFF90FC9C)
    val SPORT_RED = Color(0xFFE5334B)
    val AMBER = Color(0xFFFFB020)

    /** Swatches offered for Eco and Normal. */
    val CALM_CHOICES = listOf(
        MINT, CYAN, BLUE, Color(0xFF2AA8CC), Color(0xFF7EF0A8), Color(0xFFFFFFFF), Color(0xFFC0C6CC)
    ).map { it.toArgbInt() }

    /** Swatches offered for Sport. */
    val SPORT_CHOICES = listOf(
        SPORT_RED, Color(0xFFFF4D4D), Color(0xFFC62840), Color(0xFFFF6B3D), Color(0xFFFF8A1F), Color(0xFFFFFFFF)
    ).map { it.toArgbInt() }
}

fun Color.toArgbInt(): Int =
    ((alpha * 255f + 0.5f).toInt() shl 24) or ((red * 255f + 0.5f).toInt() shl 16) or
        ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()

data class ModePalette(
    val accent: Color,
    val accent2: Color,
    val bgTop: Color,
    val bgBottom: Color,
    /** Safety alert colour: amber in Eco/Normal, red only in Sport. */
    val alert: Color,
    /** Brake light glow on the road. */
    val brake: Color
)

fun modePalette(mode: DriveMode, dark: Boolean): ModePalette = if (dark) when (mode) {
    DriveMode.ECO -> ModePalette(Brand.MINT, Color(0xFF2AA8CC), Color(0xFF0B2622), Color(0xFF040F0D), Brand.AMBER, Color(0xFFD6FFDB))
    DriveMode.NORMAL -> ModePalette(Brand.CYAN, Brand.BLUE, Color(0xFF0C1F36), Color(0xFF050B15), Brand.AMBER, Color(0xFFBFEFFF))
    DriveMode.SPORT -> ModePalette(Brand.SPORT_RED, Color(0xFFB3202F), Color(0xFF2A1017), Color(0xFF0D0508), Brand.SPORT_RED, Color(0xFFFF3B3B))
} else when (mode) {
    DriveMode.ECO -> ModePalette(Color(0xFF1E9E5A), Color(0xFF1B8FB0), Color(0xFFEAF8EE), Color(0xFFCDEBD6), Color(0xFFB86E00), Color(0xFF1E9E5A))
    DriveMode.NORMAL -> ModePalette(Color(0xFF1F6FB0), Color(0xFF0097C8), Color(0xFFEAF3FB), Color(0xFFD0E2F3), Color(0xFFB86E00), Color(0xFF0097C8))
    DriveMode.SPORT -> ModePalette(Color(0xFFC62840), Color(0xFF8F1B2C), Color(0xFFFBECEE), Color(0xFFEFD0D6), Color(0xFFC62840), Color(0xFFC62840))
}

/** True when [c] reads as red (hue near 0 degrees, clearly saturated). Used by tests to enforce the "red only in Sport" rule. */
fun isRedHue(c: Color): Boolean {
    val mx = max(c.red, max(c.green, c.blue))
    val mn = min(c.red, min(c.green, c.blue))
    val d = mx - mn
    if (mx < 0.35f || d / mx < 0.45f) return false
    val h = when (mx) {
        c.red -> 60f * (((c.green - c.blue) / d) % 6f)
        c.green -> 60f * ((c.blue - c.red) / d + 2f)
        else -> 60f * ((c.red - c.green) / d + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return h <= 18f || h >= 340f
}
