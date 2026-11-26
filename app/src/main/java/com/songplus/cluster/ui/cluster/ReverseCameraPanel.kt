package com.songplus.cluster.ui.cluster

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.songplus.cluster.R
import com.songplus.cluster.camera.CamStatus
import com.songplus.cluster.camera.Camera2Feed
import com.songplus.cluster.camera.MjpegFeed
import com.songplus.cluster.data.CamSource
import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.ui.theme.Brand
import com.songplus.cluster.ui.theme.modePalette
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Square, rounded reverse-camera tile that REPLACES the car picture while the gear is R.
 * Switching is instant (no animation); the stream is opened only while the tile is on screen, so leaving
 * reverse releases the camera immediately. If there is no usable picture a clear message is shown instead
 * of a blank or frozen image.
 */
@Composable
internal fun ReverseCameraPanel(c: WCtx, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val s = c.settings
    val mode = if (c.state.mode.available) c.state.mode.value else DriveMode.NORMAL
    val mp = modePalette(mode, c.colors.dark)
    var status by remember { mutableStateOf(if (c.liveCamera) CamStatus.STARTING else CamStatus.PREVIEW_ONLY) }
    var detail by remember { mutableStateOf<String?>(null) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    val shape = RoundedCornerShape(c.scale.dp(26f))

    Box(
        modifier.aspectRatio(1f).clip(shape).background(Color.Black).border(c.scale.dp(3f), mp.accent.copy(alpha = 0.9f), shape)
    ) {
        if (c.liveCamera) {
            val hasPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            when (s.camSource) {
                CamSource.MJPEG_URL -> {
                    if (s.camUrl.isBlank()) {
                        LaunchedEffect(Unit) { status = CamStatus.NO_URL }
                    } else {
                        val feed = remember(s.camUrl) { MjpegFeed(s.camUrl, { frame = it }, { st, m -> status = st; detail = m }) }
                        DisposableEffect(feed) { feed.start(); onDispose { feed.close() } }
                        LaunchedEffect(feed) { watchdog({ feed.lastFrameMs }, { status }, { status = it }) }
                        frame?.let {
                            Image(
                                it.asImageBitmap(), null, Modifier.fillMaxSize().scale(if (s.camMirror) -1f else 1f, 1f),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                else -> {
                    if (!hasPermission) {
                        LaunchedEffect(Unit) { status = CamStatus.NO_PERMISSION }
                    } else {
                        val feed = remember(s.camId, s.camSource) { Camera2Feed(ctx) { st, m -> status = st; detail = m } }
                        DisposableEffect(feed) { onDispose { feed.close() } }
                        LaunchedEffect(feed) { watchdog({ feed.lastFrameMs }, { status }, { status = it }) }
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                TextureView(context).apply {
                                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                            val id = feed.pick(if (s.camSource == CamSource.CAMERA2_ID) s.camId else "")
                                            if (id == null) { status = CamStatus.NO_CAMERA } else feed.start(id, st)
                                        }
                                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) { applyCrop(this@apply, feed.bufferAspect, s.camMirror) }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        if (status == CamStatus.LIVE && s.camGuides) Guidelines(c, mp.accent, mp.alert)
        if (status == CamStatus.LIVE) {
            Text(
                "R", style = c.label(18f, Color.White, FontWeight.Bold),
                modifier = Modifier.align(Alignment.TopStart).padding(c.scale.dp(12f))
                    .background(mp.accent.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(horizontal = c.scale.dp(10f), vertical = c.scale.dp(2f))
            )
        } else {
            Fallback(c, status, detail, mp.accent)
        }
    }
}

/** Centre-crops the camera buffer into the square tile without distortion. */
private fun applyCrop(tv: TextureView, aspect: Float, mirror: Boolean) {
    val w = tv.width.toFloat(); val h = tv.height.toFloat()
    if (w <= 0f || h <= 0f) return
    val m = Matrix()
    val sx = if (aspect >= 1f) aspect else 1f
    val sy = if (aspect >= 1f) 1f else 1f / aspect
    m.postScale(sx * (if (mirror) -1f else 1f), sy, w / 2f, h / 2f)
    tv.setTransform(m)
}

/** Marks the picture as lost when frames stop arriving, and live again when they resume. */
private suspend fun watchdog(last: () -> Long, status: () -> CamStatus, set: (CamStatus) -> Unit) {
    while (true) {
        delay(500)
        val l = last()
        val age = SystemClock.elapsedRealtime() - l
        if (status() == CamStatus.LIVE && l > 0 && age > 2000) set(CamStatus.LOST)
        else if (status() == CamStatus.LOST && l > 0 && age < 1000) set(CamStatus.LIVE)
    }
}

@Composable
private fun Fallback(c: WCtx, status: CamStatus, detail: String?, accent: Color) {
    val (title, body) = when (status) {
        CamStatus.STARTING -> stringResource(R.string.cam_starting) to ""
        CamStatus.NO_PERMISSION -> stringResource(R.string.cam_unavailable) to stringResource(R.string.cam_no_permission)
        CamStatus.NO_CAMERA -> stringResource(R.string.cam_unavailable) to stringResource(R.string.cam_no_camera)
        CamStatus.NO_URL -> stringResource(R.string.cam_unavailable) to stringResource(R.string.cam_no_url)
        CamStatus.PREVIEW_ONLY -> stringResource(R.string.cam_preview) to stringResource(R.string.cam_preview_body)
        CamStatus.LOST -> stringResource(R.string.cam_lost) to stringResource(R.string.cam_lost_body)
        else -> stringResource(R.string.cam_unavailable) to (detail ?: stringResource(R.string.cam_error))
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0D12)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(c.scale.dp(22f))) {
            Text(title, style = c.label(26f, Color.White, FontWeight.SemiBold), textAlign = TextAlign.Center)
            if (body.isNotEmpty()) Text(body, style = c.label(16f, Color(0xFFB6C2CF)), textAlign = TextAlign.Center, modifier = Modifier.padding(top = c.scale.dp(8f)))
            Text("R", style = c.label(46f, accent, FontWeight.Bold), modifier = Modifier.padding(top = c.scale.dp(10f)))
        }
    }
}

/**
 * Parking guidelines drawn over the picture. They are straight, fixed guides: this app has no verified source for
 * the steering angle, so they do not bend with the wheel. Colours follow the drive mode (amber/red only for the near zone).
 */
@Composable
private fun Guidelines(c: WCtx, accent: Color, near: Color) {
    val steer = if (c.state.steeringDeg.available) c.state.steeringDeg.value else 0f
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val topY = h * 0.48f
        val shift = (steer / 540f).coerceIn(-1f, 1f) * w * 0.22f
        val sw = size.minDimension * 0.014f
        // y fractions of the band edges, near to far; colours: near zone (amber, red only in Sport), mid (accent), far (mint)
        val ys = listOf(1.0f, 0.82f, 0.64f, topY / h)
        val cols = listOf(near, accent, Brand.MINT)
        fun railX(left: Boolean, yf: Float): Float {
            val k = ((1f - yf) / (1f - topY / h)).coerceIn(0f, 1f)          // 0 at the bottom, 1 at the far end
            val base = if (left) w * (0.10f + 0.20f * k) else w * (0.90f - 0.20f * k)
            return base + shift * k * k
        }
        for (i in 0 until 3) {
            val y0 = h * ys[i]; val y1 = h * ys[i + 1]
            val col = cols[i].copy(alpha = 0.95f)
            drawLine(col, Offset(railX(true, ys[i]), y0), Offset(railX(true, ys[i + 1]), y1), sw, StrokeCap.Round)
            drawLine(col, Offset(railX(false, ys[i]), y0), Offset(railX(false, ys[i + 1]), y1), sw, StrokeCap.Round)
            if (i > 0) drawLine(col, Offset(railX(true, ys[i]), y0), Offset(railX(false, ys[i]), y0), sw * 0.7f, StrokeCap.Round)
        }
        drawLine(cols[2].copy(alpha = 0.95f), Offset(railX(true, ys[3]), h * ys[3]), Offset(railX(false, ys[3]), h * ys[3]), sw * 0.7f, StrokeCap.Round)
    }
}
