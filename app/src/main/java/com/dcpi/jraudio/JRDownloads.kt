package com.dcpi.jraudio

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject

class JRDownloads(private val context: Context) {
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = context.getSharedPreferences("jr_downloads", Context.MODE_PRIVATE)

    fun enqueue(url: String, title: String): Long {
        val uri = Uri.parse(url.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "Solo se admiten enlaces HTTPS directos." }
        val safe = title.replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(90).ifBlank { "JR_Audio" }
        val request = DownloadManager.Request(uri)
            .setTitle(safe)
            .setDescription("Descargando en JR Audio")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "JR Audio/$safe.mp3")
        val id = manager.enqueue(request)
        val ids = prefs.getStringSet("ids", emptySet())!!.toMutableSet()
        ids.add(id.toString())
        prefs.edit().putStringSet("ids", ids).apply()
        return id
    }

    fun list(): JSONArray {
        val out = JSONArray()
        val ids = prefs.getStringSet("ids", emptySet())!!.toList()
        for (raw in ids) {
            val id = raw.toLongOrNull() ?: continue
            var cursor: Cursor? = null
            try {
                cursor = manager.query(DownloadManager.Query().setFilterById(id))
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Audio"
                    val state = when (status) {
                        DownloadManager.STATUS_PENDING -> "pending"
                        DownloadManager.STATUS_RUNNING -> "downloading"
                        DownloadManager.STATUS_PAUSED -> "paused"
                        DownloadManager.STATUS_SUCCESSFUL -> "completed"
                        DownloadManager.STATUS_FAILED -> "failed"
                        else -> "unknown"
                    }
                    out.put(JSONObject().put("id", id).put("title", title).put("state", state)
                        .put("downloaded", soFar).put("total", total)
                        .put("percent", if (total > 0) (soFar * 100 / total).toInt() else -1)
                        .put("reason", reason))
                }
            } catch (_: Exception) {
                // The Android DownloadManager may have removed an old record.
            } finally { cursor?.close() }
        }
        return out
    }

    fun cancel(id: Long) { manager.remove(id) }
}