package io.github.sekademi.spotufi.ui.viewmodel

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.entity.ArtistOverviewModel
import io.github.sekademi.spotufi.data.entity.ArtistsModel
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
class ArtistViewModel @Inject constructor(private val repository: AppRepository, private val currentSongState: CurrentSongState) : ViewModel() {

    val currentSongPlayingState: State<Boolean> get() = currentSongState.playingState
    val currentSongId: State<Int> get() = currentSongState.songId


    private val _songs : MutableStateFlow<Response<List<SongsModel>>> = MutableStateFlow(Response.Loading())
    val songs : StateFlow<Response<List<SongsModel>>> = _songs

    private val _artists : MutableStateFlow<Response<List<ArtistsModel>>> = MutableStateFlow(Response.Loading())
    val artists : StateFlow<Response<List<ArtistsModel>>> = _artists

    private val _overview : MutableStateFlow<Response<ArtistOverviewModel>> = MutableStateFlow(Response.Loading())
    val overview : StateFlow<Response<ArtistOverviewModel>> = _overview

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    fun addAllToQueue(songs: List<SongsModel>) = currentSongState.addAllToQueue(songs)

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId :Int, songIndex : Int = 0, album : String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    val likeState = currentSongState.likeState

    fun updateLikeState(likeState : Boolean){
        currentSongState.updateLikeState(likeState)
    }

    private var loadedArtist: String? = null

    // Ids of artists the user follows on Spotify, so the Follow button reflects
    // the real account state (not just follows made from this app).
    private val _followedArtistIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
    val followedArtistIds: StateFlow<Set<String>> = _followedArtistIds

    init {
        fetchArtists()
        viewModelScope.launch(Dispatchers.IO) {
            _followedArtistIds.value = repository.provideFollowedArtists()
                .mapNotNull { it.id.ifBlank { null } }
                .toSet()
        }
    }

    // Loads this artist's top tracks (GraphQL, not rate-limited). Called by the
    // screen with the artist name it was opened for.
    fun loadArtistSongs(artistName: String) {
        if (loadedArtist == artistName) return
        loadedArtist = artistName
        viewModelScope.launch(Dispatchers.IO) {
            repository.provideArtistSongs(artistName).collect { response ->
                _songs.value = if (response is Response.Success) {
                    Response.Success(response.data.distinctBy { it.id })
                } else {
                    response
                }
            }
        }
    }

    private var loadedOverview: String? = null

    // Loads the full Spotify-style artist page (header, monthly listeners, bio,
    // popular tracks, discography, related artists) in one GQL round-trip.
    // Passing the exact Spotify artist id avoids the fuzzy name search.
    fun loadArtistOverview(artistName: String, artistId: String = "") {
        val key = artistId.ifBlank { artistName }
        if (loadedOverview == key) return
        loadedOverview = key
        viewModelScope.launch(Dispatchers.IO) {
            repository.provideArtistOverview(artistName, artistId).collect { overview ->
                _overview.value = overview
                // Keep the legacy songs flow in sync (popular tracks) so any
                // other consumer and the play/queue helpers still work.
                if (overview is Response.Success) {
                    _songs.value = Response.Success(overview.data.topTracks.map { it.song })
                }
            }
        }
    }

    private fun fetchArtists() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideArtists().collect{ artists ->
            _artists.value = artists
        }
    }
}