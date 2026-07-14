package io.github.sekademi.spotufi.di

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.innertube.utils.YouTubeUrlParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Core audio playback orchestrator. Delegates stream resolution to [StreamResolver],
 * candidate scoring to [CandidateScorer], downloads to [DownloadManager], and
 * crossfade/DJ mixing to [CrossfadeEngine].
 */
object SongPlayer {
    private const val TAG = "SongPlayer"
    private var player: ExoPlayer? = null
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appCtx: Context? = null
    @Volatile private var currentPlayerFilter: io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor? = null
    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val sourceCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val videoCandidatesCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    // ── Stream resolution delegation ──
    var currentSource: String
        get() = StreamResolver.currentSource
        set(value) { StreamResolver.currentSource = value }
    var currentQuality: String
        get() = StreamResolver.currentQuality
        set(value) { StreamResolver.currentQuality = value }
    var losslessStreaming: Boolean
        get() = StreamResolver.losslessStreaming
        set(value) { StreamResolver.losslessStreaming = value }
    var losslessHiRes: Boolean
        get() = StreamResolver.losslessHiRes
        set(value) { StreamResolver.losslessHiRes = value }
    @Volatile var webPlayerEnabled = false
    var youtubeEnabled: Boolean
        get() = StreamResolver.youtubeEnabled
        set(value) { StreamResolver.youtubeEnabled = value }

    fun registerLossless(pairs: List<Pair<String, String>>) = StreamResolver.registerLossless(pairs)
    fun registerAlternativeKeys(pairs: List<Pair<String, String>>) = StreamResolver.registerAlternativeKeys(pairs)
    fun registerExplicit(pairs: List<Pair<String, Boolean>>) = StreamResolver.registerExplicit(pairs)
    fun registerDuration(pairs: List<Pair<String, Int>>) = StreamResolver.registerDuration(pairs)
    fun registerMetadata(pairs: List<Pair<String, CandidateScorer.TrackMatchMetadata>>) = StreamResolver.registerMetadata(pairs)
    fun invalidateResolvedStream(song: String) {
        streamCache.remove(song)
        sourceCache.remove(song)
        videoCandidatesCache.remove("$song|FILTER_SONG")
        videoCandidatesCache.remove("$song|FILTER_VIDEO")
        appCtx?.let { ctx ->
            io.github.sekademi.spotufi.data.preferences.clearCachedVideoId(ctx, song)
            io.github.sekademi.spotufi.data.preferences.clearCachedStream(ctx, song)
        }
        StreamResolver.invalidateResolvedStream(song)
    }
    fun buildSpotifyPlayQuery(spotifyTrackId: String, title: String, artist: String) = StreamResolver.buildSpotifyPlayQuery(spotifyTrackId, title, artist)
    fun searchTextForPlayback(song: String) = StreamResolver.searchTextForPlayback(song)
    fun videoIdFromYouTubeLink(text: String): String? =
        YouTubeUrlParser.extractVideoId(text)
            ?: text.trim().takeIf { it.matches(Regex("""[A-Za-z0-9_-]{11}""")) }

    @Volatile private var boundState: CurrentSongState? = null
    @Volatile private var lastYtFailureReason: String? = null

    private fun updateResolveStatus(isResolving: Boolean, status: String = "") {
        boundState?.updateResolveState(isResolving, status)
    }

    // ── Download delegation ──
    @Volatile var onDownloadsChanged: (() -> Unit)? = null
        set(value) { field = value; DownloadManager.onDownloadsChanged = value }
    @Volatile var lastDownloadError: String? = null
        get() = DownloadManager.lastDownloadError

