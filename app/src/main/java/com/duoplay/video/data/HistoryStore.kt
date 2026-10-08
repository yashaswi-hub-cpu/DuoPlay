package com.duoplay.video.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val uri: String,
    val name: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayed: Long
) {
    val finished: Boolean get() = durationMs > 0 && positionMs >= durationMs - 5_000
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Remembers, for every video, where you stopped. Survives app restarts and phone reboots. */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    private fun readAll(): MutableList<HistoryItem> {
        val out = mutableListOf<HistoryItem>()
        try {
            val arr = JSONArray(prefs.getString("items", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += HistoryItem(
                    uri = o.getString("uri"),
                    name = o.optString("name", "Video"),
                    positionMs = o.optLong("pos", 0),
                    durationMs = o.optLong("dur", 0),
                    lastPlayed = o.optLong("at", 0)
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun writeAll(list: List<HistoryItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("uri", it.uri)
                    .put("name", it.name)
                    .put("pos", it.positionMs)
                    .put("dur", it.durationMs)
                    .put("at", it.lastPlayed)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun all(): List<HistoryItem> = readAll().sortedByDescending { it.lastPlayed }

    fun get(uri: String): HistoryItem? = readAll().firstOrNull { it.uri == uri }

    @Synchronized
    fun upsert(item: HistoryItem) {
        val list = readAll()
        list.removeAll { it.uri == item.uri }
        list.add(item)
        writeAll(list.sortedByDescending { it.lastPlayed }.take(300))
    }

    @Synchronized
    fun remove(uri: String) {
        val list = readAll()
        list.removeAll { it.uri == uri }
        writeAll(list)
    }
}
