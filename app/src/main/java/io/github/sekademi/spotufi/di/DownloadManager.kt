package io.github.sekademi.spotufi.di

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages track downloads for offline playback.
 * Extracted from SongPlayer to isolate download logic.
 */
object DownloadManager {
    private const val TAG = "DownloadManager"

    private val downloading = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )
    @Volatile var onDownloadsChanged: (() -> Unit)? = null

    private val downloadProgress = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val downloadingSongs =
        java.util.concurrent.ConcurrentHashMap<String, io.github.sekademi.spotufi.data.entity.SongsModel>()

    @Volatile var lastDownloadError: String? = null

    fun isDownloading(query: String): Boolean = downloading.contains(query)

    fun downloadProgress(query: String): Int = downloadProgress[query] ?: -1

    fun downloadingSnapshot(): List<Pair<io.github.sekademi.spotufi.data.entity.SongsModel, Int>> =
        downloadingSongs.entries.map { (q, song) -> song to (downloadProgress[q] ?: 0) }

    private fun openDownloadConn(url: String): java.net.HttpURLConnection =
        (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
        }

    /**
     * Download [url] to [tmpFile] using HTTP **Range** requests in chunks, reporting
     * progress (0..100) for [query].
     */
    private fun httpDownloadRanged(url: String, tmpFile: java.io.File, query: String): Boolean {
        val chunk = 8L * 1024 * 1024 // 8 MB
        var total = -1L
        var position = 0L
        downloadProgress[query] = 0
        try {
            java.io.BufferedOutputStream(tmpFile.outputStream()).use { output ->
                outer@ while (true) {
                    val end = if (total > 0) minOf(position + chunk - 1, total - 1) else position + chunk - 1
                    var attempt = 0
                    var fullBody = false
                    while (true) {
                        attempt++
                        val conn = openDownloadConn(url)
                        conn.setRequestProperty("Range", "bytes=$position-$end")
                        try {
                            val code = conn.responseCode
                            if (code !in 200..299) {
                                lastDownloadError = "Stream returned HTTP $code"
                                return false
                            }
                            if (total < 0) {
                                total = conn.getHeaderField("Content-Range")
                                    ?.substringAfter('/')?.toLongOrNull()
                                    ?: conn.contentLengthLong
                            }
                            fullBody = code == 200
                            conn.inputStream.use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = input.read(buf)
                                    if (r < 0) break
                                    output.write(buf, 0, r)
                                    position += r
                                    if (total > 0) {
                                        val pct = ((position * 100) / total).toInt().coerceIn(0, 100)
                                        if (downloadProgress[query] != pct) {
                                            downloadProgress[query] = pct
                                            onDownloadsChanged?.invoke()
                                        }
                                    }
                                }
                            }
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "chunk @${position} failed (attempt $attempt): ${e.message}")
                            if (attempt >= 4) {
                                lastDownloadError = e.message ?: "Connection reset"
                                return false
                            }
                        } finally {
                            conn.disconnect()
                        }
                    }
                    if (fullBody) { total = position; break@outer }
                    if (total in 1..position) break@outer
                    if (total < 0) break@outer
                }
            }
            downloadProgress[query] = 100
            return total <= 0 || position >= total
        } catch (e: Exception) {
            lastDownloadError = e.message ?: "Download error"
            return false
        }
    }

    /**
     * Resolve the track's stream and save the audio to local storage for offline
     * playback. Runs on the IO scope; invokes [onComplete] (main thread) with whether
     * it succeeded.
     */
    private suspend fun downloadToFile(
        song: io.github.sekademi.spotufi.data.entity.SongsModel,
        appContext: Context,
    ): Boolean {
        val dlQuality = io.github.sekademi.spotufi.data.preferences.getDownloadQuality(appContext)
        if (song.spotifyTrackId.isNotBlank()) {
            val flacOk = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                runCatching { downloadFlacToFile(song, appContext) }.getOrDefault(false)
            } ?: false
            if (flacOk) return true
        }
        if (!SongPlayer.youtubeEnabled) {
            lastDownloadError = "Track not available on lossless providers"
            return false
        }

        val query = song.url
        val playback = SongPlayer.resolveYtPlaybackPublic(query, dlQuality.audioQuality, appContext) ?: run {
            lastDownloadError = "Couldn't resolve a stream"
            return false
        }

        val dir = java.io.File(appContext.filesDir, "downloads").apply { mkdirs() }
        val tmpFile = java.io.File(dir, "${song.id}.part")
        val outName = "${song.id}.m4a"

        if (!httpDownloadRanged(playback.streamUrl, tmpFile, song.url)) {
            runCatching { tmpFile.delete() }
            return false
        }

        val uri = saveToPublicMusic(appContext, song, tmpFile, "m4a", "audio/mp4")
        tmpFile.delete()
        if (uri != null) {
            io.github.sekademi.spotufi.data.preferences.addDownload(appContext, song, uri)
            return true
        }
        // Fallback: private storage (no WRITE_EXTERNAL_STORAGE on API < 29, or MediaStore failure)
        val fallbackDir = java.io.File(appContext.filesDir, "downloads").apply { mkdirs() }
        val fallbackFile = java.io.File(fallbackDir, "${song.id}.m4a")
        if (!tmpFile.renameTo(fallbackFile)) {
            lastDownloadError = "Couldn't save file"
            return false
        }
        io.github.sekademi.spotufi.data.preferences.addDownload(appContext, song, fallbackFile.absolutePath)
        return true
    }

    /**
     * Download a true lossless FLAC via SpotiFLAC.
     */
    private suspend fun downloadFlacToFile(
        song: io.github.sekademi.spotufi.data.entity.SongsModel,
        appContext: Context,
    ): Boolean {
        val isrc = runCatching {
            com.metrolist.spotify.Spotify.track(song.spotifyTrackId).getOrNull()?.isrc
        }.getOrNull()
        val flac = when (
            val r = com.metrolist.spotify.SpotiFlac.resolve(song.spotifyTrackId, isrc, preferHiRes = SongPlayer.losslessHiRes)
        ) {
            is com.metrolist.spotify.SpotiFlac.Result.Success -> r.track
            is com.metrolist.spotify.SpotiFlac.Result.Cooldown -> {
                Log.w(TAG, "FLAC download on cooldown for ${song.title}: ${r.message}")
                return false
            }
            else -> return false
        }

        val dir = java.io.File(appContext.filesDir, "downloads").apply { mkdirs() }
        val tmpFile = java.io.File(dir, "${song.id}.flacpart")
        if (!httpDownloadRanged(flac.url, tmpFile, song.url)) {
            Log.e(TAG, "FLAC download failed for ${song.title}: $lastDownloadError")
            runCatching { tmpFile.delete() }
            return false
        }

        val uri = saveToPublicMusic(appContext, song, tmpFile, "flac", "audio/flac")
        tmpFile.delete()
        if (uri != null) {
            io.github.sekademi.spotufi.data.preferences.addDownload(appContext, song, uri)
            Log.d(TAG, "FLAC downloaded (${flac.provider} ${flac.quality}-bit): ${song.title}")
            return true
        }
        // Fallback: private storage
        val fallbackDir = java.io.File(appContext.filesDir, "downloads").apply { mkdirs() }
        val fallbackFile = java.io.File(fallbackDir, "${song.id}.flac")
        if (!tmpFile.renameTo(fallbackFile)) {
            lastDownloadError = "Couldn't save file"
            return false
        }
        io.github.sekademi.spotufi.data.preferences.addDownload(appContext, song, fallbackFile.absolutePath)
        Log.d(TAG, "FLAC downloaded (private fallback): ${song.title}")
        return true
    }

    /**
     * Save a downloaded temp file to the public Music/<folder>/ directory.
     * API 29+: uses MediaStore with IS_PENDING for atomic writes + metadata.
     * API < 29: direct file move to Music/<folder>/.
     * Returns the content/file URI as a string, or null on failure.
     */
    private fun saveToPublicMusic(
        appContext: Context,
        song: io.github.sekademi.spotufi.data.entity.SongsModel,
        tmpFile: java.io.File,
        ext: String,
        mime: String,
    ): String? {
        val folderName = io.github.sekademi.spotufi.data.preferences.getDownloadFolderName(appContext)
        val fileName = "${song.singer} - ${song.title} [${song.id}]"
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .let { if (it.length > 200) it.take(200) else it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val displayName = "$fileName.$ext"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$folderName")
                put(MediaStore.Audio.Media.TITLE, song.title)
                put(MediaStore.Audio.Media.ARTIST, song.singer)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = appContext.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return null

            try {
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    tmpFile.inputStream().use { it.copyTo(out) }
                } ?: run {
                    appContext.contentResolver.delete(uri, null, null)
                    return null
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                appContext.contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                runCatching { appContext.contentResolver.delete(uri, null, null) }
                return null
            }
            return uri.toString()
        } else {
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                folderName,
            ).apply { mkdirs() }
            val outFile = java.io.File(dir, "$fileName.$ext")
            if (!tmpFile.renameTo(outFile)) return null
            return outFile.absolutePath
        }
    }

    fun downloadAll(songs: List<io.github.sekademi.spotufi.data.entity.SongsModel>, context: Context, scope: CoroutineScope) {
        songs.forEach { downloadSong(it, context, scope) }
    }

    fun allDownloaded(
        songs: List<io.github.sekademi.spotufi.data.entity.SongsModel>,
        context: Context,
    ): Boolean {
        if (songs.isEmpty()) return false
        val appContext = context.applicationContext
        return songs.all {
            io.github.sekademi.spotufi.data.preferences.isDownloaded(appContext, it.id.toString())
        }
    }

    fun downloadSong(
        song: io.github.sekademi.spotufi.data.entity.SongsModel,
        context: Context,
        scope: CoroutineScope,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val appContext = context.applicationContext
        val query = song.url
        if (query.isBlank() ||
            io.github.sekademi.spotufi.data.preferences.isDownloaded(appContext, song.id.toString()) ||
            !downloading.add(query)
        ) return
        downloadingSongs[query] = song
        downloadProgress[query] = 0
        onDownloadsChanged?.invoke()
        lastDownloadError = null
        scope.launch {
            val ok = runCatching { downloadToFile(song, appContext) }
                .onFailure { lastDownloadError = it.message ?: "Unexpected error" }
                .getOrDefault(false)
            downloading.remove(query)
            downloadProgress.remove(query)
            downloadingSongs.remove(query)
            withContext(Dispatchers.Main) {
                if (!ok) {
                    android.widget.Toast.makeText(
                        appContext,
                        "Download failed: ${lastDownloadError ?: "unknown reason"}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                } else {
                    val folder = io.github.sekademi.spotufi.data.preferences.getDownloadFolderName(appContext)
                    android.widget.Toast.makeText(
                        appContext,
                        "Saved to Music/$folder",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                onDownloadsChanged?.invoke()
                onComplete(ok)
            }
        }
    }
}