    fun isDownloading(query: String): Boolean = DownloadManager.isDownloading(query)
    fun downloadProgress(query: String): Int = DownloadManager.downloadProgress(query)
    fun downloadingSnapshot(): List<Pair<io.github.sekademi.spotufi.data.entity.SongsModel, Int>> = DownloadManager.downloadingSnapshot()
    fun downloadAll(songs: List<io.github.sekademi.spotufi.data.entity.SongsModel>, context: Context) = DownloadManager.downloadAll(songs, context, scope)
    fun allDownloaded(songs: List<io.github.sekademi.spotufi.data.entity.SongsModel>, context: Context): Boolean = DownloadManager.allDownloaded(songs, context)
    fun downloadSong(song: io.github.sekademi.spotufi.data.entity.SongsModel, context: Context, onComplete: (Boolean) -> Unit = {}) = DownloadManager.downloadSong(song, context, scope, onComplete)

    // ── Crossfade delegation ──
    @Volatile var onPlayerSwapped: ((ExoPlayer) -> Unit)? = null
    fun isCrossfadeActive(): Boolean = CrossfadeEngine.isCrossfadeActive()
    private fun cancelCrossfade() = CrossfadeEngine.cancelCrossfade()
    private fun startPositionWatch() = CrossfadeEngine.startPositionWatch(scope)
    private fun triggerCrossfade(ctx: Context, configuredMs: Int) = CrossfadeEngine.triggerCrossfade(ctx, configuredMs, scope)

    fun initCrossfade(appContext: Context, state: CurrentSongState) {
        CrossfadeEngine.init(appContext, state)
        CrossfadeEngine.onPlayerSwapped = { onPlayerSwapped?.invoke(it) }
        boundState = state
    }

    // ── Media notification metadata ──
    @Volatile private var metaTitle: String = ""
    @Volatile private var metaArtist: String = ""
    @Volatile private var metaCover: String = ""
    @Volatile private var currentRequest: String = ""
    @Volatile private var loadedQuery: String? = null
    @Volatile private var playWhenResolved = true

    fun setNowPlayingMeta(title: String, artist: String, coverUri: String) {
        metaTitle = title
        metaArtist = artist
        metaCover = coverUri
    }

    // ── Stream resolution (delegates to StreamResolver with caching) ──
    private suspend fun resolveStreamUrl(song: String, appContext: Context, forPlayback: Boolean = false): String? {
        streamCache[song]?.let {
            if (forPlayback) {
                currentSource = sourceCache[song] ?: "YouTube"
                updateResolveStatus(false)
            }
            return it
        }
        return StreamResolver.resolveStreamUrl(song, appContext, forPlayback)
    }

    suspend fun resolveYtPlaybackPublic(
        query: String,
        audioQuality: com.metrolist.music.constants.AudioQuality,
        appContext: Context,
    ): com.metrolist.music.utils.YTPlayerUtils.PlaybackData? = StreamResolver.resolveYtPlayback(query, audioQuality, appContext)

    fun resolveStreamUrlPublic(song: String, appContext: Context, forPlayback: Boolean = false): String? =
        kotlinx.coroutines.runBlocking { StreamResolver.resolveStreamUrl(song, appContext, forPlayback) }

    // ── MediaItem + ExoPlayer ──
    private fun buildMediaItem(streamUrl: String, mimeType: String? = null): MediaItem {
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(metaTitle)
            .setArtist(metaArtist)
            .apply { if (metaCover.isNotBlank()) setArtworkUri(android.net.Uri.parse(metaCover)) }
            .build()
        return MediaItem.Builder()
            .setUri(streamUrl)
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .setMediaMetadata(metadata)
            .build()
    }

    fun buildMediaItemPublic(streamUrl: String): MediaItem = buildMediaItem(streamUrl)
    fun buildAudioAttributesPublic() = buildAudioAttributes()

