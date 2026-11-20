package com.dcpi.jraudio

import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

class JRLibrary(private val context: Context) {
    fun list(): JSONArray {
        val out = JSONArray()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
        context.contentResolver.query(collection, projection, null, null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                out.put(JSONObject().put("id", id)
                    .put("title", cursor.getString(titleCol) ?: "Sin título")
                    .put("artist", cursor.getString(artistCol) ?: "Artista desconocido")
                    .put("album", cursor.getString(albumCol) ?: "")
                    .put("duration", cursor.getLong(durationCol))
                    .put("path", cursor.getString(pathCol) ?: "")
                    .put("uri", uri.toString()))
            }
        }
        return out
    }
}