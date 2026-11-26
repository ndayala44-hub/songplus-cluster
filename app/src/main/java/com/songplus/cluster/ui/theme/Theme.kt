package com.songplus.cluster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.songplus.cluster.data.AccentSource
import com.songplus.cluster.data.AppSettings
import com.songplus.cluster.data.ClusterConfig
import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.data.ThemeMode
import java.util.Calendar

/** Colours used by the cluster renderer (separate from Material colours so the cluster is fully themeable). */
data class ClusterColors(
    val name: String,
    val dark: Boolean,
    val bg0: Color,
    val bg1: Color,
    val panel: Color,
    val text: Color,
    val textDim: Color,
    val line: Color,
    val ok: Color,
    val warn: Color,
    val alert: Color,
    val info: Color,
    val accent: Color
)

object Palettes {
    private fun dark(name: String, bg0: Long, bg1: Long, panel: Long) = ClusterColors(
        name, true, Color(bg0), Color(bg1), Color(panel), Color(0xFFF2F6FA), Color(0xFF8C98A6), Color(0x33FFFFFF),
        Color(0xFF90FC9C), Color(0xFFFFB020), Color(0xFFFFB020), Color(0xFF00C6FC), Color(0xFF00C6FC)
    )

    private fun light(name: String, bg0: Long, bg1: Long, panel: Long) = ClusterColors(
        name, false, Color(bg0), Color(bg1), Color(panel), Color(0xFF14202B), Color(0xFF5B6876), Color(0x33000000),
        Color(0xFF1E9E5A), Color(0xFFB86E00), Color(0xFFB86E00), Color(0xFF1F6FB0), Color(0xFF1F6FB0)
    )

    val darkSet = listOf(
        dark("Midnight", 0xFF05080C, 0xFF111A24, 0xFF0C131B),
        dark("Graphite", 0xFF0A0A0B, 0xFF1B1C1F, 0xFF131416),
        dark("Deep blue", 0xFF030A17, 0xFF0B1E3A, 0xFF08142A)
    )
    val lightSet = listOf(
        light("Cloud", 0xFFE9EEF3, 0xFFFFFFFF, 0xFFF4F7FA),
        light("Warm", 0xFFF1ECE4, 0xFFFFFCF7, 0xFFFAF6EF),
        light("Mint", 0xFFE3EFEA, 0xFFF7FFFC, 0xFFEEF8F4)
    )

    fun pick(dark: Boolean, index: Int): ClusterColors {
        val set = if (dark) darkSet else lightSet
        return set[index.coerceIn(0, set.size - 1)]
    }
}

/** Whether dark colours are in effect for the given settings and hour of day. */
fun resolveDark(s: AppSettings, systemDark: Boolean, hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Boolean =
    when (s.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.AUTO_TIME ->
            if (s.autoDarkFromHour <= s.autoDarkToHour) hour >= s.autoDarkFromHour && hour < s.autoDarkToHour
            else hour >= s.autoDarkFromHour || hour < s.autoDarkToHour
    }

/** Accent for the current drive mode. In light theme the colour is darkened for contrast. */
fun accentFor(s: AppSettings, cfg: ClusterConfig, mode: DriveMode, dark: Boolean): Color {
    val argb = if (cfg.accentSource == AccentSource.FIXED) cfg.fixedAccent else when (mode) {
        DriveMode.ECO -> s.accentEco
        DriveMode.NORMAL -> s.accentNormal
        DriveMode.SPORT -> s.accentSport
    }
    val c = Color(argb)
    return if (dark) c else Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, 1f)
}

val LocalClusterColors = staticCompositionLocalOf { Palettes.darkSet[0] }

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val cc = Palettes.pick(dark, 0)
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF00C6FC), background = cc.bg0, surface = cc.panel, onSurface = cc.text, onBackground = cc.text,
        surfaceVariant = Color(0xFF1B2733), onSurfaceVariant = cc.textDim
    ) else lightColorScheme(
        primary = Color(0xFF1F6FB0), background = cc.bg0, surface = cc.panel, onSurface = cc.text, onBackground = cc.text,
        surfaceVariant = Color(0xFFDDE5EC), onSurfaceVariant = cc.textDim
    )
    CompositionLocalProvider(LocalClusterColors provides cc) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
