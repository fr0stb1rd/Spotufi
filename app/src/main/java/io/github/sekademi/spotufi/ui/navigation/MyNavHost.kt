package io.github.sekademi.spotufi.ui.navigation

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import io.github.sekademi.spotufi.data.api.SpotifySession
import io.github.sekademi.spotufi.ui.screens.SpotifyLoginScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import androidx.compose.ui.window.DialogProperties
import io.github.sekademi.spotufi.ui.screens.AlbumScreen
import io.github.sekademi.spotufi.ui.screens.ArtistReleasesScreen
import io.github.sekademi.spotufi.ui.screens.ArtistScreen
import io.github.sekademi.spotufi.ui.screens.CategoryScreen
import io.github.sekademi.spotufi.ui.screens.DownloadsScreen
import io.github.sekademi.spotufi.ui.screens.HistoryScreen
import io.github.sekademi.spotufi.ui.screens.HomeScreen
import io.github.sekademi.spotufi.ui.screens.LibraryScreen
import io.github.sekademi.spotufi.ui.screens.LikedSongsScreen
import io.github.sekademi.spotufi.ui.screens.PlayerScreen
import io.github.sekademi.spotufi.ui.screens.PlaylistScreen
import io.github.sekademi.spotufi.ui.screens.ShowScreen
import io.github.sekademi.spotufi.ui.screens.QueueScreen
import io.github.sekademi.spotufi.ui.screens.SearchScreen
import io.github.sekademi.spotufi.ui.screens.SettingsScreen
import io.github.sekademi.spotufi.ui.viewmodel.PlayerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun MyNavHost(
    navHostController: NavHostController
) {

    val playerViewModel : PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.currentSongTitle

    Log.d("player", playerState)

    val context = LocalContext.current
    // First launch (no Spotify session) lands on the login screen.
    val startDestination = if (SpotifySession.spDc(context).isBlank()) {
        Routes.Login.route
    } else {
        Routes.Home.route
    }

    // Restore the last session: put the track back into the mini player (paused),
    // restore the queue and repeat mode, and arm the player to resume from the
    // saved position on the first play tap.
    LaunchedEffect(Unit) {
        if (playerViewModel.currentSongTitle.value.isBlank()) {
            io.github.sekademi.spotufi.data.preferences.loadRestorePoint(context)?.let { point ->
                playerViewModel.updateQueue(point.queue)
                playerViewModel.updateSongState(
                    point.song.coverUri, point.song.title, point.song.singer,
                    false, point.song.id,
                    point.queue.indexOfFirst { it.id == point.song.id }.coerceAtLeast(0),
                    point.song.album)
                point.repeatMode?.let { playerViewModel.updateRepeatState(it) }
                io.github.sekademi.spotufi.di.SongPlayer.setRestorePoint(point.song.url, point.positionMs)
            }
        }
    }

    NavHost(
        navController = navHostController,
        startDestination = startDestination,
        // Quick fade between screens instead of the default slide/scale animations.
        enterTransition = { fadeIn(animationSpec = tween(150)) },
        exitTransition = { fadeOut(animationSpec = tween(150)) },
        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
        popExitTransition = { fadeOut(animationSpec = tween(150)) },
    ){
        composable(Routes.Login.route){
            SpotifyLoginScreen(navHostController)
        }
        composable(Routes.Home.route){
            HomeScreen(navHostController)
        }
        composable(Routes.Search.route){
            SearchScreen(navHostController)
        }
        composable(Routes.Library.route) {
            LibraryScreen(navHostController)
        }
        dialog(
            route = Routes.Player.route,
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = true,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false
            )
        ) {
            PlayerScreen(navHostController)
        }

        composable(Routes.Queue.route){
            QueueScreen(navHostController)
        }

        composable(Routes.Liked.route){
            LikedSongsScreen(navHostController)
        }

        composable(Routes.Downloads.route){
            DownloadsScreen(navHostController)
        }

        composable(Routes.Settings.route){
            SettingsScreen(navHostController)
        }

        composable(Routes.History.route){
            HistoryScreen(navHostController)
        }

        composable(
            "${Routes.Category.route}/{genre}?title={title}",
            arguments = listOf(navArgument("title") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val genre = navBackStackEntry.arguments?.getString("genre").orEmpty()
            val title = navBackStackEntry.arguments?.getString("title").orEmpty()
            CategoryScreen(navHostController, genre = genre, title = title.ifBlank { genre })
        }

        composable(
            "${Routes.Album.route}/{uString}?artist={artist}",
            arguments = listOf(navArgument("artist") { defaultValue = "" }),
        ) { navBackStackEntry ->
            /* Extracting the id from the route */
            val uId = navBackStackEntry.arguments?.getString("uString")
            val artist = navBackStackEntry.arguments?.getString("artist").orEmpty()
            /* We check if it's not null */
            uId?.let { id->
                AlbumScreen(navController = navHostController, albumName = id, artist = artist)
            }
        }

        composable(
            "${Routes.Playlist.route}/{pId}?name={name}",
            arguments = listOf(navArgument("name") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val pId = navBackStackEntry.arguments?.getString("pId")
            val name = navBackStackEntry.arguments?.getString("name").orEmpty()
            pId?.let { PlaylistScreen(navHostController, playlistId = it, playlistName = name) }
        }

        composable(
            "${Routes.Show.route}/{sId}?name={name}",
            arguments = listOf(navArgument("name") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val sId = navBackStackEntry.arguments?.getString("sId")
            val name = navBackStackEntry.arguments?.getString("name").orEmpty()
            sId?.let { ShowScreen(navHostController, showId = it, showName = name) }
        }

        composable("${Routes.ArtistReleases.route}/{aString}") { navBackStackEntry ->
            val aId = navBackStackEntry.arguments?.getString("aString")
            aId?.let { ArtistReleasesScreen(navHostController, it) }
        }

        composable(
            "${Routes.Artist.route}/{aString}?id={artistId}",
            arguments = listOf(navArgument("artistId") { defaultValue = "" }),
        ) { navBackStackEntry ->
            /* Extracting the id from the route */
            val aId = navBackStackEntry.arguments?.getString("aString")
            val artistId = navBackStackEntry.arguments?.getString("artistId").orEmpty()
            /* We check if it's not null */
            aId?.let { aid->
                ArtistScreen(navHostController, aid, artistId)
            }
        }
    }
}
