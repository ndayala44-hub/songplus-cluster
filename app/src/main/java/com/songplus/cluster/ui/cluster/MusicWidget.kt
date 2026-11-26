package com.songplus.cluster.ui.cluster

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.songplus.cluster.R
import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.media.MediaAccess
import com.songplus.cluster.ui.theme.modePalette
import kotlinx.coroutines.delay

private fun fmt(ms: Long): String {
    val t = (ms / 1000L).coerceAtLeast(0L)
    return String.format(java.util.Locale.US, "%d:%02d", t / 60, t % 60)
}

/**
 * Now-playing card built from the active Android media session (any compatible player, e.g. Spotify).
 * Shows artwork (or a clean placeholder), title, artist, progress, elapsed / total time and play/pause state.
 * When no session is active, or access has not been granted, it says so instead of showing made-up data.
 */
@Composable
internal fun MusicWidget(c: WCtx) {
    val m = c.media
    val mode = if (c.state.mode.available) c.state.mode.value else DriveMode.NORMAL
    val mp = modePalette(mode, c.colors.dark)
    val acc = mp.accent
    val text = c.colors.text
    val dim = c.colors.textDim
    val shape = RoundedCornerShape(c.scale.dp(16f))

    // tick while playing so the progress bar and time advance smoothly between player updates
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(m?.playing, m?.title) {
        while (m != null && m.playing) { tick = System.nanoTime(); delay(400) }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    Row(
        Modifier.fillMaxSize().clip(shape)
            .background(c.colors.panel.copy(alpha = if (c.colors.dark) 0.62f else 0.78f))
            .border(1.dp, acc.copy(alpha = 0.30f), shape)
            .padding(c.scale.dp(8f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // artwork or placeholder
        Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(c.scale.dp(10f)))) {
            val art = m?.art
            if (art != null) {
                Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.linearGradient(listOf(mp.accent2.copy(alpha = 0.55f), acc.copy(alpha = 0.35f))))
                    // simple music-note glyph
                    val u = size.minDimension
                    val p = Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(Offset(u * 0.30f, u * 0.58f), Size(u * 0.20f, u * 0.16f)))
                    }
                    drawPath(p, text.copy(alpha = 0.85f))
                    drawRect(text.copy(alpha = 0.85f), Offset(u * 0.47f, u * 0.28f), Size(u * 0.035f, u * 0.38f))
                    drawRect(text.copy(alpha = 0.85f), Offset(u * 0.47f, u * 0.28f), Size(u * 0.20f, u * 0.045f))
                }
            }
            if (m != null) {
                // play / pause badge
                Canvas(Modifier.align(Alignment.BottomEnd).padding(c.scale.dp(3f)).size(c.scale.dp(24f))) {
                    drawCircle(Color.Black.copy(alpha = 0.55f))
                    val u = size.minDimension
                    if (m.playing) {
                        drawRoundRect(Color.White, Offset(u * 0.32f, u * 0.28f), Size(u * 0.12f, u * 0.44f), CornerRadius(u * 0.03f))
                        drawRoundRect(Color.White, Offset(u * 0.56f, u * 0.28f), Size(u * 0.12f, u * 0.44f), CornerRadius(u * 0.03f))
                    } else {
                        val tri = Path().apply { moveTo(u * 0.38f, u * 0.28f); lineTo(u * 0.72f, u * 0.5f); lineTo(u * 0.38f, u * 0.72f); close() }
                        drawPath(tri, Color.White)
                    }
                }
            }
        }
        Column(Modifier.padding(start = c.scale.dp(12f)).fillMaxHeight().weight(1f), verticalArrangement = Arrangement.Center) {
            if (m == null) {
                Text(
                    stringResource(if (c.mediaAccess == MediaAccess.NO_PERMISSION) R.string.music_need_access else R.string.music_none),
                    style = c.label(15f, text, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(if (c.mediaAccess == MediaAccess.NO_PERMISSION) R.string.music_need_access_hint else R.string.music_none_hint),
                    style = c.label(11f, dim), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    m.title.ifBlank { stringResource(R.string.music_unknown_title) },
                    style = c.label(17f, text, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    m.artist.ifBlank { m.appLabel },
                    style = c.label(13f, dim), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val dur = m.durationMs
                val pos = m.positionNow()
                val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                Canvas(Modifier.padding(top = c.scale.dp(7f)).fillMaxWidth().height(c.scale.dp(5f))) {
                    val r = CornerRadius(size.height / 2f)
                    drawRoundRect(c.colors.line.copy(alpha = 0.35f), Offset.Zero, size, r)
                    if (dur > 0 && m.positionKnown && frac > 0f) drawRoundRect(acc, Offset.Zero, Size(size.width * frac, size.height), r)
                }
                Row(Modifier.fillMaxWidth().padding(top = c.scale.dp(3f)), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (m.positionKnown) fmt(pos) else "--:--", style = c.label(11f, dim))
                    Text(if (dur > 0) fmt(dur) else "--:--", style = c.label(11f, dim))
                }
            }
        }
    }
}
