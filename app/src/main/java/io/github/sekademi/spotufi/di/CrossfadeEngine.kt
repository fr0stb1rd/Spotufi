package io.github.sekademi.spotufi.di

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Handles crossfade and DJ-style mixing between tracks.
 * Extracted from SongPlayer to isolate the crossfade logic.
 */
object CrossfadeEngine {
    private const val TAG = "CrossfadeEngine"
    private const val CF_LPF_START_HZ = 20000f
    private const val CF_LPF_END_HZ = 200f
    private const val CF_HPF_START_HZ = 2000f
    private const val CF_HPF_END_HZ = 20f
    private const val CF_SIGMOID_K = 6f

    @Volatile private var appCtx: Context? = null
    @Volatile private var boundState: CurrentSongState? = null
    @Volatile private var currentPlayerFilter: io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor? = null
    @Volatile private var secondaryPlayer: ExoPlayer? = null
    @Volatile private var secondaryPlayerFilter: io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor? = null
    @Volatile var isCrossfading = false
        private set
    @Volatile private var crossfadeJob: kotlinx.coroutines.Job? = null
    @Volatile private var positionWatchJob: kotlinx.coroutines.Job? = null

    /** Notified (on the main thread) when the active ExoPlayer instance changes after a
     *  crossfade, so the media session can re-bind to the promoted player. */
    @Volatile var onPlayerSwapped: ((ExoPlayer) -> Unit)? = null

    private var posSaveTick = 0

    fun bindState(state: CurrentSongState) { boundState = state }

    /** Bind the primary player's audio filter so DJ-mode crossfade can apply
     *  low-pass sweep on the outgoing track. Called from SongPlayer.ensurePlayer(). */
    fun bindPrimaryFilter(filter: io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor?) {
        currentPlayerFilter = filter
    }

    fun isCrossfadeActive(): Boolean = isCrossfading

    private fun sigmoid(t: Float): Float = 1.0f / (1.0f + exp(-CF_SIGMOID_K * (t - 0.5f)))

    private fun expInterpolate(start: Float, end: Float, t: Float): Float {
        if (start <= 0f || end <= 0f) return end
        return exp(ln(start) + (ln(end) - ln(start)) * t)
    }

    fun cancelCrossfade() {
        if (!isCrossfading && secondaryPlayer == null) return
        crossfadeJob?.cancel()
        crossfadeJob = null
        currentPlayerFilter?.enabled = false
        secondaryPlayerFilter?.enabled = false
        runCatching { secondaryPlayer?.release() }
        secondaryPlayer = null
        secondaryPlayerFilter = null
        SongPlayer.exoPlayer?.volume = 1f
        isCrossfading = false
    }

