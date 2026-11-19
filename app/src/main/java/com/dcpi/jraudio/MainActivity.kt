package com.dcpi.jraudio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var downloads: JRDownloads
    private lateinit var library: JRLibrary
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingItem: MediaItem? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloads = JRDownloads(this)
        library = JRLibrary(this)
        requestAudioPermissionIfNeeded()
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
        connectPlaybackService()
    }

    private fun connectPlaybackService() {
        val token = SessionToken(this, ComponentName(this, JRPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                pendingItem?.let { item ->
                    controller?.setMediaItem(item)
                    controller?.prepare()
                    controller?.play()
                    pendingItem = null
                }
                notifyJs("service_connected", "Servicio multimedia conectado")
            } catch (e: Exception) {
                notifyJs("error", "No se pudo conectar el servicio multimedia: ${e.message ?: "desconocido"}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestAudioPermissionIfNeeded() {
        val permissions = mutableListOf(
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 4102)
    }

    inner class AndroidBridge {
        @JavascriptInterface fun platform(): String = "android-kotlin"
        @JavascriptInterface fun play(url: String, title: String, artist: String) {
            runOnUiThread {
                val parsed = android.net.Uri.parse(url.trim())
                val scheme = parsed.scheme?.lowercase()
                if (scheme !in setOf("https", "content")) {
                    notifyJs("error", "Usa una URL HTTPS o una canción de la biblioteca local.")
                    return@runOnUiThread
                }
                if (scheme == "https" && parsed.host.isNullOrBlank()) {
                    notifyJs("error", "La dirección de audio no es válida.")
                    return@runOnUiThread
                }
                playItem(MediaItem.Builder().setUri(parsed).setMediaMetadata(
                    MediaMetadata.Builder().setTitle(title).setArtist(artist).build()
                ).build())
            }
        }
        @JavascriptInterface fun playLocal(uri: String, title: String, artist: String) {
            runOnUiThread {
                val parsed = android.net.Uri.parse(uri)
                if (parsed.scheme != "content") {
                    notifyJs("error", "Archivo local no autorizado.")
                    return@runOnUiThread
                }
                playItem(MediaItem.Builder().setUri(parsed).setMediaMetadata(
                    MediaMetadata.Builder().setTitle(title).setArtist(artist).build()
                ).build())
            }
        }
        @JavascriptInterface fun pause() { runOnUiThread { controller?.pause() } }
        @JavascriptInterface fun resume() { runOnUiThread { controller?.play() } }
        @JavascriptInterface fun stop() { runOnUiThread { controller?.stop(); notifyJs("stopped", "") } }
        @JavascriptInterface fun download(url: String, title: String): String {
            return try {
                val id = downloads.enqueue(url, title)
                notifyJs("download_queued", "Descarga añadida a la cola (#$id)")
                id.toString()
            } catch (e: Exception) {
                notifyJs("error", e.message ?: "No se pudo iniciar la descarga")
                ""
            }
        }
        @JavascriptInterface fun downloadQueue(): String = downloads.list().toString()
        @JavascriptInterface fun cancelDownload(id: Long) { downloads.cancel(id); notifyJs("downloads_changed", "Descarga cancelada") }
        @JavascriptInterface fun localLibrary(): String {
            return try { library.list().toString() } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "No se pudo leer la biblioteca").toString()
            }
        }
        @JavascriptInterface fun refreshDownloads() { notifyJs("downloads_data", downloads.list().toString()) }
        @JavascriptInterface fun requestMediaPermission() {
            runOnUiThread { requestAudioPermissionIfNeeded() }
        }
        @JavascriptInterface fun diagnostics(): String {
            val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
            val granted = ContextCompat.checkSelfPermission(this@MainActivity, audioPermission) == PackageManager.PERMISSION_GRANTED
            val notifications = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val count = if (granted) try { library.list().length() } catch (_: Exception) { -1 } else -1
            return JSONObject()
                .put("bridge", true)
                .put("platform", "android-kotlin")
                .put("sdk", Build.VERSION.SDK_INT)
                .put("release", Build.VERSION.RELEASE)
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("audioPermission", granted)
                .put("notificationPermission", notifications)
                .put("localAudioCount", count)
                .put("mediaSessionService", true)
                .put("downloadManager", true)
                .put("localMediaStore", true).toString()
        }
    }

    private fun playItem(item: MediaItem) {
        val active = controller
        if (active != null) {
            active.setMediaItem(item)
            active.prepare()
            active.play()
            notifyJs("loading", item.mediaMetadata.title?.toString() ?: "Audio")
        } else {
            pendingItem = item
            notifyJs("connecting", "Conectando con el servicio de reproducción…")
            controllerFuture?.addListener({
                controller?.let {
                    pendingItem?.let { media ->
                        it.setMediaItem(media)
                        it.prepare()
                        it.play()
                        pendingItem = null
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun notifyJs(state: String, detail: String) {
        if (!::web.isInitialized) return
        val safeState = JSONObject.quote(state)
        val safeDetail = JSONObject.quote(detail)
        runOnUiThread {
            web.evaluateJavascript("window.onJRPlayerEvent && window.onJRPlayerEvent($safeState,$safeDetail)", null)
        }
    }

    override fun onDestroy() {
        controller?.let { MediaController.releaseFuture(controllerFuture!!) }
        controller = null
        controllerFuture = null
        if (::web.isInitialized) {
            web.removeJavascriptInterface("JRAndroid")
            web.destroy()
        }
        super.onDestroy()
    }
}
