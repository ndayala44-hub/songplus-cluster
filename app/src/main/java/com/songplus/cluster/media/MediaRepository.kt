package com.songplus.cluster.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

enum class MediaAccess { NO_PERMISSION, NO_SESSION, ACTIVE }

/** What the music widget shows. Built only from a real Android media session. */
data class MediaInfo(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    /** Track length, or <= 0 when the player does not report it. */
    val durationMs: Long,
    val playing: Boolean,
    val buffering: Boolean,
    val positionKnown: Boolean,
    /** Position reported by the player at [baseElapsedMs] (SystemClock.elapsedRealtime). */
    val basePositionMs: Long,
    val baseElapsedMs: Long,
    val speed: Float,
    /** Custom action the player offers for toggling shuffle, if any. */
    val shuffleAction: String? = null
) {
    /** Current position, extrapolated from the last report while playing. */
    fun positionNow(): Long {
        var p = basePositionMs
        if (playing) p += ((SystemClock.elapsedRealtime() - baseElapsedMs) * speed).toLong()
        if (durationMs > 0) p = p.coerceAtMost(durationMs)
        return p.coerceAtLeast(0L)
    }
}

/**
 * Follows the active media session of any compatible player (Spotify, YouTube Music, local players, ...)
 * through [MediaSessionManager] and [MediaController]. It does not talk to any particular app.
 *
 * Needs notification-listener access (granted by the user in system settings). Without it, [access] is
 * [MediaAccess.NO_PERMISSION] and nothing is shown, never made-up data.
 */
class MediaRepository(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val component = ComponentName(ctx, MediaListenerService::class.java)

    private val _info = MutableStateFlow<MediaInfo?>(null)
    val info: StateFlow<MediaInfo?> = _info.asStateFlow()
    private val _access = MutableStateFlow(MediaAccess.NO_PERMISSION)
    val access: StateFlow<MediaAccess> = _access.asStateFlow()

    private var controller: MediaController? = null
    private var registered = false
    private var artKey: String? = null
    private var artBitmap: Bitmap? = null

    init { current = this }

    /** Transport commands go to the session being shown, so they work with whichever player is active. */
    fun playPause() = main.post {
        val c = controller ?: return@post
        if (_info.value?.playing == true) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() { main.post { controller?.transportControls?.skipToNext() } }
    fun previous() { main.post { controller?.transportControls?.skipToPrevious() } }
    fun sendCustom(action: String) { main.post { controller?.transportControls?.sendCustomAction(action, null) } }

    private fun labelFor(pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg.substringAfterLast('.') }

    companion object {
        @Volatile var current: MediaRepository? = null
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list ?: emptyList()) }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { refresh() }
    }

    fun hasAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Call when the app comes to the foreground: picks up a permission granted in the meantime. */
    fun refresh() { main.post { refreshNow() } }

    private fun manager() = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private fun refreshNow() {
        if (!hasAccess()) {
            detach()
            _access.value = MediaAccess.NO_PERMISSION
            _info.value = null
            return
        }
        try {
            val m = manager()
            if (!registered) {
                m.addOnActiveSessionsChangedListener(sessionsListener, component, main)
                registered = true
            }
            onSessions(m.getActiveSessions(component))
        } catch (se: SecurityException) {
            registered = false
            detach()
            _access.value = MediaAccess.NO_PERMISSION
            _info.value = null
        } catch (t: Throwable) {
            _access.value = MediaAccess.NO_SESSION
            _info.value = null
        }
    }

    private fun detach() {
        controller?.unregisterCallback(callback)
        controller = null
        if (registered) {
            try { manager().removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Throwable) {}
            registered = false
        }
    }

    private fun onSessions(list: List<MediaController>) {
        // Prefer a session that is playing; otherwise the most recently active one that has metadata.
        val pick = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata != null }
            ?: list.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = pick
            artKey = null
            artBitmap = null
            pick?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        val ps = c?.playbackState
        if (c == null || (md == null && (ps == null || ps.state == PlaybackState.STATE_NONE || ps.state == PlaybackState.STATE_STOPPED))) {
            _access.value = MediaAccess.NO_SESSION
            _info.value = null
            return
        }
        val title = md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        val art = bitmapFrom(md)
        val state = ps?.state ?: PlaybackState.STATE_NONE
        val playing = state == PlaybackState.STATE_PLAYING
        val rawPos = ps?.position ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN
        _access.value = MediaAccess.ACTIVE
        _info.value = MediaInfo(
            packageName = c.packageName, appLabel = labelFor(c.packageName),
            title = title, artist = artist, album = album, art = art, durationMs = duration,
            playing = playing, buffering = state == PlaybackState.STATE_BUFFERING,
            positionKnown = rawPos != PlaybackState.PLAYBACK_POSITION_UNKNOWN,
            basePositionMs = if (rawPos < 0) 0L else rawPos,
            baseElapsedMs = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            shuffleAction = ps?.customActions?.firstOrNull { it.action.contains("shuffle", ignoreCase = true) }?.action
        )
    }

    /** Album art from the bitmap keys, else from an art URI (loaded off the main thread). */
    private fun bitmapFrom(md: MediaMetadata?): Bitmap? {
        if (md == null) return null
        val direct = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (direct != null) return direct
        val uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: return null
        if (uri == artKey) return artBitmap
        artKey = uri
        artBitmap = null
        val owner = controller?.sessionToken
        io.execute {
            val bmp = loadUri(uri)
            main.post {
                if (artKey == uri && controller?.sessionToken == owner) {
                    artBitmap = bmp
                    publish()
                }
            }
        }
        return null
    }

    private fun loadUri(uri: String): Bitmap? = try {
        val u = Uri.parse(uri)
        if (u.scheme == "content" || u.scheme == "file" || u.scheme == "android.resource") {
            ctx.contentResolver.openInputStream(u)?.use { ins ->
                val bytes = ins.readBytes()
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var sample = 1
                while (o.outWidth / sample > 512) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } else null
    } catch (_: Throwable) { null }
}
