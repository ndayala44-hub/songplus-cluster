package com.songplus.cluster.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

enum class CamStatus { STARTING, LIVE, NO_PERMISSION, NO_CAMERA, ERROR, LOST, NO_URL, PREVIEW_ONLY }

/**
 * Reverse-camera source 1: the standard Android Camera2 API.
 *
 * This works when the head unit (or an attached USB/UVC camera) exposes the camera to normal apps. BYD's own
 * factory reverse and 360-degree cameras are NOT exposed this way (see docs/REVERSE_CAMERA.md), so on a stock
 * DiLink unit this typically reports "no camera" and the panel shows its fallback message.
 */
internal class Camera2Feed(private val ctx: Context, private val onState: (CamStatus, String?) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("reverse-cam").also { it.start() }
    private val handler = Handler(thread.looper)

    /** elapsedRealtime of the last delivered frame, 0 if none yet. */
    @Volatile var lastFrameMs = 0L
        private set
    @Volatile var bufferAspect = 4f / 3f
        private set
    @Volatile private var closed = false
    @Volatile private var live = false
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    private fun post(s: CamStatus, m: String?) { mainHandler.post { if (!closed) onState(s, m) } }

    /** [pref] = explicit camera id, or blank for automatic choice (external, then back-facing, then any non-front). */
    fun pick(pref: String): String? = try {
        val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = mgr.cameraIdList.toList()
        fun facing(id: String) = mgr.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
        if (pref.isNotBlank()) ids.firstOrNull { it == pref.trim() }
        else ids.firstOrNull { facing(it) == CameraCharacteristics.LENS_FACING_EXTERNAL }
            ?: ids.firstOrNull { facing(it) == CameraCharacteristics.LENS_FACING_BACK }
            ?: ids.firstOrNull { facing(it) != CameraCharacteristics.LENS_FACING_FRONT }
            ?: ids.firstOrNull()
    } catch (t: Throwable) { null }

    @SuppressLint("MissingPermission")
    fun start(id: String, tex: SurfaceTexture) {
        try {
            val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val map = mgr.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = choose(map?.getOutputSizes(SurfaceTexture::class.java))
            tex.setDefaultBufferSize(size.width, size.height)
            bufferAspect = size.width.toFloat() / size.height
            val surface = Surface(tex)
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    if (closed) { d.close(); return }
                    device = d
                    startSession(d, surface)
                }
                override fun onDisconnected(d: CameraDevice) { d.close(); post(CamStatus.LOST, "disconnected") }
                override fun onError(d: CameraDevice, error: Int) { d.close(); post(CamStatus.ERROR, "error $error") }
            }, handler)
        } catch (se: SecurityException) {
            post(CamStatus.NO_PERMISSION, null)
        } catch (t: Throwable) {
            post(CamStatus.ERROR, t.message)
        }
    }

    private fun choose(sizes: Array<Size>?): Size {
        if (sizes == null || sizes.isEmpty()) return Size(640, 480)
        val ok = sizes.filter { it.width <= 1280 && it.height <= 960 }.ifEmpty { sizes.toList() }
        val fourThree = ok.filter { kotlin.math.abs(it.width * 3 - it.height * 4) < 8 }
        return (fourThree.ifEmpty { ok }).maxByOrNull { it.width * it.height } ?: ok.first()
    }

    @Suppress("DEPRECATION")
    private fun startSession(d: CameraDevice, surface: Surface) {
        try {
            d.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (closed) return
                    session = s
                    try {
                        val req = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }.build()
                        s.setRepeatingRequest(req, object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                lastFrameMs = SystemClock.elapsedRealtime()
                                if (!live) { live = true; post(CamStatus.LIVE, null) }
                            }
                        }, handler)
                    } catch (t: Throwable) { post(CamStatus.ERROR, t.message) }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { post(CamStatus.ERROR, "configure failed") }
            }, handler)
        } catch (t: Throwable) { post(CamStatus.ERROR, t.message) }
    }

    fun close() {
        closed = true
        try { session?.close() } catch (_: Throwable) {}
        try { device?.close() } catch (_: Throwable) {}
        thread.quitSafely()
    }
}

/**
 * Reverse-camera source 2: an MJPEG-over-HTTP stream (aftermarket IP cameras, a camera box, a phone acting as a
 * camera). Reconnects automatically; the panel treats a stalled stream as "signal lost", never as a frozen picture.
 */
internal class MjpegFeed(private val url: String, private val onFrame: (Bitmap) -> Unit, private val onState: (CamStatus, String?) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var running = true
    @Volatile var lastFrameMs = 0L
        private set
    private val worker = Thread({ loop() }, "reverse-mjpeg").also { it.isDaemon = true }

    fun start() = worker.start()
    fun close() { running = false; worker.interrupt() }

    private fun post(s: CamStatus, m: String?) { mainHandler.post { if (running) onState(s, m) } }

    private fun loop() {
        while (running) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 3000; readTimeout = 3000 }
                val input = BufferedInputStream(conn.inputStream, 1 shl 16)
                val buf = ByteArray(1 shl 20)
                var len = 0
                var first = true
                while (running) {
                    val n = input.read(buf, len, buf.size - len)
                    if (n < 0) break
                    len += n
                    while (true) {
                        val soi = find(buf, 0xFF, 0xD8, 0, len)
                        if (soi < 0) { len = 0; break }
                        val eoi = find(buf, 0xFF, 0xD9, soi + 2, len)
                        if (eoi < 0) {
                            if (soi > 0) { System.arraycopy(buf, soi, buf, 0, len - soi); len -= soi }
                            if (len >= buf.size) len = 0
                            break
                        }
                        val end = eoi + 2
                        val bmp = BitmapFactory.decodeByteArray(buf, soi, end - soi)
                        System.arraycopy(buf, end, buf, 0, len - end); len -= end
                        if (bmp != null) {
                            lastFrameMs = SystemClock.elapsedRealtime()
                            mainHandler.post { if (running) onFrame(bmp) }
                            if (first) { first = false; post(CamStatus.LIVE, null) }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (running) post(CamStatus.ERROR, t.message)
            } finally {
                try { conn?.disconnect() } catch (_: Throwable) {}
            }
            if (running) try { Thread.sleep(1000) } catch (_: InterruptedException) {}
        }
    }

    private fun find(b: ByteArray, a: Int, c: Int, from: Int, to: Int): Int {
        var i = from
        while (i < to - 1) {
            if ((b[i].toInt() and 0xFF) == a && (b[i + 1].toInt() and 0xFF) == c) return i
            i++
        }
        return -1
    }
}
