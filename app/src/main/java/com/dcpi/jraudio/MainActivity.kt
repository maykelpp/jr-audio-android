package com.dcpi.jraudio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.net.Uri
import android.provider.Settings
import android.os.Environment
import java.net.HttpURLConnection
import java.net.URL
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
    private var updateDownloadId: Long = -1L
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != updateDownloadId) return
            val uri = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).getUriForDownloadedFile(id)
            if (uri == null) { notifyJs("update_error", "No se pudo descargar el APK."); return }
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                notifyJs("update_permission", "Autoriza instalar aplicaciones para continuar.")
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                return
            }
            val install = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(install) } catch (e: Exception) { notifyJs("update_error", e.message ?: "No se pudo abrir el instalador.") }
        }
    }

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
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(updateReceiver, android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
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
        permissions.add(Manifest.permission.RECORD_AUDIO)
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 4102)
    }

    inner class AndroidBridge {
        @JavascriptInterface fun platform(): String = "android-kotlin"
        @JavascriptInterface fun positionMs(): Long = controller?.currentPosition ?: 0L
        @JavascriptInterface fun visualizerData(): String = JRPlaybackService.instance?.fftData() ?: "[]"
        @JavascriptInterface fun fetchLyrics(title: String, artist: String) {
            Thread {
                val payload = JSONObject().put("syncedLyrics", "").put("plainLyrics", "").put("source", "LRCLIB")
                try {
                    val query = "track_name=" + Uri.encode(title) + "&artist_name=" + Uri.encode(artist)
                    val conn = (URL("https://lrclib.net/api/get?$query").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000; readTimeout = 8000; requestMethod = "GET"
                        setRequestProperty("User-Agent", "JR-Audio/0.1.0 (Android)")
                    }
                    try {
                        if (conn.responseCode == 200) {
                            val data = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            payload.put("syncedLyrics", data.optString("syncedLyrics"))
                            payload.put("plainLyrics", data.optString("plainLyrics"))
                        }
                    } finally { conn.disconnect() }
                } catch (_: Exception) { payload.put("error", "No se encontraron letras o el servicio no está disponible.") }
                val js = "window.onJRLyrics && window.onJRLyrics(\${JSONObject.quote(payload.toString())})"
                runOnUiThread { if (::web.isInitialized) web.evaluateJavascript(js, null) }
            }.start()
        }
        @JavascriptInterface fun installUpdate(url: String, version: String): String {
            return try {
                val parsed = Uri.parse(url.trim())
                val host = parsed.host?.lowercase().orEmpty()
                require(parsed.scheme == "https" && (host == "github.com" || host == "objects.githubusercontent.com" || host == "githubusercontent.com" || host.endsWith(".githubusercontent.com"))) {
                    "La actualización debe estar alojada en GitHub Releases mediante HTTPS."
                }
                if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                    runOnUiThread { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                    return "Autoriza instalar aplicaciones y vuelve a pulsar Actualizar."
                }
                val req = DownloadManager.Request(parsed).setTitle("JR Audio $version")
                    .setDescription("Descargando actualización de JR Audio")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "JR Audio/JR-Audio-$version.apk")
                updateDownloadId = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                "Descargando actualización. Android solicitará confirmación para instalarla."
            } catch (e: Exception) { "No se pudo iniciar la actualización: \${e.message}" }
        }
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
                .put("lyricsProvider", "LRCLIB")
                .put("audioVisualizer", JRPlaybackService.instance != null)
                .put("appVersion", BuildConfig.VERSION_NAME)
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
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) { }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        if (::web.isInitialized) {
            web.removeJavascriptInterface("JRAndroid")
            web.destroy()
        }
        super.onDestroy()
    }
}