    fun startPositionWatch(scope: CoroutineScope) {
        positionWatchJob?.cancel()
        positionWatchJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(250)
                val ctx = appCtx ?: continue
                if (++posSaveTick % 12 == 0 && !SongPlayer.webPlaybackActive()) {
                    SongPlayer.exoPlayer?.let { p ->
                        val pos = withContext(Dispatchers.Main) {
                            if (p.isPlaying) p.currentPosition else -1L
                        }
                        if (pos > 0) io.github.sekademi.spotufi.data.preferences.saveLastPosition(ctx, pos)
                    }
                }
                if (isCrossfading) continue
                val crossfadeMs = io.github.sekademi.spotufi.data.preferences.getCrossfadeMs(ctx)
                if (crossfadeMs <= 0) continue
                val state = boundState ?: continue
                if (state.repeat.value != RepeatMode.OFF) continue
                val p = SongPlayer.exoPlayer ?: continue
                val playing = withContext(Dispatchers.Main) { p.isPlaying }
                if (!playing) continue
                val dur = withContext(Dispatchers.Main) { p.duration }
                val pos = withContext(Dispatchers.Main) { p.currentPosition }
                if (dur <= 0 || pos < 0) continue
                if (pos >= dur - crossfadeMs) {
                    triggerCrossfade(ctx, crossfadeMs, scope)
                }
            }
        }
    }

    fun triggerCrossfade(ctx: Context, configuredMs: Int, scope: CoroutineScope) {
        if (isCrossfading) return
        val state = boundState ?: return
        val q = state.queue.value
        if (q.isEmpty()) return
        val cur = q.indexOfFirst { it.id == state.songId.value }
        if (cur < 0 || cur >= q.size - 1) return
        val nextSong = q[cur + 1]
        isCrossfading = true
        scope.launch {
            try {
                val nextUrl = SongPlayer.resolveStreamUrlPublic(nextSong.url, ctx, forPlayback = true) ?: run {
                    isCrossfading = false; return@launch
                }
                val remaining = withContext(Dispatchers.Main) {
                    val p = SongPlayer.exoPlayer ?: return@withContext configuredMs.toLong()
                    val d = p.duration; val ps = p.currentPosition
                    if (d > 0 && ps >= 0) (d - ps) else configuredMs.toLong()
                }
                val effectiveMs = minOf(configuredMs.toLong(), remaining).coerceAtLeast(1000L).toInt()
                val djMode = io.github.sekademi.spotufi.data.preferences.isCrossfadeDjMode(ctx)

                withContext(Dispatchers.Main) {
                    state.updateSongState(
                        nextSong.coverUri, nextSong.title, nextSong.singer, true,
                        nextSong.id, cur + 1, nextSong.album,
                    )
                    val (sp, sf) = SongPlayer.createPlayerWithFilterPublic(ctx, handleAudioFocus = false)
                    secondaryPlayer = sp
                    secondaryPlayerFilter = sf
                    sp.setMediaItem(SongPlayer.buildMediaItemPublic(nextUrl))
                    sp.prepare()
                    sp.volume = 0f
                    sp.playWhenReady = true
                }
                performCrossfade(effectiveMs, djMode, scope)
            } catch (e: Exception) {
                Log.e(TAG, "crossfade failed", e)
                cancelCrossfade()
            }
        }
    }

    private suspend fun performCrossfade(effectiveMs: Int, djMode: Boolean, scope: CoroutineScope) {
        val steps = 50
        val delayPerStep = (effectiveMs / steps).coerceAtLeast(20)
        if (djMode) {
            currentPlayerFilter?.apply {
                filterType = io.github.sekademi.spotufi.audio.BiquadFilter.FilterType.LOW_PASS
                cutoffFrequencyHz = CF_LPF_START_HZ; enabled = true
            }
            secondaryPlayerFilter?.apply {
                filterType = io.github.sekademi.spotufi.audio.BiquadFilter.FilterType.HIGH_PASS
                cutoffFrequencyHz = CF_HPF_START_HZ; enabled = true
            }
        }
        crossfadeJob?.cancel()
        val job = scope.launch {
            try {
                for (step in 0..steps) {
                    if (!isActive) break
                    val progress = step.toFloat() / steps
                    val angle = (progress * PI / 2).toFloat()
                    withContext(Dispatchers.Main) {
                        SongPlayer.exoPlayer?.volume = cos(angle)
                        secondaryPlayer?.volume = sin(angle)
                        if (djMode) {
                            val fp = sigmoid(progress)
                            currentPlayerFilter?.cutoffFrequencyHz = expInterpolate(CF_LPF_START_HZ, CF_LPF_END_HZ, fp)
                            secondaryPlayerFilter?.cutoffFrequencyHz = expInterpolate(CF_HPF_START_HZ, CF_HPF_END_HZ, fp)
                        }
                    }
                    kotlinx.coroutines.delay(delayPerStep.toLong())
                }
                finalizeCrossfade()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            }
        }
        crossfadeJob = job
        job.join()
    }

    private suspend fun finalizeCrossfade() {
        withContext(Dispatchers.Main) {
            val incoming = secondaryPlayer ?: run { isCrossfading = false; return@withContext }
            currentPlayerFilter?.enabled = false
            secondaryPlayerFilter?.enabled = false
            SongPlayer.promotePlayer(incoming, secondaryPlayerFilter)
            secondaryPlayer = null
            secondaryPlayerFilter = null
            incoming.volume = 1f
            incoming.setAudioAttributes(SongPlayer.buildAudioAttributesPublic(), true)
            incoming.setHandleAudioBecomingNoisy(true)
            isCrossfading = false
            onPlayerSwapped?.invoke(incoming)
        }
        startPositionWatch(SongPlayer.scope)
    }

    fun init(appContext: Context, state: CurrentSongState) {
        appCtx = appContext
        boundState = state
    }

    fun release() {
        positionWatchJob?.cancel()
        cancelCrossfade()
    }
}
