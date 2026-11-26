package com.songplus.cluster.ui.cluster

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songplus.cluster.R
import com.songplus.cluster.data.AppSettings
import com.songplus.cluster.data.BgStyle
import com.songplus.cluster.data.ClusterConfig
import com.songplus.cluster.data.ConnectionStatus
import com.songplus.cluster.data.EffectiveSource
import com.songplus.cluster.data.VehicleState
import com.songplus.cluster.data.WidgetId
import com.songplus.cluster.ui.theme.Palettes
import com.songplus.cluster.ui.theme.accentFor
import com.songplus.cluster.media.MediaAccess
import com.songplus.cluster.media.MediaInfo
import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.data.Gear
import com.songplus.cluster.ui.theme.modePalette
import com.songplus.cluster.vehicle.Projection
import kotlin.math.min

/**
 * Renders a [ClusterConfig] with live [VehicleState]. Used by the full-screen display and by the designer preview.
 * [overlay] is drawn above the widgets and receives the canvas size in dp (the designer uses it for drag handles).
 */
@Composable
fun ClusterCanvas(
    state: VehicleState,
    cfg: ClusterConfig,
    settings: AppSettings,
    status: ConnectionStatus,
    dark: Boolean,
    modifier: Modifier = Modifier,
    showStatusBadge: Boolean = true,
    projection: Projection? = null,
    media: MediaInfo? = null,
    mediaAccess: MediaAccess = MediaAccess.NO_PERMISSION,
    liveCamera: Boolean = false,
    overlay: @Composable BoxScope.(Dp, Dp, Scale) -> Unit = { _, _, _ -> }
) {
    val colors = Palettes.pick(dark, if (dark) cfg.darkPalette else cfg.lightPalette)
    val accent = accentFor(settings, cfg, state.mode.value, dark)
    val blink = rememberBlink(settings.blinkRate)
    val density = LocalDensity.current
    val mode = if (state.mode.available) state.mode.value else DriveMode.NORMAL
    val mp = modePalette(mode, dark)
    // Backgrounds follow the drive mode (blend with the chosen palette); red only appears in Sport.
    val bg0 = mixColor(colors.bg0, mp.bgBottom, 0.6f)
    val bg1 = mixColor(colors.bg1, mp.bgTop, 0.6f)
    val reverse = settings.reverseCamera && state.gear.available && state.gear.value == Gear.R
    BoxWithConstraints(modifier.background(brushFor(cfg, bg0, bg1))) {
        val u = min(maxHeight.value / 720f, maxWidth.value / 1920f)
        val scale = Scale(u, density)
        val ctx = WCtx(
            state, settings, cfg, colors.copy(accent = accent, alert = mp.alert), accent, scale, blink, projection,
            reverse = reverse, liveCamera = liveCamera, media = media, mediaAccess = mediaAccess
        )
        if (cfg.background == BgStyle.GRID) {
            Canvas(Modifier.fillMaxSize()) {
                val step = scale.px(60f)
                var x = 0f
                while (x < size.width) { drawLine(colors.line.copy(alpha = 0.12f), Offset(x, 0f), Offset(x, size.height)); x += step }
                var y = 0f
                while (y < size.height) { drawLine(colors.line.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y)); y += step }
            }
        }
        for (id in WidgetId.values()) {
            val sp = cfg.spec(id)
            if (!sp.visible) continue
            if (id == WidgetId.GEAR && !settings.showGear) continue
            if (id == WidgetId.CAR && reverse) {
                // the camera tile is square and a little larger than the car picture, centred on the car's spot
                val side = min(maxHeight.value * 0.62f, maxWidth.value * 0.28f).dp
                val cxp = maxWidth * (sp.x + sp.w / 2f)
                val cyp = maxHeight * (sp.y + sp.h / 2f)
                val topP = (cyp - side / 2f).coerceIn(maxHeight * 0.14f, maxHeight * 0.82f - side)
                ClusterWidget(id, ctx, Modifier.offset(cxp - side / 2f, topP).size(side, side))
            } else ClusterWidget(
                id, ctx,
                Modifier.offset(maxWidth * sp.x, maxHeight * sp.y).size(maxWidth * sp.w, maxHeight * sp.h)
            )
        }
        val haz = state.hazard.available && state.hazard.value
        if (haz && settings.showHazardBanner && blink) {
            Box(Modifier.fillMaxSize().border(c4(scale), colors.warn.copy(alpha = 0.9f)))
        }
        if (showStatusBadge) {
            SourceBadge(status.effective, state, ctx, Modifier.align(Alignment.TopStart).padding(scale.dp(12f)))
        }
        if (settings.debugOverlay) {
            val txt = "src=${status.effective}  ${String.format(java.util.Locale.US, "%.1f", status.updatesPerSecond)} Hz  " +
                status.devices.joinToString(" ") { it.device.take(3) + ":" + it.status.name.take(2) }
            Text(txt, style = ctx.label(14f, colors.warn), modifier = Modifier.align(Alignment.BottomStart).padding(scale.dp(8f)))
        }
        overlay(maxWidth, maxHeight, scale)
    }
}

private fun mixColor(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color, t: Float) =
    androidx.compose.ui.graphics.Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

private fun c4(s: Scale) = s.dp(5f)

private fun brushFor(cfg: ClusterConfig, c0: androidx.compose.ui.graphics.Color, c1: androidx.compose.ui.graphics.Color): Brush =
    when (cfg.background) {
        BgStyle.SOLID -> Brush.verticalGradient(listOf(c0, c0))
        BgStyle.GRADIENT, BgStyle.GRID -> Brush.radialGradient(listOf(c1, c0))
    }

/** Always visible: tells the driver whether the numbers on screen are live, simulated, or missing. */
@Composable
fun SourceBadge(effective: EffectiveSource, state: VehicleState, c: WCtx, modifier: Modifier = Modifier) {
    val (label, color) = when (effective) {
        EffectiveSource.REAL -> stringResource(R.string.badge_real) to c.colors.ok
        EffectiveSource.SIMULATION -> stringResource(R.string.badge_sim) to c.colors.warn
        EffectiveSource.NONE -> stringResource(R.string.badge_none) to c.colors.alert
    }
    val partial = effective == EffectiveSource.REAL && !state.coreComplete()
    Text(
        label + if (partial) " · " + stringResource(R.string.badge_partial) else "",
        style = c.label(15f, color, FontWeight.Bold),
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = c.scale.dp(12f), vertical = c.scale.dp(4f))
    )
}

/** Some fields are unavailable on a real connection when the head unit denies individual devices. */
private fun VehicleState.coreComplete(): Boolean =
    socPct.available && gear.available && lowBeam.available && leftSignal.available

/** Start-up animation: logo fades in, holds, fades out. [onDone] is called when it finishes. */
@Composable
fun IntroOverlay(accent: androidx.compose.ui.graphics.Color, dark: Boolean, onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, tween(600))
        kotlinx.coroutines.delay(700)
        a.animateTo(0f, tween(500))
        onDone()
    }
    val bg = if (dark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
    Box(Modifier.fillMaxSize().background(bg.copy(alpha = 0.92f * (0.4f + 0.6f * a.value))), contentAlignment = Alignment.Center) {
        Text(
            "SONG PLUS EV",
            color = accent.copy(alpha = a.value),
            style = androidx.compose.ui.text.TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = 12.sp)
        )
    }
}
