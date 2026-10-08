package com.duoplay.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Track(val uri: String, val title: String)

data class Folder(val id: String, val name: String, val tracks: List<Track>)

/** Everything the app remembers lives here, on the phone only. */
class Store(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("mymusic", Context.MODE_PRIVATE)

    fun loadFolders(): List<Folder> {
        val raw = sp.getString("folders", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val t = o.getJSONArray("tracks")
                Folder(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    tracks = (0 until t.length()).map { j ->
                        val to = t.getJSONObject(j)
                        Track(to.getString("uri"), to.getString("title"))
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveFolders(list: List<Folder>) {
        val arr = JSONArray()
        list.forEach { f ->
            val tracks = JSONArray()
            f.tracks.forEach { t ->
                tracks.put(JSONObject().put("uri", t.uri).put("title", t.title))
            }
            arr.put(JSONObject().put("id", f.id).put("name", f.name).put("tracks", tracks))
        }
        sp.edit().putString("folders", arr.toString()).apply()
    }

    var lastFolderId: String?
        get() = sp.getString("lastFolder", null)
        set(v) { sp.edit().putString("lastFolder", v).apply() }

    var lastIndex: Int
        get() = sp.getInt("lastIndex", 0)
        set(v) { sp.edit().putInt("lastIndex", v).apply() }

    var lastPosition: Long
        get() = sp.getLong("lastPosition", 0L)
        set(v) { sp.edit().putLong("lastPosition", v).apply() }

    var themeName: String
        get() = sp.getString("theme", "Midnight") ?: "Midnight"
        set(v) { sp.edit().putString("theme", v).apply() }

    var bgPath: String?
        get() = sp.getString("bgPath", null)
        set(v) { sp.edit().putString("bgPath", v).apply() }

    var dim: Float
        get() = sp.getFloat("dim", 0.45f)
        set(v) { sp.edit().putFloat("dim", v).apply() }
}
