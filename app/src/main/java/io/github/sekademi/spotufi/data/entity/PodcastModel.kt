package io.github.sekademi.spotufi.data.entity

/**
 * A podcast show (search result / show header). [id] is the RAW Spotify show id
 * so the detail screen can fetch its episodes. Episodes themselves are modelled
 * as [SongsModel] with url = "episode:<rawId>" so they flow through the existing
 * queue/player (SongPlayer routes that prefix to the web player's episode page).
 */
data class PodcastModel(
    val id: String,
    val name: String,
    val publisher: String,
    val coverUri: String,
) {
    constructor() : this("", "", "", "")
}
