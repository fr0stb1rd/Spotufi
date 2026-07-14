package io.github.sekademi.spotufi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.entity.AlbumsModel
import io.github.sekademi.spotufi.data.entity.ArtistOverviewModel
import io.github.sekademi.spotufi.data.entity.ArtistTrackUi
import io.github.sekademi.spotufi.data.entity.ArtistsModel
import io.github.sekademi.spotufi.data.preferences.addLikedSongId
import io.github.sekademi.spotufi.data.preferences.isSongLiked
import io.github.sekademi.spotufi.data.preferences.removeLikedSongId
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.components.BiographyText
import io.github.sekademi.spotufi.ui.components.Loader
import io.github.sekademi.spotufi.ui.navigation.Routes
import io.github.sekademi.spotufi.ui.navigation.albumRoute
import io.github.sekademi.spotufi.ui.navigation.artistRoute
import io.github.sekademi.spotufi.ui.theme.AppBackground
import io.github.sekademi.spotufi.ui.theme.AppPalette
import io.github.sekademi.spotufi.ui.theme.SpotifyGreen
import io.github.sekademi.spotufi.ui.components.SwipeToQueueBox
import io.github.sekademi.spotufi.ui.viewmodel.ArtistViewModel
import io.github.sekademi.spotufi.ui.viewmodel.PlayerViewModel

private fun grouped(n: Long): String =
    "%,d".format(n)

@Composable
fun ArtistScreen(navController: NavController, artistName: String, artistId: String = "") {
    val artistViewModel: ArtistViewModel = hiltViewModel()
    val overview by artistViewModel.overview.collectAsState()

    LaunchedEffect(artistName, artistId) { artistViewModel.loadArtistOverview(artistName, artistId) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        when (val state = overview) {
            is Response.Loading -> Loader()
            is Response.Error -> ArtistOverviewContent(
                navController, artistViewModel,
                ArtistOverviewModel(name = artistName), artistName,
            )
            is Response.Success -> ArtistOverviewContent(
                navController, artistViewModel, state.data, artistName,
            )
        }
    }
}

