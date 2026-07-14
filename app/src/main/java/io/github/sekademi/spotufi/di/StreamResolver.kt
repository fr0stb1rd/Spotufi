package io.github.sekademi.spotufi.di

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.utils.YTPlayerUtils

/**
 * Resolves stream URLs for playback from various sources:
 * - Alternative streams (local files, YouTube)
 * - Downloaded tracks (local files)
 * - Cached streams
 * - Lossless (SpotiFLAC from Tidal/Qobuz/Amazon)
 * - YouTube (with video candidate scoring)
 *
 * Extracted from SongPlayer to isolate stream resolution logic.
 */
object StreamResolver {
    private const val TAG = "StreamResolver"
    private const val SPOTIFY_TRACK_PREFIX = "spotify:track:"
    private const val PRELOAD_BYTES = 1L * 1024 * 1024

    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val sourceCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val qualityCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile var currentSource: String = "YouTube"
        internal set
    @Volatile var currentQuality: String = ""
        internal set

    @Volatile var losslessStreaming = true
    @Volatile var losslessHiRes = true
    @Volatile var youtubeEnabled = true

    private val trackIdRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val alternativeKeyRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val explicitRegistry = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val durationRegistry = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val metadataRegistry = java.util.concurrent.ConcurrentHashMap<String, CandidateScorer.TrackMatchMetadata>()

    private val featSearchPattern = Regex("""\s*[\(\[]\s*(feat|ft)\..*?[\)\]]""", RegexOption.IGNORE_CASE)

    fun registerLossless(pairs: List<Pair<String, String>>) {
        pairs.forEach { (query, spotifyId) ->
            if (query.isNotBlank() && spotifyId.isNotBlank()) trackIdRegistry[query] = spotifyId
        }
    }

    fun registerAlternativeKeys(pairs: List<Pair<String, String>>) {
        pairs.forEach { (query, key) ->
            if (query.isNotBlank() && key.isNotBlank()) alternativeKeyRegistry[query] = key
        }
    }

    fun registerExplicit(pairs: List<Pair<String, Boolean>>) {
        pairs.forEach { (query, explicit) ->
            if (query.isNotBlank()) explicitRegistry[query] = explicit
        }
    }

    fun registerDuration(pairs: List<Pair<String, Int>>) {
        pairs.forEach { (query, ms) ->
            if (query.isNotBlank() && ms > 0) durationRegistry[query] = ms
        }
    }

    fun registerMetadata(pairs: List<Pair<String, CandidateScorer.TrackMatchMetadata>>) {
        pairs.forEach { (query, meta) ->
            if (query.isNotBlank() && meta.title.isNotBlank()) metadataRegistry[query] = meta
        }
    }

    fun invalidateResolvedStream(song: String) {
        streamCache.remove(song)
        sourceCache.remove(song)
        qualityCache.remove(song)
    }