    private fun buildAudioAttributes() =
        androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun createPlayerWithFilterPublic(
        context: Context,
        handleAudioFocus: Boolean,
    ): Pair<ExoPlayer, io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor> =
        createPlayerWithFilter(context, handleAudioFocus)

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun createPlayerWithFilter(
        context: Context,
        handleAudioFocus: Boolean,
    ): Pair<ExoPlayer, io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor> {
        val filter = io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor()
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain(filter),
                    ).build()
        }
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(StreamResolver.cacheDataSourceFactory(context)),
            )
            .setRenderersFactory(renderers)
            .setAudioAttributes(buildAudioAttributes(), handleAudioFocus)
            .setHandleAudioBecomingNoisy(handleAudioFocus)
            .build()
        return p to filter
    }

    private fun ensurePlayer(context: Context) {
        appCtx = context.applicationContext
        if (player == null) {
            val (p, filter) = createPlayerWithFilter(context, handleAudioFocus = true)
            player = p
            currentPlayerFilter = filter
            CrossfadeEngine.bindPrimaryFilter(filter)
            onPlayerCreated?.invoke(p)
        }
    }

    fun promotePlayer(incoming: ExoPlayer, filter: io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor?) {
        player = incoming
        currentPlayerFilter = filter
        CrossfadeEngine.bindPrimaryFilter(filter)
    }

    val exoPlayer: ExoPlayer? get() = player
    fun ensureCreated(context: Context) = ensurePlayer(context.applicationContext)
    @Volatile var onPlayerCreated: ((ExoPlayer) -> Unit)? = null

    // ── Playback controls ──
    fun isPlaying(): Boolean {
        if (webPlaybackActive()) return SpotifyWebPlayer.isPlaying
        return player?.isPlaying ?: false
    }

    fun webPlaybackActive(): Boolean {
        if (!webPlayerEnabled) return false
        val ctx = appCtx ?: return false
        return io.github.sekademi.spotufi.data.preferences.isWebPlaybackEnabled(ctx) &&
            SpotifyWebPlayer.canPlay &&
            io.github.sekademi.spotufi.data.api.SpotifySession.spDc(ctx).isNotBlank()
    }

    // ── Session restore ──
    @Volatile private var restoreQuery: String? = null
    @Volatile private var restorePositionMs: Long = 0L

    fun setRestorePoint(query: String, positionMs: Long) {
        if (query.isBlank()) return
        restoreQuery = query
        restorePositionMs = positionMs.coerceAtLeast(0L)
        loadedQuery = query
    }

    fun playSong(song: String, context: Context) {
        val appContext = context.applicationContext
        appCtx = appContext
        currentRequest = song
        playWhenResolved = true
        boundState?.setSongUrl(song)
        cancelCrossfade()
        runCatching {
            player?.pause()
            player?.clearMediaItems()
        }

        if (song.startsWith("episode:") && webPlayerEnabled && SpotifyWebPlayer.canPlay &&
            io.github.sekademi.spotufi.data.preferences.isWebPlaybackEnabled(appContext)
        ) {
            runCatching { player?.pause() }
            SpotifyWebPlayer.playEpisode(song.removePrefix("episode:"))
            return
        }

        val downloadedPath = io.github.sekademi.spotufi.data.preferences.downloadedPathForQuery(appContext, song)
        if (downloadedPath == null && webPlayerEnabled &&
            io.github.sekademi.spotufi.data.preferences.isWebPlaybackEnabled(appContext) &&
            SpotifyWebPlayer.canPlay
        ) {
            val spotifyId = StreamResolver.spotifyTrackIdForPlayback(song)
            if (spotifyId != null) {
                runCatching { player?.pause() }
                currentSource = "Spotify"
                currentQuality = ""
                updateResolveStatus(true, "Loading Spotify track...")
                SpotifyWebPlayer.play(spotifyId)
                scope.launch {
                    delay(1500)
                    updateResolveStatus(false)
                }
                return
            }
            Log.w(TAG, "web playback on but no Spotify id for query: $song — using fallback engine")
        }

        scope.launch {
            try {
                val streamUrl = resolveStreamUrl(song, appContext, forPlayback = true) ?: run {
                    if (currentRequest == song) withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            appContext, "Couldn't find a playable stream for this track",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                    if (boundState?.resolveError?.value == null) {
                        boundState?.updateResolveError("No stream found")
                    }
                    updateResolveStatus(false)
                    return@launch
                }
                if (currentRequest != song) {
                    updateResolveStatus(false)
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    if (currentRequest != song) {
                        updateResolveStatus(false)
                        return@withContext
                    }
                    ensurePlayer(appContext)
                    player!!.setMediaItem(buildMediaItem(streamUrl))
                    player!!.prepare()
                    if (song == restoreQuery && restorePositionMs > 0) {
                        player!!.seekTo(restorePositionMs)
                    }
                    restoreQuery = null
                    player!!.playWhenReady = playWhenResolved
                    loadedQuery = song
                    updateResolveStatus(false)
                }
                startPositionWatch()
            } catch (e: Exception) {
                Log.e(TAG, "playSong failed for query: $song", e)
                boundState?.updateResolveError(e.message ?: "Playback failed")
                updateResolveStatus(false)
            }
        }
    }

    fun prefetch(song: String, context: Context) {
        if (song.isBlank()) return
        val appContext = context.applicationContext
        if (webPlaybackActive()) return
        scope.launch {
            val url = runCatching { resolveStreamUrl(song, appContext) }.getOrNull()
            if (url != null) StreamResolver.cacheIntro(url, appContext)
        }
    }

    fun prefetchList(songs: List<String>, context: Context, count: Int = 4) {
        // Intentionally no-op: don't resolve streams for whole result lists.
    }

    fun play() {
        if (webPlaybackActive()) { SpotifyWebPlayer.resume(); return }
        playWhenResolved = true
        if (currentRequest.isNotBlank() && currentRequest != loadedQuery) {
            return
        }
        if ((player?.mediaItemCount ?: 0) == 0) {
            val q = restoreQuery
            val ctx = appCtx
            if (q != null && ctx != null) { playSong(q, ctx); return }
        }
        player?.play()
    }

    fun pause() {
        cancelCrossfade()
        playWhenResolved = false
        if (webPlaybackActive()) { SpotifyWebPlayer.pause(); return }
        player?.let {
            it.playWhenReady = false
            appCtx?.let { ctx ->
                val pos = it.currentPosition
                if (pos > 0) io.github.sekademi.spotufi.data.preferences.saveLastPosition(ctx, pos)
            }
        }
    }

    fun stop() {
        cancelCrossfade()
        player?.stop()
        loadedQuery = null
        currentRequest = ""
    }

    fun seekTo(position: Long) {
        cancelCrossfade()
        if (webPlaybackActive()) { SpotifyWebPlayer.seekTo(position); return }
        player?.seekTo(position)
    }

    fun release() {
        CrossfadeEngine.release()
        player?.release()
        player = null
        loadedQuery = null
        currentRequest = ""
    }

    fun getDuration(): Long {
        if (webPlaybackActive()) return SpotifyWebPlayer.durationMs
        return player?.duration ?: 0L
    }

    fun getCurrentPosition(): Long {
        if (webPlaybackActive()) return SpotifyWebPlayer.positionMs
        return player?.currentPosition ?: 0L
    }

    fun isPrepared(): Boolean {
        val playerState = player?.playbackState
        return playerState != null && playerState != ExoPlayer.STATE_IDLE && playerState != ExoPlayer.STATE_ENDED
    }

    // ── Sleep timer ──
    @Volatile private var sleepJob: kotlinx.coroutines.Job? = null
    @Volatile var sleepTimerEndAt: Long = 0L
        private set

    fun setSleepTimer(durationMillis: Long) {
        sleepJob?.cancel()
        if (durationMillis <= 0L) {
            sleepTimerEndAt = 0L
            return
        }
        sleepTimerEndAt = System.currentTimeMillis() + durationMillis
        sleepJob = scope.launch {
            kotlinx.coroutines.delay(durationMillis)
            withContext(Dispatchers.Main) { pause() }
            sleepTimerEndAt = 0L
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepTimerEndAt = 0L
    }
}
