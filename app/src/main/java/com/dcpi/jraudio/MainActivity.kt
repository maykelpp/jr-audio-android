package com.dcpi.jraudio

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var player: ExoPlayer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(AndroidBridge(), "JRAndroid")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    inner class AndroidBridge {
        @JavascriptInterface fun platform(): String = "android-kotlin"
        @JavascriptInterface fun play(url: String, title: String, artist: String) {
            runOnUiThread {
                try {
                    val parsed = android.net.Uri.parse(url.trim())
                    val scheme = parsed.scheme?.lowercase()
                    if (scheme !in setOf("https", "content", "file")) {
                        notifyJs("error", "URL no admitida. Usa HTTPS o un archivo local autorizado.")
                        return@runOnUiThread
                    }
                    if (scheme == "https" && parsed.host.isNullOrBlank()) {
                        notifyJs("error", "La dirección de audio no es válida.")
                        return@runOnUiThread
                    }
                    if (scheme == "file" && !url.startsWith("file:///android_asset/")) {
                        notifyJs("error", "Para archivos locales selecciona un archivo mediante el selector del sistema.")
                        return@runOnUiThread
                    }
                    if (player == null) {
                        player = ExoPlayer.Builder(this@MainActivity).build().also {
                            it.addListener(object : Player.Listener {
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    notifyJs(if (isPlaying) "playing" else "paused", "")
                                }
                                override fun onPlaybackStateChanged(state: Int) {
                                    if (state == Player.STATE_ENDED) notifyJs("ended", "")
                                }
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    notifyJs("error", "No se pudo reproducir este flujo: " + (error.message ?: "formato o servidor no compatible"))
                                }
                            })
                        }
                    }
                    player?.setMediaItem(MediaItem.fromUri(parsed))
                    player?.prepare()
                    player?.play()
                    notifyJs("loading", JSONObject.quote(title))
                } catch (e: Exception) {
                    notifyJs("error", e.message ?: "Error al iniciar el reproductor")
                }
            }
        }
        @JavascriptInterface fun pause() { runOnUiThread { player?.pause() } }
        @JavascriptInterface fun resume() { runOnUiThread { player?.play() } }
        @JavascriptInterface fun stop() { runOnUiThread { player?.stop(); notifyJs("stopped", "") } }
        @JavascriptInterface fun isPlaying(): Boolean = player?.isPlaying == true
        @JavascriptInterface fun diagnostics(): String {
            return JSONObject().put("bridge", true).put("platform", "android-kotlin")
                .put("playerCreated", player != null).put("isPlaying", player?.isPlaying == true).toString()
        }
    }

    private fun notifyJs(state: String, detail: String) {
        val safeState = JSONObject.quote(state)
        val safeDetail = JSONObject.quote(detail)
        web.evaluateJavascript("window.onJRPlayerEvent && window.onJRPlayerEvent($safeState,$safeDetail)", null)
    }

    override fun onDestroy() {
        player?.release()
        player = null
        web.removeJavascriptInterface("JRAndroid")
        web.destroy()
        super.onDestroy()
    }
}
