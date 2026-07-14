package io.github.sekademi.spotufi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.entity.AlbumsModel
import io.github.sekademi.spotufi.data.entity.ArtistsModel
import io.github.sekademi.spotufi.data.entity.HomeFeedModel
import io.github.sekademi.spotufi.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: AppRepository)  : ViewModel() {


    private val _albums : MutableStateFlow<Response<List<AlbumsModel>>> = MutableStateFlow(Response.Loading())
    val albums : StateFlow<Response<List<AlbumsModel>>> = _albums

    private val _artists : MutableStateFlow<Response<List<ArtistsModel>>> = MutableStateFlow(Response.Loading())
    val artists : StateFlow<Response<List<ArtistsModel>>> = _artists

    private val _home : MutableStateFlow<Response<HomeFeedModel>> = MutableStateFlow(Response.Loading())
    val home : StateFlow<Response<HomeFeedModel>> = _home


    init {
        fetchHome()
        fetchArtists()
        fetchAlbums()
    }

    private fun fetchHome() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideHomeFeed().collect { feed ->
            _home.value = feed
        }
    }

    private fun fetchAlbums() = viewModelScope.launch(Dispatchers.IO) {
            repository.provideAlbums().collect{ album ->
                _albums.value = album
            }
    }

    private fun fetchArtists() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideArtists().collect { artist ->
            _artists.value = artist
        }
    }



}