@Composable
private fun ArtistOverviewContent(
    navController: NavController,
    artistViewModel: ArtistViewModel,
    overview: ArtistOverviewModel,
    artistName: String,
) {
    val context = LocalContext.current
    val tracks = overview.topTracks
    val displayName = overview.name.ifBlank { artistName }

    // Warm the stream cache for the top tracks so the first tap plays instantly.
    LaunchedEffect(tracks) {
        if (tracks.isNotEmpty()) {
            SongPlayer.prefetchList(tracks.map { it.song.url }, context)
        }
    }

    // Followed = known locally OR followed on the real Spotify account.
    val followedIds by artistViewModel.followedArtistIds.collectAsState()
    var following by remember(overview.id, followedIds) {
        mutableStateOf(
            io.github.sekademi.spotufi.data.preferences.isArtistFollowed(context, overview.id) ||
                overview.id in followedIds
        )
    }

    fun playTrackAt(index: Int) {
        if (index !in tracks.indices) return
        val songs = tracks.map { it.song }
        artistViewModel.updateQueue(songs)
        val s = songs[index]
        SongPlayer.playSong(s.url, context)
        artistViewModel.updateSongState(s.coverUri, s.title, s.singer, true, s.id, index)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        // ── Header: big artist image with scrim + name ──
        item {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = overview.headerImage.ifBlank { overview.avatarImage },
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.placeholder),
                    placeholder = painterResource(R.drawable.placeholder),
                    contentDescription = "",
                )
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color(AppBackground.toArgb()),
                            ),
                        )
                    )
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(16.dp, 40.dp)
                        .size(26.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { navController.navigateUp() },
                )
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp, 0.dp, 16.dp, 12.dp),
                )
            }
        }

        // ── Verified + monthly listeners ──
        item {
            if (overview.verified) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_verified),
                        contentDescription = "Verified Artist",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Verified Artist", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            overview.monthlyListeners?.let {
                Text(
                    text = "${grouped(it)} monthly listeners",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp, 2.dp, 16.dp, 0.dp),
                )
            }
        }

        // ── Action row: Follow + shuffle + play ──
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            following = !following
                            // Persist locally and mirror the follow to the real
                            // Spotify account.
                            if (following) io.github.sekademi.spotufi.data.preferences.addFollowedArtist(context, overview.id, displayName)
                            else io.github.sekademi.spotufi.data.preferences.removeFollowedArtist(context, overview.id)
                            io.github.sekademi.spotufi.data.api.SpotifySync.setArtistFollowed(context, overview.id, following)
                        }
                        .padding(16.dp, 7.dp),
                    ) {
                        Text(
                            text = if (following) "Following" else "Follow",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_dots),
                        contentDescription = "",
                        tint = Color.Gray,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_shuffle),
                        contentDescription = "",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { if (tracks.isNotEmpty()) playTrackAt(tracks.indices.random()) },
                    )
                    Spacer(Modifier.width(20.dp))
                    Box(modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { playTrackAt(0) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.play_svgrepo_com),
                            contentDescription = "",
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }

        // ── Popular tracks ──
        if (tracks.isNotEmpty()) {
            item {
                Text(
                    text = "Popular",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp),
                )
            }
            itemsIndexed(tracks.take(5)) { index, item ->
                PopularTrackRow(item, index, artistViewModel) { playTrackAt(index) }
            }
        }

        // ── Popular releases (discography) — vertical list, first few + "Show all" ──
        if (overview.popularReleases.isNotEmpty()) {
            item { SectionHeader("Popular releases") }
            itemsIndexed(overview.popularReleases.take(4)) { _, album ->
                ReleaseRow(album) {
                    navController.navigate(albumRoute(album.name, album.artists.ifBlank { displayName }))
                }
            }
            if (overview.popularReleases.size > 4) {
                item {
                    ShowAllButton {
                        navController.navigate("${Routes.ArtistReleases.route}/$artistName")
                    }
                }
            }
        }

        // ── About ──
        if (!overview.biography.isNullOrBlank()) {
            item { SectionHeader("About") }
            item {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                ) {
                    Column {
                        if (overview.avatarImage.isNotBlank()) {
                            AsyncImage(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                model = overview.avatarImage,
                                contentScale = ContentScale.Crop,
                                error = painterResource(R.drawable.placeholder),
                                contentDescription = "",
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp, 12.dp, 14.dp, 4.dp),
                        ) {
                            Text(
                                text = displayName,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (overview.verified) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_verified),
                                    contentDescription = "Verified Artist",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        overview.monthlyListeners?.let {
                            Text(
                                text = "${grouped(it)} monthly listeners",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(14.dp, 4.dp, 14.dp, 4.dp),
                            )
                        }
                        BiographyText(
                            html = overview.biography.orEmpty(),
                            onLinkClick = { uri, displayText ->
                                handleBiographyLink(
                                    uri = uri,
                                    displayText = displayText,
                                    artistName = displayName,
                                    navController = navController,
                                    context = context,
                                    artistViewModel = artistViewModel,
                                )
                            },
                            modifier = Modifier.padding(14.dp, 4.dp, 14.dp, 16.dp),
                        )
                    }
                }
            }
        }

        // ── Fans also like ──
        if (overview.relatedArtists.isNotEmpty()) {
            item { SectionHeader("Fans also like") }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 0.dp),
                ) {
                    items(overview.relatedArtists.size) { i ->
                        RelatedArtistCard(overview.relatedArtists[i]) {
                            navController.navigate(artistRoute(overview.relatedArtists[i].name, overview.relatedArtists[i].id))
                        }
                    }
                }
            }
        }

        // ── Appears on ──
        if (overview.appearsOn.isNotEmpty()) {
            item { SectionHeader("Appears on") }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 0.dp),
                ) {
                    items(overview.appearsOn.size) { i ->
                        ReleaseCard(overview.appearsOn[i]) {
                            navController.navigate(albumRoute(overview.appearsOn[i].name, overview.appearsOn[i].artists))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun PopularTrackRow(
    item: ArtistTrackUi,
    index: Int,
    artistViewModel: ArtistViewModel,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val song = item.song
    var isLiked by remember { mutableStateOf(isSongLiked(context, song.id.toString())) }
    val likeState = artistViewModel.likeState.value
    LaunchedEffect(likeState) { isLiked = isSongLiked(context, song.id.toString()) }
    val titleColor =
        if (song.id == artistViewModel.currentSongId.value) Color(AppPalette.toArgb()) else Color.White
    val playerViewModel: PlayerViewModel = hiltViewModel()

    SwipeToQueueBox(song = song, onAddToQueue = { playerViewModel.addToQueue(it) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 6.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onPlay() },
        ) {
            Text(
                text = "${index + 1}",
                color = Color.Gray,
                fontSize = 15.sp,
                modifier = Modifier.width(24.dp),
            )
            AsyncImage(
                modifier = Modifier
                    .padding(8.dp, 0.dp, 12.dp, 0.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                model = song.coverUri,
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.placeholder),
                placeholder = painterResource(R.drawable.placeholder),
                contentDescription = "",
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                item.playcount?.let {
                    Text(text = grouped(it), color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                } ?: Text(text = song.singer, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            }
            Icon(
                painter = if (isLiked) painterResource(id = R.drawable.added) else painterResource(id = R.drawable.ic_add),
                contentDescription = "",
                tint = if (isLiked) Color.White else Color.Gray,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (isLiked) removeLikedSongId(context, song.id.toString())
                        else addLikedSongId(context, song.id.toString())
                        isLiked = isSongLiked(context, song.id.toString())
                        artistViewModel.updateLikeState(!artistViewModel.likeState.value)
                    },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 8.dp),
    )
}

@Composable
private fun ReleaseCard(album: AlbumsModel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .padding(4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        AsyncImage(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(6.dp)),
            model = album.coverUri,
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.placeholder),
            placeholder = painterResource(R.drawable.placeholder),
            contentDescription = "",
        )
        Spacer(Modifier.height(6.dp))
        Text(text = album.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(
            text = album.time.ifBlank { album.artists }.let { if (album.time.isNotBlank()) "${album.time} • Album" else it },
            color = Color.Gray,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReleaseRow(album: AlbumsModel, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        AsyncImage(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(4.dp)),
            model = album.coverUri,
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.placeholder),
            placeholder = painterResource(R.drawable.placeholder),
            contentDescription = "",
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = album.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            val kind = album.type.replaceFirstChar { it.uppercase() }.ifBlank { "" }
            val year = releaseYear(album)
            Text(
                text = listOf(year, kind).filter { it.isNotBlank() }.joinToString(" • "),
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ShowAllButton(onClick: () -> Unit) {
    Box(modifier = Modifier
        .padding(16.dp, 10.dp, 16.dp, 4.dp)
        .clip(RoundedCornerShape(20.dp))
        .border(1.dp, Color.Gray, RoundedCornerShape(20.dp))
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onClick() }
        .padding(20.dp, 8.dp),
    ) {
        Text(text = "Show all", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Release-year helper: Spotify release dates come as "2024" or "2024-05-17". */
private fun releaseYear(album: AlbumsModel): String = album.time.take(4)

@Composable
fun ArtistReleasesScreen(navController: NavController, artistName: String) {
    val artistViewModel: ArtistViewModel = hiltViewModel()
    val overview by artistViewModel.overview.collectAsState()

    LaunchedEffect(artistName) { artistViewModel.loadArtistOverview(artistName) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        val data = (overview as? Response.Success)?.data
        val releases = data?.popularReleases ?: emptyList()
        val featuredOn = data?.appearsOn ?: emptyList()
        // Spotify's Releases view: filter chips + a "Latest release" spotlight,
        // then releases grouped by kind, newest first.
        val albums = releases.filter { it.type == "album" || it.type == "compilation" }
        val singles = releases.filter { it.type == "single" || it.type == "ep" }
        val untyped = releases.filter { it.type.isBlank() }
        val latest = releases.maxByOrNull { it.time }
        var filter by remember { mutableStateOf("") } // "" | "albums" | "singles" | "featured"

        fun openAlbum(album: AlbumsModel) {
            navController.navigate(albumRoute(album.name, album.artists.ifBlank { artistName }))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(AppBackground.toArgb()))
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 40.dp, 16.dp, 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { navController.navigateUp() },
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Releases",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item {
                Row(modifier = Modifier.padding(12.dp, 0.dp, 12.dp, 8.dp)) {
                    ReleaseFilterChip("Albums", filter == "albums") {
                        filter = if (filter == "albums") "" else "albums"
                    }
                    Spacer(Modifier.width(8.dp))
                    ReleaseFilterChip("Singles and EPs", filter == "singles") {
                        filter = if (filter == "singles") "" else "singles"
                    }
                    if (featuredOn.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        ReleaseFilterChip("Featured on", filter == "featured") {
                            filter = if (filter == "featured") "" else "featured"
                        }
                    }
                }
            }
            if (overview is Response.Loading) {
                item { Loader() }
            }
            when (filter) {
                "albums" -> items(albums.size) { i -> ReleaseRow(albums[i]) { openAlbum(albums[i]) } }
                "singles" -> items(singles.size) { i -> ReleaseRow(singles[i]) { openAlbum(singles[i]) } }
                "featured" -> items(featuredOn.size) { i -> ReleaseRow(featuredOn[i]) { openAlbum(featuredOn[i]) } }
                else -> {
                    latest?.let {
                        item { ReleaseSectionHeader("Latest release") }
                        item { ReleaseRow(it) { openAlbum(it) } }
                    }
                    if (albums.isNotEmpty()) {
                        item { ReleaseSectionHeader("Albums") }
                        items(albums.size) { i -> ReleaseRow(albums[i]) { openAlbum(albums[i]) } }
                    }
                    if (singles.isNotEmpty()) {
                        item { ReleaseSectionHeader("Singles and EPs") }
                        items(singles.size) { i -> ReleaseRow(singles[i]) { openAlbum(singles[i]) } }
                    }
                    if (untyped.isNotEmpty()) {
                        if (albums.isEmpty() && singles.isEmpty()) {
                            item { ReleaseSectionHeader("Popular releases") }
                        }
                        items(untyped.size) { i -> ReleaseRow(untyped[i]) { openAlbum(untyped[i]) } }
                    }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@Composable
private fun ReleaseSectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 8.dp),
    )
}

@Composable
private fun ReleaseFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color.White else Color(0xFF2A2A2A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(14.dp, 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RelatedArtistCard(artist: ArtistsModel, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(130.dp)
            .padding(4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        AsyncImage(
            modifier = Modifier
                .size(122.dp)
                .clip(CircleShape),
            model = artist.coverUri,
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.placeholder),
            placeholder = painterResource(R.drawable.placeholder),
            contentDescription = "",
        )
        Spacer(Modifier.height(6.dp))
        Text(text = artist.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(text = "Artist", color = Color.Gray, fontSize = 11.sp)
    }
}

private fun handleBiographyLink(
    uri: String,
    displayText: String,
    artistName: String,
    navController: NavController,
    context: android.content.Context,
    artistViewModel: ArtistViewModel,
) {
    val prefix = uri.substringBeforeLast(":")
    val entityId = uri.substringAfterLast(":")
    if (entityId.isBlank()) return

    when {
        uri.startsWith("spotify:artist:") -> {
            navController.navigate(artistRoute(displayText, entityId))
        }
        uri.startsWith("spotify:album:") -> {
            navController.navigate(albumRoute(displayText, artistName))
        }
        uri.startsWith("spotify:track:") -> {
            val url = "spotify:track:$entityId|$displayText $artistName"
            val song = SongsModel(
                id = entityId.hashCode().let { if (it < 0) -it else it },
                title = displayText,
                album = artistName,
                singer = artistName,
                coverUri = "",
                url = url,
                spotifyTrackId = entityId,
            )
            artistViewModel.updateQueue(listOf(song))
            artistViewModel.updateSongState(
                coverUri = song.coverUri,
                title = song.title,
                singer = song.singer,
                playingState = true,
                songId = song.id,
                songIndex = 0,
            )
            SongPlayer.playSong(song.url, context)
        }
    }
}
