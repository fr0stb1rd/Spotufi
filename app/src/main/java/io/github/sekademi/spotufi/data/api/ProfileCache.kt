package io.github.sekademi.spotufi.data.api

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.spotify.Spotify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-cached Spotify profile (display name + avatar) for the Home header,
 * fetched once per session via the `profileAttributes` GQL op (`Spotify.me()`).
 * Compose state so the avatar recomposes when the fetch lands.
 */
object ProfileCache {
    var name by mutableStateOf<String?>(null)
        private set
    var imageUrl by mutableStateOf<String?>(null)
        private set

    private val fetching = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensure(context: Context) {
        if (name != null) return
        if (!fetching.compareAndSet(false, true)) return
        scope.launch {
            try {
                if (SpotifyTokenProvider.ensureToken(context)) {
                    Spotify.me().onSuccess { user ->
                        name = user.displayName ?: user.id
                        imageUrl = user.images.firstOrNull()?.url
                    }
                }
            } finally {
                fetching.set(false)
            }
        }
    }
}
