package io.github.sekademi.spotufi.data.preferences

import android.content.Context
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.di.RepeatMode
import org.json.JSONArray
import org.json.JSONObject

private const val PREF = "PlaybackState"
private const val KEY_SONG = "song"
private const val KEY_POSITION = "positionMs"
private const val KEY_QUEUE = "queue"
private const val KEY_REPEAT = "repeat_mode"

private const val PREF_QUEUE = "PlaybackQueue"

fun saveLastPlayback(context: Context, song: SongsModel) {
    if (song.title.isBlank() || song.url.isBlank()) return
    val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val prevId = runCatching { p.getString(KEY_SONG, null)?.let { JSONObject(it).optInt("id", -1) } }
        .getOrNull() ?: -1
    val json = JSONObject().apply {
        put("id", song.id); put("title", song.title); put("album", song.album)
        put("singer", song.singer); put("coverUri", song.coverUri)
        put("url", song.url); put("spotifyTrackId", song.spotifyTrackId)
        put("explicit", song.explicit); put("durationMs", song.durationMs)
        put("artistIds", song.artistIds)
    }
    p.edit().apply {
        putString(KEY_SONG, json.toString())
        if (prevId != song.id) putLong(KEY_POSITION, 0L)
    }.apply()
}

fun saveLastPosition(context: Context, positionMs: Long) {
    if (positionMs <= 0) return
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit().putLong(KEY_POSITION, positionMs).apply()
}

fun loadLastPlayback(context: Context): Pair<SongsModel, Long>? {
    val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val raw = p.getString(KEY_SONG, null) ?: return null
    return runCatching {
        val o = JSONObject(raw)
        val song = SongsModel(
            id = o.optInt("id", -1),
            title = o.optString("title"),
            album = o.optString("album"),
            singer = o.optString("singer"),
            coverUri = o.optString("coverUri"),
            url = o.optString("url"),
            spotifyTrackId = o.optString("spotifyTrackId"),
            explicit = o.optBoolean("explicit", false),
            durationMs = o.optInt("durationMs", 0),
            artistIds = o.optString("artistIds"),
        )
        if (song.title.isBlank() || song.url.isBlank()) null
        else song to p.getLong(KEY_POSITION, 0L)
    }.getOrNull()
}

private fun songToJson(song: SongsModel): JSONObject = JSONObject().apply {
    put("id", song.id); put("title", song.title); put("album", song.album)
    put("singer", song.singer); put("coverUri", song.coverUri)
    put("url", song.url); put("spotifyTrackId", song.spotifyTrackId)
    put("explicit", song.explicit); put("durationMs", song.durationMs)
    put("artistIds", song.artistIds)
}

private fun jsonToSong(o: JSONObject): SongsModel = SongsModel(
    id = o.optInt("id", -1),
    title = o.optString("title"),
    album = o.optString("album"),
    singer = o.optString("singer"),
    coverUri = o.optString("coverUri"),
    url = o.optString("url"),
    spotifyTrackId = o.optString("spotifyTrackId"),
    explicit = o.optBoolean("explicit", false),
    durationMs = o.optInt("durationMs", 0),
    artistIds = o.optString("artistIds"),
)

fun saveQueue(context: Context, queue: List<SongsModel>) {
    if (queue.isEmpty()) return
    val arr = JSONArray(queue.map { songToJson(it) })
    context.getSharedPreferences(PREF_QUEUE, Context.MODE_PRIVATE)
        .edit().putString(KEY_QUEUE, arr.toString()).apply()
}

fun loadQueue(context: Context): List<SongsModel>? {
    val raw = context.getSharedPreferences(PREF_QUEUE, Context.MODE_PRIVATE)
        .getString(KEY_QUEUE, null) ?: return null
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i -> jsonToSong(arr.getJSONObject(i)) }
    }.getOrNull()
}

fun saveRepeatMode(context: Context, mode: RepeatMode) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit().putString(KEY_REPEAT, mode.name).apply()
}

fun loadRepeatMode(context: Context): RepeatMode? {
    val name = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getString(KEY_REPEAT, null) ?: return null
    return runCatching { RepeatMode.valueOf(name) }.getOrNull()
}

data class RestorePoint(
    val song: SongsModel,
    val positionMs: Long,
    val queue: List<SongsModel>,
    val repeatMode: RepeatMode?,
)

fun loadRestorePoint(context: Context): RestorePoint? {
    val (song, positionMs) = loadLastPlayback(context) ?: return null
    val queue = loadQueue(context) ?: listOf(song)
    val repeatMode = loadRepeatMode(context)
    return RestorePoint(song, positionMs, queue, repeatMode)
}
