package io.github.sekademi.spotufi

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.sekademi.spotufi.ui.navigation.MainBottomNavigation
import io.github.sekademi.spotufi.ui.navigation.MyNavHost
import io.github.sekademi.spotufi.ui.navigation.Routes
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay


@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun App() {
    val bottomBarState = rememberSaveable { (mutableStateOf(true)) }
    val bottomBarPlayerState = rememberSaveable { (mutableStateOf(true)) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val playerViewModel: io.github.sekademi.spotufi.ui.viewmodel.PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.currentSongTitle
    var lastRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentRoute, playerState) {
        if (currentRoute != Routes.Player.route) {
            bottomBarState.value = when (currentRoute) {
                Routes.Login.route, Routes.Queue.route -> false
                else -> true
            }
            bottomBarPlayerState.value = when (currentRoute) {
                Routes.Login.route, Routes.Queue.route -> false
                else -> playerState.isNotEmpty()
            }
        }
        lastRoute = currentRoute
    }

    Scaffold(
        modifier = Modifier
            .navigationBarsPadding()
        ,
        bottomBar = {
            MainBottomNavigation(navController = navController, bottomBarState = bottomBarState, bottomBarPlayerState)
        }
    ) {
        MyNavHost(navHostController = navController)
    }
}


