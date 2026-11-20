package com.dcpi.jraudio

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.media.audiofx.Visualizer
import org.json.JSONArray

class JRPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var visualizer: Visualizer? = null
    private var exoPlayer: ExoPlayer? = null

    fun fftData(): String {
        val p = exoPlayer ?: return "[]"
        return try {
            if (visualizer == null || visualizer?.enabled != true) {
                visualizer?.release()
                visualizer = Visualizer(p.audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    enabled = true
                }
            }
            val bytes = ByteArray(visualizer!!.captureSize)
            visualizer!!.getFft(bytes)
            val out = JSONArray()
            for (i in 2 until bytes.size step 2) out.put(kotlin.math.abs(bytes[i].toInt()))
            out.toString()
        } catch (_: Exception) { "[]" }
    }

    companion object { var instance: JRPlaybackService? = null }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val player = ExoPlayer.Builder(this).build()
        exoPlayer = player
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        visualizer?.release()
        visualizer = null
        instance = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}