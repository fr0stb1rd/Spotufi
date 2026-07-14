package io.github.sekademi.spotufi.data.entity

/**
 * Personalized Spotify home feed (the `home` GQL operation): a greeting plus an
 * ordered list of titled sections ("Your top mixes", "Jump back in", "Your
 * favorite artists", …), each holding albums / artists / playlists.
 */
data class HomeFeedModel(
    val greeting: String = "",
    val sections: List<HomeSection> = emptyList(),
)

data class HomeSection(
    val title: String,
    val items: List<HomeItem>,
)

sealed class HomeItem {
    abstract val name: String
    abstract val imageUrl: String

    data class Album(
        override val name: String,
        override val imageUrl: String,
        val subtitle: String,
        val artists: String = "",
    ) : HomeItem()

    data class Artist(
        override val name: String,
        override val imageUrl: String,
        val id: String = "",
    ) : HomeItem()

    data class Playlist(
        override val name: String,
        override val imageUrl: String,
        val subtitle: String,
        val id: String = "",
    ) : HomeItem()
}
