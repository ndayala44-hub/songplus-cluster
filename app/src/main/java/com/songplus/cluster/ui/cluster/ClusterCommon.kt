package com.songplus.cluster.ui.cluster

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.songplus.cluster.data.AnimLevel
import com.songplus.cluster.data.AppSettings
import com.songplus.cluster.data.BlinkRate
import com.songplus.cluster.data.ClusterConfig
import com.songplus.cluster.data.FontStyle
import com.songplus.cluster.data.VehicleState
import com.songplus.cluster.ui.theme.ClusterColors
import com.songplus.cluster.media.MediaAccess
import com.songplus.cluster.media.MediaInfo
import com.songplus.cluster.vehicle.Projection

/** Scales design units (1 unit = 1/720 of the canvas height) to dp / sp. */
class Scale(private val u: Float, private val density: Density) {
    fun dp(v: Float): Dp = (v * u).dp
    fun sp(v: Float): TextUnit = with(density) { (v * u).dp.toSp() }
    fun px(v: Float): Float = with(density) { (v * u).dp.toPx() }
}

/** Everything a widget needs to draw itself. */
class WCtx(
    val state: VehicleState,
    val settings: AppSettings,
    val cfg: ClusterConfig,
    val colors: ClusterColors,
    val accent: Color,
    val scale: Scale,
    val blinkOn: Boolean,
    val projection: Projection? = null,
    /** True while the gear is R and the reverse camera is enabled. */
    val reverse: Boolean = false,
    /** False in the designer preview, so the camera is never opened there. */
    val liveCamera: Boolean = false,
    val media: MediaInfo? = null,
    val mediaAccess: MediaAccess = MediaAccess.NO_PERMISSION
) {
    fun text(size: Float, weight: FontWeight? = null, color: Color = colors.text): TextStyle {
        val fam = if (cfg.font == FontStyle.MONO) FontFamily.Monospace else FontFamily.SansSerif
        val w = weight ?: when (cfg.font) {
            FontStyle.LIGHT -> FontWeight.Light
            FontStyle.SANS -> FontWeight.Medium
            FontStyle.MONO -> FontWeight.Normal
        }
        return TextStyle(fontFamily = fam, fontWeight = w, fontSize = scale.sp(size * cfg.digitScale), color = color)
    }

    /** Fixed-size label text (not affected by digit scale). */
    fun label(size: Float, color: Color = colors.textDim, weight: FontWeight = FontWeight.Medium): TextStyle =
        TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = scale.sp(size), color = color)
}

@Composable
fun smooth(target: Float, level: AnimLevel): Float {
    val spec = when (level) {
        AnimLevel.FULL -> tween<Float>(260)
        AnimLevel.REDUCED -> tween<Float>(110)
        AnimLevel.OFF -> snap<Float>()
    }
    val v by animateFloatAsState(targetValue = target, animationSpec = spec, label = "smooth")
    return v
}

/** True during the "on" half of the blink period. Turn signals keep blinking even when animations are off (safety). */
@Composable
fun rememberBlink(rate: BlinkRate): Boolean {
    val t = rememberInfiniteTransition(label = "blink")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(rate.periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "blinkPhase"
    )
    return phase < 0.5f
}
