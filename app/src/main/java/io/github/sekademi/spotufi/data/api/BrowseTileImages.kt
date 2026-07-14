package io.github.sekademi.spotufi.data.api

import android.content.Context
import com.metrolist.spotify.Spotify
import java.util.concurrent.ConcurrentHashMap

/**
 * Cover art for the Search "Browse all" tiles, like Spotify web shows: each
 * category box carries the cover of its top playlist, tilted in the corner.
 * Resolved lazily (one 1-item playlist search per category) and cached for the
 * app session.
 */
object BrowseTileImages {

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun coverFor(context: Context, genre: String): String {
        cache[genre]?.let { return it }
        if (!SpotifyTokenProvider.ensureToken(context.applicationContext)) return ""
        val url = Spotify.search(genre, types = listOf("playlist"), limit = 1).getOrNull()
            ?.playlists?.items?.firstOrNull()?.images?.firstOrNull()?.url.orEmpty()
        if (url.isNotBlank()) cache[genre] = url
        return url
    }
}
