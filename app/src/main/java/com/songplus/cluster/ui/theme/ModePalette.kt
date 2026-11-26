package com.songplus.cluster.ui.theme

import androidx.compose.ui.graphics.Color
import com.songplus.cluster.data.DriveMode
import kotlin.math.max
import kotlin.math.min

/**
 * The brand palette (taken from the supplied colour reference) and the per-drive-mode colour system.
 *
 * Eco is green/turquoise, Normal is cyan/electric blue, Sport is magenta/violet. Red (coral) is never a mode accent:
 * it appears only for low battery, warnings and critical alerts.
 */
object Brand {
    val BLUE = Color(0xFF2A72B4)
    val CYAN = Color(0xFF00C6FC)
    val MINT = Color(0xFF90FC9C)
    val VIOLET = Color(0xFF9B6BFF)
    val MAGENTA = Color(0xFFE23DD8)
    /** Sport accent: a performance magenta-violet. */
    val SPORT = Color(0xFFD048F0)
    /** Coral red: low battery and genuine warnings only. */
    val CORAL = Color(0xFFFF5A5F)
    val AMBER = Color(0xFFFFB020)

    /** Swatches offered for Eco and Normal. */
    val CALM_CHOICES = listOf(
        MINT, CYAN, BLUE, Color(0xFF2AA8CC), Color(0xFF7EF0A8), Color(0xFFFFFFFF), Color(0xFFC0C6CC)
    ).map { it.toArgbInt() }

    /** Swatches offered for Sport. */
    val SPORT_CHOICES = listOf(
        SPORT, MAGENTA, VIOLET, Color(0xFFFF4FB8), Color(0xFF7A5CFF), Color(0xFFFFFFFF)
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
    /** Warning colour (coral red). Used for real warnings and critical alerts only. */
    val alert: Color,
    /** Brake light glow on the road. */
    val brake: Color,
    /** Neon road boundary colour. */
    val edge: Color
)

fun modePalette(mode: DriveMode, dark: Boolean): ModePalette = if (dark) when (mode) {
    DriveMode.ECO -> ModePalette(Brand.MINT, Color(0xFF2AA8CC), Color(0xFF0A3434), Color(0xFF050F14), Brand.CORAL, Color(0xFFFF6B6B), Brand.VIOLET)
    DriveMode.NORMAL -> ModePalette(Brand.CYAN, Brand.BLUE, Color(0xFF0B3550), Color(0xFF060C1C), Brand.CORAL, Color(0xFFFF6B6B), Brand.MAGENTA)
    DriveMode.SPORT -> ModePalette(Brand.SPORT, Brand.VIOLET, Color(0xFF2A1148), Color(0xFF0A0614), Brand.CORAL, Color(0xFFFF6B6B), Brand.CYAN)
} else when (mode) {
    DriveMode.ECO -> ModePalette(Color(0xFF1E9E5A), Color(0xFF1B8FB0), Color(0xFFEAF8EE), Color(0xFFCDEBD6), Color(0xFFD8323C), Color(0xFFD8323C), Color(0xFF7A57E0))
    DriveMode.NORMAL -> ModePalette(Color(0xFF1F6FB0), Color(0xFF0097C8), Color(0xFFEAF3FB), Color(0xFFD0E2F3), Color(0xFFD8323C), Color(0xFFD8323C), Color(0xFFC026B8))
    DriveMode.SPORT -> ModePalette(Color(0xFF9B2FC0), Color(0xFF6B4BD8), Color(0xFFF4EBFB), Color(0xFFE2D2F0), Color(0xFFD8323C), Color(0xFFD8323C), Color(0xFF0097C8))
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
