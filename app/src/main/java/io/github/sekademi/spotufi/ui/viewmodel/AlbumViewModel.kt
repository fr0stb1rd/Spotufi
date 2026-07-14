package io.github.sekademi.spotufi.ui.viewmodel

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.entity.AlbumsModel
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.di.CurrentSongState
import io.github.sekademi.spotufi.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AlbumViewModel @Inject constructor(private val repository: AppRepository, private val currentSongState: CurrentSongState) :  ViewModel() {

    val currentSongPlayingState: State<Boolean> get() = currentSongState.playingState

    val currentSongId: State<Int> get() = currentSongState.songId

    private val _songs : MutableStateFlow<Response<List<SongsModel>>> = MutableStateFlow(Response.Loading())
    val songs : StateFlow<Response<List<SongsModel>>> = _songs

    private val _albums : MutableStateFlow<Response<List<AlbumsModel>>> = MutableStateFlow(Response.Loading())
    val albums : StateFlow<Response<List<AlbumsModel>>> = _albums

    val queue: State<List<SongsModel>> get() = currentSongState.queue

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    fun addAllToQueue(songs: List<SongsModel>) = currentSongState.addAllToQueue(songs)

    fun startShuffled(songs: List<SongsModel>) = currentSongState.startShuffled(songs)

    /** Pause/resume global playback (the header play button stays visible while playing). */
    fun setPlaying(playing: Boolean) {
        if (playing) io.github.sekademi.spotufi.di.SongPlayer.play() else io.github.sekademi.spotufi.di.SongPlayer.pause()
        currentSongState.updatePlayingState(playing)
    }

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId: Int, songIndex : Int = 0, album : String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    val likeState = currentSongState.likeState

    fun updateLikeState(likeState : Boolean){
        currentSongState.updateLikeState(likeState)
    }
    init {
        fetchAlbums()
    }

    private var albumKey: String? = null

    /** Loads the tracks for a specific album (resolved via Spotify search). */
    fun loadAlbumSongs(name: String, artist: String = "") {
        val key = "$name|$artist"
        if (albumKey == key) return
        albumKey = key
        viewModelScope.launch(Dispatchers.IO) {
            repository.provideAlbumSongs(name, artist).collect { response ->
                _songs.value = if (response is Response.Success) {
                    Response.Success(response.data.distinctBy { it.id })
                } else {
                    response
                }
            }
        }
    }

    private fun fetchAlbums() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideAlbums().collect{ album ->
            _albums.value = album
        }
    }


}