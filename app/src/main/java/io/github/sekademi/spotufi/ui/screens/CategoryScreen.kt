package io.github.sekademi.spotufi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.entity.LibraryEntry
import io.github.sekademi.spotufi.ui.components.Loader
import io.github.sekademi.spotufi.ui.components.Snackbar
import io.github.sekademi.spotufi.ui.navigation.playlistRoute
import io.github.sekademi.spotufi.ui.viewmodel.CategoryViewModel

@Composable
fun CategoryScreen(navController: NavController, genre: String, title: String) {
    val viewModel: CategoryViewModel = hiltViewModel()
    LaunchedEffect(genre) { viewModel.load(genre) }
    val playlists by viewModel.playlists.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E13))
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp, 12.dp, 16.dp, 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { navController.popBackStack() }
                    .padding(8.dp)
            )
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        when (playlists) {
            is Response.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Loader() }
            is Response.Success -> CategoryGrid((playlists as Response.Success).data, navController)
            else -> Box(modifier = Modifier.padding(20.dp, 60.dp)) { Snackbar(showMessage = "Couldn't load $title") }
        }
    }
}

@Composable
private fun CategoryGrid(entries: List<LibraryEntry>, navController: NavController) {
    if (entries.isEmpty()) {
        Box(modifier = Modifier.padding(20.dp, 40.dp)) { Snackbar(showMessage = "Nothing here yet") }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 130.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries) { entry ->
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { navController.navigate(playlistRoute(entry.spotifyId, entry.name)) }
            ) {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp)),
                    model = entry.coverUri,
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.placeholder),
                    placeholder = painterResource(R.drawable.placeholder),
                    contentDescription = "",
                )
                Text(
                    text = entry.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = entry.subtitle,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
