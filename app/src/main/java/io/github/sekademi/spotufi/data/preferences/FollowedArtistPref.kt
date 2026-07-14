package io.github.sekademi.spotufi.data.preferences

import android.content.Context

// Local record of artists followed from the app (keyed by Spotify artist id),
// so the Follow button keeps its state across visits. The follow itself is also
// mirrored to the real Spotify account via SpotifySync.

fun addFollowedArtist(context: Context, artistId: String, name: String) {
    if (artistId.isBlank()) return
    context.getSharedPreferences("FollowedArtists", Context.MODE_PRIVATE)
        .edit().putString(artistId, name).apply()
}

fun removeFollowedArtist(context: Context, artistId: String) {
    if (artistId.isBlank()) return
    context.getSharedPreferences("FollowedArtists", Context.MODE_PRIVATE)
        .edit().remove(artistId).apply()
}

fun isArtistFollowed(context: Context, artistId: String): Boolean =
    artistId.isNotBlank() &&
        context.getSharedPreferences("FollowedArtists", Context.MODE_PRIVATE).contains(artistId)
