package com.duoplay.video.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class DeviceVideo(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val folder: String
)

/** Lists every video on the phone (needs the storage/video permission). */
object VideoLibrary {
    fun query(ctx: Context): List<DeviceVideo> {
        val out = ArrayList<DeviceVideo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        try {
            ctx.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iDir = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                while (c.moveToNext()) {
                    out += DeviceVideo(
                        uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(iId)),
                        name = c.getString(iName) ?: "Video",
                        durationMs = c.getLong(iDur),
                        folder = c.getString(iDir) ?: "Phone"
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}
