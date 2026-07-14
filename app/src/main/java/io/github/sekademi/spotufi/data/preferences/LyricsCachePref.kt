package io.github.sekademi.spotufi.data.preferences

import android.content.Context
import io.github.sekademi.spotufi.data.entity.LyricLine
import io.github.sekademi.spotufi.data.entity.Lyrics
import org.json.JSONArray
import org.json.JSONObject

private const val PREF = "LyricsCache"
private const val KEY_ARTIST = "lyrics_cache_"

fun getCachedLyrics(context: Context, key: String): Lyrics? {
    val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getString(KEY_ARTIST + key, null) ?: return null
    return runCatching {
        val o = JSONObject(raw)
        val lines = o.getJSONArray("lines")
        Lyrics(
            lines = (0 until lines.length()).map { i ->
                val line = lines.getJSONObject(i)
                LyricLine(
                    timeMs = line.optLong("t", 0L),
                    text = line.optString("x", ""),
                )
            },
            synced = o.optBoolean("s", false),
        )
    }.getOrNull()
}

fun cacheLyrics(context: Context, key: String, lyrics: Lyrics) {
    if (lyrics.isEmpty) return
    val o = JSONObject().apply {
        put("s", lyrics.synced)
        put("lines", JSONArray(lyrics.lines.map { line ->
            JSONObject().apply {
                put("t", line.timeMs)
                put("x", line.text)
            }
        }))
    }
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ARTIST + key, o.toString())
        .apply()
}
