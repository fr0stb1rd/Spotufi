package io.github.sekademi.spotufi.di

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.sekademi.spotufi.data.entity.SongsModel
import javax.inject.Inject
import javax.inject.Singleton

enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

@Singleton
class CurrentSongState @Inject constructor() {
    private val _title: MutableState<String> = mutableStateOf("")
    val title: State<String> get() = _title

    private val _album: MutableState<String> = mutableStateOf("")
    val album : State<String> get() = _album

    private val _singer: MutableState<String> = mutableStateOf("")
    val singer: State<String> get() = _singer

    private val _coverUri: MutableState<String> = mutableStateOf("")
    val coverUri: State<String> get() = _coverUri

    private val _playingState: MutableState<Boolean> = mutableStateOf(false)
    val playingState: State<Boolean> get() = _playingState

    private val _songIndex: MutableState<Int> = mutableStateOf(0)
    val songIndex : State<Int> get() = _songIndex

    private val _songId: MutableState<Int> = mutableStateOf(0)
    val songId : State<Int> get() = _songId

    private val _songUrl: MutableState<String> = mutableStateOf("")
    val songUrl: State<String> get() = _songUrl

    fun setSongUrl(url: String) { _songUrl.value = url }

    private val _artistIds: MutableState<String> = mutableStateOf("")
    val artistIds: State<String> get() = _artistIds

    fun addAllToQueue(songs: List<SongsModel>) {
        val existing = _queue.value
        val existingIds = existing.map { it.id }.toSet()
        val fresh = songs.filter { it.id !in existingIds }
        if (fresh.isNotEmpty()) updateQueue(existing + fresh)
    }

    // The actual list the user is playing (album tracks, search results, liked
    // songs…). Next/previous operate on THIS, not on a re-derived global feed.
    private val _queue: MutableState<List<SongsModel>> = mutableStateOf(emptyList())
    val queue: State<List<SongsModel>> get() = _queue

    fun updateQueue(songs: List<SongsModel>) {
        val deduped = songs.distinctBy { it.id }
        if (_queue.value.map { it.id } == deduped.map { it.id }) return
        _queue.value = deduped
        SongPlayer.registerLossless(songs.map { it.url to it.spotifyTrackId })
        SongPlayer.registerAlternativeKeys(songs.map {
            it.url to io.github.sekademi.spotufi.data.preferences.alternativeStreamKey(it)
        })
        SongPlayer.registerExplicit(songs.map { it.url to it.explicit })
        SongPlayer.registerDuration(songs.mapNotNull { s -> if (s.durationMs > 0) s.url to s.durationMs else null })
        SongPlayer.registerMetadata(songs.map {
            it.url to CandidateScorer.TrackMatchMetadata(
                title = it.title,
                artist = it.singer,
                album = it.album,
            )
        })
        io.github.sekademi.spotufi.data.api.LyricsApi.registerTracks(songs)
        io.github.sekademi.spotufi.data.preferences.saveQueue(
            io.github.sekademi.spotufi.MyApplication.instance, songs)
    }

    val shuffle = mutableStateOf(false)
    val repeat = mutableStateOf(RepeatMode.OFF)
    val likeState = mutableStateOf(false)

    // Original queue order, kept while shuffle is on so turning it off restores
    // the list instead of leaving it permanently scrambled.
    private var unshuffledQueue: List<SongsModel>? = null

    /**
     * Toggling shuffle reorders the queue ONCE: the current track stays where it
     * is and the rest follow in random order. (Skipping used to re-shuffle the
     * whole list on every tap, which could repeat or skip songs.)
     */
    fun updateShuffleState(newShuffleState: Boolean) {
        if (newShuffleState == shuffle.value) return
        shuffle.value = newShuffleState
        val q = _queue.value
        if (newShuffleState) {
            unshuffledQueue = q
            val curIdx = q.indexOfFirst { it.id == _songId.value }
            if (curIdx >= 0) {
                _queue.value = listOf(q[curIdx]) +
                    q.filterIndexed { i, _ -> i != curIdx }.shuffled()
                _songIndex.value = 0
            } else {
                _queue.value = q.shuffled()
            }
        } else {
            val original = unshuffledQueue
            unshuffledQueue = null
            // Restore only if we're still inside that queue (it may have been
            // replaced by another list while shuffled). Keep tracks appended in
            // the meantime (queue edits, autoplay radio).
            if (original != null && original.any { it.id == _songId.value }) {
                val appended = q.filter { s -> original.none { it.id == s.id } }
                val restored = original + appended
                _queue.value = restored
                val idx = restored.indexOfFirst { it.id == _songId.value }
                if (idx >= 0) _songIndex.value = idx
            }
        }
    }