    fun buildSpotifyPlayQuery(spotifyTrackId: String, title: String, artist: String): String {
        val searchText = listOf(cleanSpotifySearchTitle(title), artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return if (spotifyTrackId.isBlank()) searchText else "$SPOTIFY_TRACK_PREFIX$spotifyTrackId|$searchText"
    }

    fun cleanSpotifySearchTitle(title: String): String =
        title.replace(featSearchPattern, "").trim()

    fun searchTextForPlayback(song: String): String =
        if (song.startsWith(SPOTIFY_TRACK_PREFIX) && song.contains('|')) {
            song.substringAfter('|').ifBlank { song }
        } else {
            song
        }

    fun spotifyTrackIdForPlayback(song: String): String? =
        if (song.startsWith(SPOTIFY_TRACK_PREFIX)) {
            song.removePrefix(SPOTIFY_TRACK_PREFIX).substringBefore('|').takeIf { it.isNotBlank() }
        } else {
            null
        }

    private fun alternativeStreamForPlayback(
        song: String,
        appContext: Context,
    ): io.github.sekademi.spotufi.data.preferences.AlternativeStream? {
        val key = alternativeKeyRegistry[song]
            ?: spotifyTrackIdForPlayback(song)?.let {
                io.github.sekademi.spotufi.data.preferences.alternativeStreamKeyForSpotifyId(it)
            }
        return key?.let { io.github.sekademi.spotufi.data.preferences.getAlternativeStream(appContext, it) }
    }

    suspend fun resolveStreamUrl(song: String, appContext: Context, forPlayback: Boolean = false): String? {
        alternativeStreamForPlayback(song, appContext)?.let { alt ->
            invalidateResolvedStream(song)
            return when {
                alt.isLocal -> {
                    if (forPlayback) {
                        currentSource = "Alternative file"
                        currentQuality = alt.label.substringAfterLast('.', "").uppercase().takeIf { it.length in 2..5 }.orEmpty()
                    }
                    alt.value
                }
                alt.isYouTube -> {
                    if (forPlayback) {
                        currentSource = "Alternative YouTube"
                        currentQuality = ""
                    }
                    val quality = io.github.sekademi.spotufi.data.preferences.currentStreamingQuality(appContext)
                    val playback = resolveYtPlayback(alt.value, quality.audioQuality, appContext) ?: return null
                    val codec = playback.format.mimeType
                        .substringAfter("codecs=\"", "").substringBefore('"').substringBefore('.')
                        .uppercase()
                    val ytQuality = listOf(codec, "${playback.format.bitrate / 1000} kbps")
                        .filter { it.isNotBlank() }.joinToString(" ")
                    if (forPlayback) currentQuality = ytQuality
                    playback.streamUrl
                }
                else -> null
            }
        }
        io.github.sekademi.spotufi.data.preferences.downloadedPathForQuery(appContext, song)?.let { path ->
            if (forPlayback) {
                currentSource = "Downloaded"
                currentQuality = if (path.startsWith("content://")) "AUDIO" else path.substringAfterLast('.', "").uppercase()
            }
            return if (path.startsWith("content://") || path.startsWith("file://")) path
            else android.net.Uri.fromFile(java.io.File(path)).toString()
        }
        streamCache[song]?.let {
            if (forPlayback) {
                currentSource = sourceCache[song] ?: "YouTube"
                currentQuality = qualityCache[song] ?: ""
            }
            return it
        }
        val quality = io.github.sekademi.spotufi.data.preferences.currentStreamingQuality(appContext)
        if (losslessStreaming && quality.lossless) {
            (trackIdRegistry[song] ?: spotifyTrackIdForPlayback(song))?.let { spotifyId ->
                val r = kotlinx.coroutines.withTimeoutOrNull(8_000) {
                    com.metrolist.spotify.SpotiFlac.resolve(
                        spotifyId,
                        isrc = null,
                        preferHiRes = losslessHiRes,
                    )
                }
                when (r) {
                    is com.metrolist.spotify.SpotiFlac.Result.Success -> {
                        Log.d(TAG, "lossless ${r.track.provider} ${r.track.quality}-bit for: $song")
                        val flacQuality = "FLAC ${r.track.quality}-bit"
                        if (forPlayback) {
                            currentSource = "Lossless • ${r.track.provider}"
                            currentQuality = flacQuality
                        }
                        streamCache[song] = r.track.url
                        sourceCache[song] = "Lossless • ${r.track.provider}"
                        qualityCache[song] = flacQuality
                        return r.track.url
                    }
                    is com.metrolist.spotify.SpotiFlac.Result.Cooldown ->
                        Log.d(TAG, "lossless on cooldown, using YouTube for: $song")
                    null -> Log.w(TAG, "lossless timed out, using YouTube for: $song")
                    else -> Log.w(TAG, "lossless miss ($r), using YouTube for: $song")
                }
            }
        }
        if (!youtubeEnabled) {
            Log.w(TAG, "YouTube fallback disabled — no stream for: $song")
            return null
        }
        if (forPlayback) {
            currentSource = "YouTube"
            currentQuality = ""
        }
        val playback = resolveYtPlayback(song, quality.audioQuality, appContext) ?: return null
        val codec = playback.format.mimeType
            .substringAfter("codecs=\"", "").substringBefore('"').substringBefore('.')
            .uppercase()
        val ytQuality = listOf(codec, "${playback.format.bitrate / 1000} kbps")
            .filter { it.isNotBlank() }.joinToString(" ")
        if (forPlayback) currentQuality = ytQuality
        streamCache[song] = playback.streamUrl
        sourceCache[song] = "YouTube"
        qualityCache[song] = ytQuality
        return playback.streamUrl
    }

    private suspend fun ensureSpotifyMatchMetadata(query: String): CandidateScorer.TrackMatchMetadata? {
        val currentMeta = metadataRegistry[query]
        val hasUsefulMeta = currentMeta?.let {
            it.title.isNotBlank() && it.artist.isNotBlank() && it.album.isNotBlank()
        } ?: false
        if (hasUsefulMeta && durationRegistry[query] != null && explicitRegistry.containsKey(query)) {
            return currentMeta
        }

        val spotifyId = trackIdRegistry[query] ?: spotifyTrackIdForPlayback(query) ?: return currentMeta
        val track = runCatching { com.metrolist.spotify.Spotify.track(spotifyId).getOrNull() }
            .onFailure { Log.w(TAG, "Spotify metadata repair failed for $spotifyId", it) }
            .getOrNull()
            ?: return currentMeta

        val repaired = CandidateScorer.TrackMatchMetadata(
            title = track.name,
            artist = track.artists.joinToString(", ") { it.name },
            album = track.album?.name ?: currentMeta?.album.orEmpty(),
        )
        metadataRegistry[query] = repaired
        trackIdRegistry[query] = spotifyId
        explicitRegistry[query] = track.explicit
        if (track.durationMs > 0) durationRegistry[query] = track.durationMs
        return repaired
    }

    private suspend fun resolveVideoCandidates(
        query: String,
        filter: YouTube.SearchFilter = YouTube.SearchFilter.FILTER_SONG,
    ): List<String> {
        val searchText = searchTextForPlayback(query)
        if (searchText.length == 11 && !searchText.contains(' ')) return listOf(searchText)
        val hits = YouTube.search(searchText, filter)
            .onFailure { Log.w(TAG, "resolveVideoId: YouTube search failed for: $searchText", it) }
            .getOrNull()
            ?.items
            ?.filterIsInstance<SongItem>()
            .orEmpty()
        if (hits.isEmpty()) {
            Log.w(TAG, "resolveVideoId: no YouTube song results for: $searchText")
            return emptyList()
        }
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val qn = norm(searchText)
        val exactMeta = ensureSpotifyMatchMetadata(query)
        val wantSec = durationRegistry[query]?.let { it / 1000 }
        val scored = hits.map { h ->
            val cleanTitle = norm(h.title.substringBefore('(').substringBefore('['))
            var s = 0
            if (cleanTitle.isNotEmpty() && qn.contains(cleanTitle)) s += 1
            if (h.artists.any { a -> norm(a.name).let { it.isNotEmpty() && qn.contains(it) } }) s += 2
            val hDur = h.duration
            if (wantSec != null && hDur != null && kotlin.math.abs(hDur - wantSec) <= 4) s += 2
            h to s
        }
        val transferScored = if (exactMeta != null) {
            hits.map { CandidateScorer.ytmusicTransferScore(it, exactMeta, durationRegistry[query] ?: 0) }
        } else {
            emptyList()
        }
        fun verified(h: SongItem): Boolean {
            val artistOk = h.artists.any { a -> norm(a.name).let { it.isNotEmpty() && qn.contains(it) } }
            val d = h.duration
            val durOk = wantSec != null && d != null && kotlin.math.abs(d - wantSec) <= 4
            return artistOk || durOk
        }
        val wantExplicit = explicitRegistry[query]
        fun explicitFirst(list: List<SongItem>) =
            if (wantExplicit != null) list.sortedByDescending { it.explicit == wantExplicit } else list
        val ordered = if (transferScored.isNotEmpty()) {
            val accepted = transferScored
                .filter { with(CandidateScorer) { it.isAcceptableMatch() } }
                .sortedWith(
                    compareByDescending<CandidateScorer.CandidateScore> { it.item.explicit == wantExplicit || wantExplicit == null }
                        .thenByDescending { it.score }
                )
                .map { it.item }
            if (accepted.isEmpty()) {
                val best = transferScored.maxByOrNull { it.score }
                Log.w(
                    TAG,
                    "resolveVideoId: rejecting weak YouTube match for '$searchText' " +
                        "(best='${best?.item?.title}' score=${"%.2f".format(best?.score ?: 0.0)} " +
                        "title=${"%.2f".format(best?.titleScore ?: 0.0)} " +
                        "artist=${"%.2f".format(best?.artistEvidenceScore ?: 0.0)} " +
                        "duration=${"%.2f".format(best?.durationScore ?: 0.0)} " +
                        "album=${"%.2f".format(best?.albumScore ?: 0.0)} " +
                        "alt=${best?.unexpectedAlternates.orEmpty().joinToString("/")})",
                )
                return emptyList()
            }
            accepted.distinctBy { it.id }
        } else {
            val verifiedRanked = scored
                .filter { verified(it.first) }
                .sortedByDescending { it.second }
                .map { it.first }
            val restRanked = scored
                .filter { !verified(it.first) }
                .sortedByDescending { it.second }
                .map { it.first }
            (explicitFirst(verifiedRanked) + explicitFirst(restRanked)).distinctBy { it.id }
        }

        if (ordered.isEmpty()) return emptyList()
        val chosen = ordered.first()
        if (transferScored.isEmpty() && !verified(chosen)) {
            Log.w(TAG, "resolveVideoId: no verified match for: $searchText (want=${wantSec}s) — best-effort '${chosen.title}'")
        }
        val chosenScore = transferScored.firstOrNull { it.item.id == chosen.id }
        Log.d(
            TAG,
            "resolveVideoId: '$searchText' -> '${chosen.title}' by " +
                chosen.artists.joinToString { it.name } +
                " [explicit=${chosen.explicit} dur=${chosen.duration}s want=${wantSec}s id=${chosen.id}] " +
                (chosenScore?.let {
                    "score=${"%.2f".format(it.score)} title=${"%.2f".format(it.titleScore)} " +
                        "artist=${"%.2f".format(it.artistEvidenceScore)} duration=${"%.2f".format(it.durationScore ?: 0.0)} " +
                        "album=${"%.2f".format(it.albumScore ?: 0.0)} " +
                        "altPenalty=${"%.2f".format(it.alternatePenalty)} " +
                        "alt=${it.unexpectedAlternates.joinToString("/")}"
                } ?: "${ordered.count { verified(it) }} verified/${ordered.size}"),
        )
        return ordered.map { it.id }.distinct()
    }

    suspend fun resolveYtPlayback(
        query: String,
        audioQuality: AudioQuality,
        appContext: Context,
    ): YTPlayerUtils.PlaybackData? {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val tried = mutableSetOf<String>()
        suspend fun tryIds(ids: List<String>): YTPlayerUtils.PlaybackData? {
            for (videoId in ids) {
                if (!tried.add(videoId)) continue
                YTPlayerUtils.playerResponseForPlayback(
                    videoId = videoId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                ).fold(
                    onSuccess = { return it },
                    onFailure = { Log.w(TAG, "stream failed for $videoId (${it.message}) — trying next candidate for: ${searchTextForPlayback(query)}") },
                )
            }
            return null
        }
        tryIds(resolveVideoCandidates(query).take(3))?.let { return it }
        if (!io.github.sekademi.spotufi.data.preferences.isVideoFallbackEnabled(appContext)) {
            Log.w(TAG, "song candidates exhausted and video fallback disabled for: ${searchTextForPlayback(query)}")
            return null
        }
        Log.w(TAG, "song candidates exhausted, trying video search for: ${searchTextForPlayback(query)}")
        tryIds(resolveVideoCandidates(query, YouTube.SearchFilter.FILTER_VIDEO).take(3))?.let { return it }
        Log.e(TAG, "All YouTube candidates failed for: ${searchTextForPlayback(query)}")
        return null
    }

    // ── Media cache ──
    @Volatile private var mediaCache: androidx.media3.datasource.cache.SimpleCache? = null

    fun mediaCache(context: Context): androidx.media3.datasource.cache.SimpleCache =
        mediaCache ?: synchronized(this) {
            mediaCache ?: androidx.media3.datasource.cache.SimpleCache(
                java.io.File(context.cacheDir, "media"),
                androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
                androidx.media3.database.StandaloneDatabaseProvider(context),
            ).also { mediaCache = it }
        }

    fun cacheDataSourceFactory(context: Context): androidx.media3.datasource.cache.CacheDataSource.Factory {
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .setAllowCrossProtocolRedirects(true)
        val upstream = androidx.media3.datasource.DefaultDataSource.Factory(context, http)
        return androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(mediaCache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun cacheIntro(url: String, appContext: Context) {
        if (!url.startsWith("http")) return
        if (!io.github.sekademi.spotufi.data.preferences.isPreloadEnabled(appContext)) return
        runCatching {
            val ds = cacheDataSourceFactory(appContext).createDataSource()
            val spec = androidx.media3.datasource.DataSpec.Builder()
                .setUri(android.net.Uri.parse(url))
                .setLength(PRELOAD_BYTES)
                .build()
            androidx.media3.datasource.cache.CacheWriter(ds, spec, null, null).cache()
        }.onFailure { Log.d(TAG, "intro preload skipped: ${it.message}") }
    }
}
