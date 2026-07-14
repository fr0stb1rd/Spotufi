package com.metrolist.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Podcast show + episode models from Spotify's REST Web API
 * (`/search?type=show,episode`, `/shows/{id}/episodes`). REST is used instead of
 * GQL here because the catalog endpoints return clean, stable, documented JSON
 * and work with the same web bearer token as track/artist reads.
 */
@Serializable
data class SpotifyShow(
    val id: String = "",
    val name: String = "",
    val publisher: String = "",
    val description: String = "",
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("total_episodes") val totalEpisodes: Int = 0,
    val uri: String? = null,
)

@Serializable
data class SpotifyEpisode(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("duration_ms") val durationMs: Int = 0,
    @SerialName("release_date") val releaseDate: String = "",
    val uri: String? = null,
    // Present on /shows/{id}/episodes items via a nested show; often null on search.
    val show: SpotifyShow? = null,
)

@Serializable
data class SpotifyPodcastSearchResult(
    val shows: SpotifyPaging<SpotifyShow>? = null,
    val episodes: SpotifyPaging<SpotifyEpisode>? = null,
)
