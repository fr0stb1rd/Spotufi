package io.github.sekademi.spotufi.ui.repository

import io.github.sekademi.spotufi.data.api.Api
import javax.inject.Inject

class AppRepository @Inject constructor(private val api : Api) {

    suspend fun provideAlbums() = api.getAlbums()

    suspend fun provideHomeFeed() = api.getHomeFeed()

    suspend fun provideArtists() = api.getArtists()

    suspend fun provideSongs() = api.getSongs()

    suspend fun searchSongs(query: String) = api.searchTracks(query)

    suspend fun searchEverything(query: String) = api.searchEverything(query)

    suspend fun provideAlbumSongs(albumName: String, artist: String = "") = api.getAlbumSongs(albumName, artist)

    suspend fun provideArtistSongs(artistName: String) = api.getArtistSongs(artistName)

    suspend fun provideArtistOverview(artistName: String, artistId: String = "") = api.getArtistOverview(artistName, artistId)

    suspend fun providePlaylistSongs(playlistId: String) = api.getPlaylistSongs(playlistId)

    suspend fun providePlaylist(playlistId: String) = api.getPlaylist(playlistId)

    suspend fun provideShowEpisodes(showId: String) = api.getShowEpisodes(showId)

    suspend fun provideShow(showId: String) = api.getShow(showId)

    suspend fun provideLibrary() = api.getLibrary()

    suspend fun provideFollowedArtists() = api.getFollowedArtists()

    suspend fun provideCategoryPlaylists(genre: String) = api.getCategoryPlaylists(genre)

    suspend fun provideRecommendations(seedTrackIds: List<String>) = api.getRecommendations(seedTrackIds)

    suspend fun provideLikedSongs() = api.getLikedSongs()

    suspend fun provideAccount() = api.getAccount()

    suspend fun provideCanvasUrl(trackId: String) = api.getCanvasUrl(trackId)
}