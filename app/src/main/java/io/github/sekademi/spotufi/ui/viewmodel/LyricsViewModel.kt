package io.github.sekademi.spotufi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sekademi.spotufi.data.api.LyricsApi
import io.github.sekademi.spotufi.data.entity.Lyrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor() : ViewModel() {

    sealed class State {
        object Loading : State()
        data class Loaded(val lyrics: Lyrics) : State()
        object NotFound : State()
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    private var loadedKey: String? = null

    fun load(title: String, artist: String, album: String, durationSec: Int) {
        val key = "$title|$artist"
        if (loadedKey == key && _state.value !is State.NotFound) return
        loadedKey = key
        _state.value = State.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val lyrics = LyricsApi.fetch(title, artist, album, durationSec)
            withContext(Dispatchers.Main) {
                _state.value = if (lyrics == null || lyrics.isEmpty) State.NotFound
                else State.Loaded(lyrics)
            }
        }
    }
}