    /**
     * Starts shuffled playback of a full list (the shuffle button on
     * playlist/album/liked screens): the queue becomes a shuffled copy, shuffle
     * turns on, and the caller plays the returned first track.
     */
    fun startShuffled(songs: List<SongsModel>): SongsModel? {
        if (songs.isEmpty()) return null
        updateQueue(songs.shuffled())
        unshuffledQueue = songs
        shuffle.value = true
        return _queue.value.firstOrNull()
    }
    fun updateRepeatState(newRepeatState : RepeatMode){
        repeat.value = newRepeatState
        io.github.sekademi.spotufi.data.preferences.saveRepeatMode(
            io.github.sekademi.spotufi.MyApplication.instance, newRepeatState)
    }

    private val _isResolving: MutableState<Boolean> = mutableStateOf(false)
    val isResolving: State<Boolean> get() = _isResolving

    private val _resolveStatus: MutableState<String> = mutableStateOf("")
    val resolveStatus: State<String> get() = _resolveStatus

    private val _isBuffering: MutableState<Boolean> = mutableStateOf(false)
    val isBuffering: State<Boolean> get() = _isBuffering

    private val _resolveError: MutableState<String?> = mutableStateOf(null)
    val resolveError: State<String?> get() = _resolveError

    fun updateResolveError(error: String?) {
        _resolveError.value = error
    }

    fun updateResolveState(isResolving: Boolean, status: String = "") {
        _isResolving.value = isResolving
        _resolveStatus.value = status
        if (isResolving) {
            _resolveError.value = null
        }
    }

    fun updateBufferingState(isBuffering: Boolean) {
        _isBuffering.value = isBuffering
    }

    /** Sync the play/pause state without touching the rest of the now-playing
     *  metadata — used to reflect the web player's real state (e.g. after the
     *  system notification's pause button) back into the in-app UI. */
    fun updatePlayingState(playing: Boolean) {
        _playingState.value = playing
    }

    fun updateLikeState(newLikeState : Boolean){
        likeState.value = newLikeState
    }

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId : Int, songIndex : Int, album : String) {
        _coverUri.value = coverUri
        _title.value = title
        _album.value = album
        _singer.value = singer
        _playingState.value = playingState
        _songIndex.value = songIndex
        _songId.value = songId
        _artistIds.value = _queue.value.firstOrNull { it.id == songId }?.artistIds.orEmpty()
        // Feed the system media notification (MediaSession) with the current track.
        SongPlayer.setNowPlayingMeta(title, singer, coverUri)
        // Persist the current track so a fresh launch can restore the session.
        if (playingState && title.isNotBlank()) {
            _queue.value.firstOrNull { it.id == songId }?.let { track ->
                io.github.sekademi.spotufi.data.preferences.saveLastPlayback(
                    io.github.sekademi.spotufi.MyApplication.instance, track)
            }
            // Prefetch the next track in the queue to make transitions seamless.
            val q = _queue.value
            if (q.isNotEmpty() && songIndex >= 0) {
                val nextIdx = songIndex + 1
                if (nextIdx < q.size) {
                    SongPlayer.prefetch(q[nextIdx].url, io.github.sekademi.spotufi.MyApplication.instance)
                }
            }
        }
        // Warm the lyrics cache in the background so they're already loaded by the
        // time the user opens the player / scrolls to the lyrics card.
        if (playingState && title.isNotBlank()) {
            io.github.sekademi.spotufi.data.api.LyricsApi.prefetch(title, singer, album)
            // Resolve the play-query URL for this track so tapping it in history
            // can re-play it.  Falls back to the stored URL (set by SongPlayer)
            // or an empty string for tracks played before this field was added.
            val trackUrl = _queue.value.firstOrNull { it.id == songId }?.url
                ?: _songUrl.value
            // Log the play into the local listening history (History & stats screen).
            io.github.sekademi.spotufi.data.preferences.addListeningHistory(
                io.github.sekademi.spotufi.MyApplication.instance,
                io.github.sekademi.spotufi.data.preferences.HistoryEntry(
                    ts = System.currentTimeMillis(),
                    songId = songId,
                    title = title,
                    singer = singer,
                    album = album,
                    image = coverUri,
                    url = trackUrl,
                ),
            )
        }
    }
}
