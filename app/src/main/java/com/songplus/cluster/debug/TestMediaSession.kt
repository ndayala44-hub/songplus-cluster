package com.songplus.cluster.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState

/**
 * TEST FIXTURE, used only by debug builds (the call site checks FLAG_DEBUGGABLE). It publishes a real Android
 * [MediaSession], exactly like a music app would, so the real [com.songplus.cluster.media.MediaRepository] path can be
 * verified on an emulator that has no music app. The widget itself never uses this class or any static data.
 */
object TestMediaSession {
    private var session: MediaSession? = null

    fun start(ctx: Context) {
        if (session != null) return
        val s = MediaSession(ctx, "SongPlusTestFixture")
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawRect(0f, 0f, 300f, 300f, Paint().apply {
            shader = LinearGradient(0f, 0f, 300f, 300f, 0xFF2A72B4.toInt(), 0xFF90FC9C.toInt(), Shader.TileMode.CLAMP)
        })
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Kigali Nights")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Test Fixture Artist")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Debug Album")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 215_000L)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
                .build()
        )
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 83_000L, 1f)
                .build()
        )
        s.isActive = true
        session = s
    }
}
