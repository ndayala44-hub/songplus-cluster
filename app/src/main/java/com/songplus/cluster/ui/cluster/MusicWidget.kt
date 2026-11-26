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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
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
import com.songplus.cluster.media.MediaRepository
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

    val repo = MediaRepository.current
    Column(
        Modifier.fillMaxSize().clip(shape)
            .background(c.colors.panel.copy(alpha = if (c.colors.dark) 0.55f else 0.72f))
            .border(1.dp, (if (c.colors.dark) Color.White else acc).copy(alpha = if (c.colors.dark) 0.14f else 0.30f), shape)
            .padding(horizontal = c.scale.dp(14f), vertical = c.scale.dp(10f)),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // header: title and artist on the left, artwork on the right
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (m == null) {
                    Text(
                        stringResource(if (c.mediaAccess == MediaAccess.NO_PERMISSION) R.string.music_need_access else R.string.music_none),
                        style = c.label(15f, text, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(if (c.mediaAccess == MediaAccess.NO_PERMISSION) R.string.music_need_access_hint else R.string.music_none_hint),
                        style = c.label(10f, dim), maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        m.title.ifBlank { stringResource(R.string.music_unknown_title) },
                        style = c.label(17f, text, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        m.artist.ifBlank { m.appLabel },
                        style = c.label(13f, dim), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(Modifier.padding(start = c.scale.dp(8f)).size(c.scale.dp(42f)).clip(RoundedCornerShape(c.scale.dp(8f)))) {
                val art = m?.art
                if (art != null) {
                    Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.linearGradient(listOf(mp.accent2.copy(alpha = 0.55f), acc.copy(alpha = 0.35f))))
                        val u = size.minDimension
                        drawOval(text.copy(alpha = 0.85f), Offset(u * 0.28f, u * 0.58f), Size(u * 0.22f, u * 0.17f))
                        drawRect(text.copy(alpha = 0.85f), Offset(u * 0.47f, u * 0.26f), Size(u * 0.04f, u * 0.40f))
                        drawRect(text.copy(alpha = 0.85f), Offset(u * 0.47f, u * 0.26f), Size(u * 0.22f, u * 0.05f))
                    }
                }
            }
        }

        // progress bar with elapsed / total time
        val dur = m?.durationMs ?: -1L
        val pos = m?.positionNow() ?: 0L
        val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        Column {
            Canvas(Modifier.fillMaxWidth().height(c.scale.dp(8f))) {
                val h = size.height * 0.5f
                val y = (size.height - h) / 2f
                val r = CornerRadius(h / 2f)
                drawRoundRect(c.colors.line.copy(alpha = 0.40f), Offset(0f, y), Size(size.width, h), r)
                if (m != null && dur > 0 && m.positionKnown && frac > 0f) {
                    drawRoundRect(acc, Offset(0f, y), Size(size.width * frac, h), r)
                    drawCircle(acc, size.height / 2f, Offset(size.width * frac, size.height / 2f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (m != null && m.positionKnown) fmt(pos) else "--:--", style = c.label(10f, dim))
                Text(if (dur > 0) fmt(dur) else "--:--", style = c.label(10f, dim))
            }
        }

        // source badge and transport controls
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SourceBadge(m?.packageName, m?.appLabel, acc, text, c.scale.dp(22f))
            val on = m != null && repo != null
            TransportIcon(TKind.PREV, text, on, c.scale.dp(24f)) { repo?.previous() }
            TransportIcon(if (m?.playing == true) TKind.PAUSE else TKind.PLAY, text, on, c.scale.dp(30f)) { repo?.playPause() }
            TransportIcon(TKind.NEXT, text, on, c.scale.dp(24f)) { repo?.next() }
            val sh = m?.shuffleAction
            TransportIcon(TKind.SHUFFLE, text, sh != null, c.scale.dp(24f)) { if (sh != null) repo?.sendCustom(sh) }
        }
    }
}

private enum class TKind { PREV, NEXT, PLAY, PAUSE, SHUFFLE }

@Composable
private fun TransportIcon(kind: TKind, color: Color, enabled: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Canvas(Modifier.size(size).clickable(enabled = enabled, onClick = onClick)) {
        val u = this.size.minDimension
        val col = color.copy(alpha = if (enabled) 0.95f else 0.30f)
        fun tri(x0: Float, x1: Float) {
            val p = Path().apply { moveTo(u * x0, u * 0.28f); lineTo(u * x1, u * 0.5f); lineTo(u * x0, u * 0.72f); close() }
            drawPath(p, col)
        }
        when (kind) {
            TKind.PLAY -> tri(0.30f, 0.76f)
            TKind.PAUSE -> {
                drawRoundRect(col, Offset(u * 0.28f, u * 0.22f), Size(u * 0.16f, u * 0.56f), CornerRadius(u * 0.04f))
                drawRoundRect(col, Offset(u * 0.56f, u * 0.22f), Size(u * 0.16f, u * 0.56f), CornerRadius(u * 0.04f))
            }
            TKind.NEXT -> { tri(0.24f, 0.66f); drawRect(col, Offset(u * 0.68f, u * 0.28f), Size(u * 0.08f, u * 0.44f)) }
            TKind.PREV -> {
                val p = Path().apply { moveTo(u * 0.76f, u * 0.28f); lineTo(u * 0.34f, u * 0.5f); lineTo(u * 0.76f, u * 0.72f); close() }
                drawPath(p, col); drawRect(col, Offset(u * 0.24f, u * 0.28f), Size(u * 0.08f, u * 0.44f))
            }
            TKind.SHUFFLE -> {
                val st = androidx.compose.ui.graphics.drawscope.Stroke(u * 0.07f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val a = Path().apply { moveTo(u * 0.2f, u * 0.32f); lineTo(u * 0.42f, u * 0.32f); lineTo(u * 0.58f, u * 0.68f); lineTo(u * 0.76f, u * 0.68f) }
                val b = Path().apply { moveTo(u * 0.2f, u * 0.68f); lineTo(u * 0.42f, u * 0.68f); lineTo(u * 0.58f, u * 0.32f); lineTo(u * 0.76f, u * 0.32f) }
                drawPath(a, col, style = st); drawPath(b, col, style = st)
                val h1 = Path().apply { moveTo(u * 0.74f, u * 0.56f); lineTo(u * 0.86f, u * 0.68f); lineTo(u * 0.74f, u * 0.8f); close() }
                val h2 = Path().apply { moveTo(u * 0.74f, u * 0.2f); lineTo(u * 0.86f, u * 0.32f); lineTo(u * 0.74f, u * 0.44f); close() }
                drawPath(h1, col); drawPath(h2, col)
            }
        }
    }
}

/** Source of the current session: the Spotify mark for Spotify, otherwise the player's initial. */
@Composable
private fun SourceBadge(pkg: String?, label: String?, acc: Color, text: Color, size: androidx.compose.ui.unit.Dp) {
    val spotify = pkg?.contains("spotify", ignoreCase = true) == true
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val u = this.size.minDimension
            if (spotify) {
                drawCircle(Color(0xFF1DB954))
                val st = androidx.compose.ui.graphics.drawscope.Stroke(u * 0.075f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                for ((i, w) in listOf(0.56f, 0.46f, 0.36f).withIndex()) {
                    val y = u * (0.36f + 0.15f * i)
                    drawArc(Color(0xFF0B1F12), 205f, 130f, false, Offset(u * (0.5f - w / 2f), y - w * u * 0.3f), Size(u * w, w * u * 0.6f), style = st)
                }
            } else {
                drawCircle(acc.copy(alpha = 0.22f))
                drawCircle(acc.copy(alpha = 0.7f), style = androidx.compose.ui.graphics.drawscope.Stroke(u * 0.06f))
            }
        }
        if (!spotify && !label.isNullOrBlank()) Text(label.take(1).uppercase(), style = androidx.compose.ui.text.TextStyle(color = text, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.5f).sp))
    }
}
