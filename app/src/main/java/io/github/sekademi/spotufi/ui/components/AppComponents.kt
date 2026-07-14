package io.github.sekademi.spotufi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.util.lerp
import io.github.sekademi.spotufi.data.entity.SongsModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.preferences.addLikedSongId
import io.github.sekademi.spotufi.data.preferences.isSongLiked
import io.github.sekademi.spotufi.data.preferences.removeLikedSongId
import io.github.sekademi.spotufi.di.Palette
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.navigation.Routes
import io.github.sekademi.spotufi.ui.theme.AppBackground
import io.github.sekademi.spotufi.ui.theme.GridBackground
import io.github.sekademi.spotufi.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Loader() {
    Column(Modifier
        .background(Color(AppBackground.toArgb())),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(45.dp),
            color = Color(0xFF4A4AC4)
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(navController: NavHostController) {
    val miniPlayerViewModel : PlayerViewModel = hiltViewModel()
    val songTitle = miniPlayerViewModel.currentSongTitle.value
    val songSinger = miniPlayerViewModel.currentSongSinger.value
    val songCoverUri = miniPlayerViewModel.currentSongCoverUri.value
    var songPlayingState = miniPlayerViewModel.currentSongPlayingState.value
    val songId = miniPlayerViewModel.currentSongId.value
    val songIndex = miniPlayerViewModel.currentSongIndex.value
    val songAlbum = miniPlayerViewModel.currentSongAlbum.value

    var swipeOffsetY by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    var songProgress by remember { mutableFloatStateOf(0f) }

    songProgress = if (SongPlayer.getDuration() > 0) {
        SongPlayer.getCurrentPosition().toFloat() / SongPlayer.getDuration().toFloat()
    } else {
        0f
    }

    val currentRoute = navController.currentBackStackEntry?.destination?.route



    LaunchedEffect(key1  = songPlayingState) {
            while (songPlayingState) {
                songProgress = SongPlayer.getDuration().toFloat().let { dur ->
                    if (dur > 0f) (SongPlayer.getCurrentPosition().toFloat() / dur).coerceIn(0f, 1f) else 0f
                }
                delay(300L) // update every .00 second

                if (songProgress > 0f && songProgress >= 1f && currentRoute != Routes.Player.route) {
                    navController.navigate(Routes.Player.route)
                    songPlayingState = false
                }
        }
    }

    val context = LocalContext.current

    var darkVibrantColor by remember {
        mutableStateOf(Color(GridBackground.toArgb()))
    }
    Palette().extractFirstColorFromImageUrl(context = context, songCoverUri){ color ->
        darkVibrantColor = color
    }

    var isLiked by remember {
        mutableStateOf(false)
    }
    val likeState = miniPlayerViewModel.likeState.value
    LaunchedEffect(likeState, songId) {
        isLiked = isSongLiked(context, songId.toString())
    }
    val currentTrack = miniPlayerViewModel.queue.value.firstOrNull { it.id == songId }
    var showSavedIn by remember { mutableStateOf(false) }
    if (showSavedIn && currentTrack != null) {
        SavedInSheet(
            song = currentTrack,
            context = context,
            onDismiss = { showSavedIn = false },
            onLikedChanged = {
                isLiked = it
                miniPlayerViewModel.updateLikeState(!likeState)
            },
        )
    }


    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .graphicsLayer {
                translationY = swipeOffsetY
                alpha = (1f + swipeOffsetY / 150f).coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(8.dp))
            .background(darkVibrantColor)
            .padding(horizontal = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    navController.navigate(Routes.Player.route)
                }
                .pointerInput(Unit) {
                    var navigated = false
                    var lastEventTimeMs = 0L
                    val distThreshold = 20.dp.toPx()
                    val velThreshold = 1500f
                    detectDragGestures(
                        onDragStart = {
                            navigated = false
                            lastEventTimeMs = 0L
                        },
                        onDragEnd = {
                            if (!navigated) {
                                coroutineScope.launch {
                                    val anim = Animatable(swipeOffsetY)
                                    anim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.7f,
                                            stiffness = 400f
                                        )
                                    ) { swipeOffsetY = value }
                                }
                            } else {
                                coroutineScope.launch {
                                    val anim = Animatable(swipeOffsetY)
                                    anim.animateTo(
                                        targetValue = -300f,
                                        animationSpec = tween(280)
                                    ) { swipeOffsetY = value }
                                    swipeOffsetY = 0f
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                val anim = Animatable(swipeOffsetY)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.7f,
                                        stiffness = 400f
                                    )
                                ) { swipeOffsetY = value }
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        swipeOffsetY = (swipeOffsetY + dragAmount.y).coerceAtMost(0f)
                        if (!navigated) {
                            val now = change.uptimeMillis
                            val dtMs = if (lastEventTimeMs > 0L) now - lastEventTimeMs else 0L
                            lastEventTimeMs = now
                            val velocityPxPerSec = if (dtMs > 5) dragAmount.y / dtMs * 1000f else 0f
                            if (swipeOffsetY < -distThreshold || velocityPxPerSec < -velThreshold) {
                                navigated = true
                                navController.navigate(Routes.Player.route)
                            }
                        }
                    }
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
            ) {
                AsyncImage(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(42.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    model = songCoverUri,
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.placeholder),
                    placeholder = painterResource(R.drawable.placeholder),
                    contentDescription = ""
                )
                Column(Modifier.weight(1f)) {
                    Text(text = songTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(text = songSinger, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {

                // Plus = save; green check = already saved. A second tap opens the
                // "Saved in" sheet (Liked Songs + playlists) instead of unliking.
                if (isLiked) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        tint = Color(0xFF1ED760),
                        modifier = Modifier
                            .size(22.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    removeLikedSongId(context, songId.toString())
                                    isLiked = false
                                    miniPlayerViewModel.updateLikeState(!likeState)
                                },
                                onLongClick = { showSavedIn = true },
                            ),
                        contentDescription = "Saved",
                    )
                } else {
                    Icon(
                        modifier = Modifier
                            .size(22.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    addLikedSongId(context, songId.toString())
                                    isLiked = true
                                    miniPlayerViewModel.updateLikeState(!likeState)
                                    io.github.sekademi.spotufi.data.api.SpotifySync.setTrackSaved(
                                        context, currentTrack?.spotifyTrackId.orEmpty(), true)
                                },
                                onLongClick = { showSavedIn = true },
                            ),
                        painter = painterResource(id = R.drawable.ic_add),
                        tint = Color.White,
                        contentDescription = "Add",
                    )
                }


                val isLocatingOrBuffering = miniPlayerViewModel.isResolving.value || miniPlayerViewModel.isBuffering.value
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    if (isLocatingOrBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            painter = if (songPlayingState)
                                painterResource(id = R.drawable.ic_playing)
                            else
                                painterResource(id = R.drawable.play_svgrepo_com)
                            ,
                            contentDescription = "",
                            tint = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (songPlayingState) {
                                        SongPlayer.pause()
                                        miniPlayerViewModel.updateSongState(
                                            songCoverUri,
                                            songTitle,
                                            songSinger,
                                            false,
                                            songId,
                                            songIndex,
                                            songAlbum
                                        )
                                    } else {
                                        SongPlayer.play()
                                        miniPlayerViewModel.updateSongState(
                                            songCoverUri,
                                            songTitle,
                                            songSinger,
                                            true,
                                            songId,
                                            songIndex,
                                            songAlbum
                                        )
                                    }
                                }
                        )
                    }
                }
            }
        }

        CustomSlider(
            value = songProgress,
            onValueChange = { newValue ->
                SongPlayer.seekTo((newValue * SongPlayer.getDuration()).toLong())
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }

}


@Composable
fun CustomSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .height(14.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val mapped = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                    onValueChange(mapped)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val newFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val mapped = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                    onValueChange(mapped)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            val trackHeightPx = with(density) { 2.dp.toPx() }
            val trackY = size.height / 2f
            val thumbX = fraction * size.width

            drawLine(
                color = Color(0xFF535353),
                start = Offset(0f, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, trackY),
                end = Offset(thumbX, trackY),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToQueueBox(
    song: SongsModel,
    onAddToQueue: (SongsModel) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState()

    val dismissProgress = dismissState.progress
    val isSettling = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
    val progress = if (isSettling) dismissProgress.coerceIn(0f, 1f) else 0f

    val iconTint by animateColorAsState(
        targetValue = if (progress > 0.3f) Color.White else Color.Gray,
        label = "IconTintAnimation"
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        onDismiss = {
            onAddToQueue(song)
            android.widget.Toast.makeText(
                context,
                "${song.title} added to queue",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            scope.launch { dismissState.reset() }
        },
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1DB954).copy(alpha = progress * 0.4f))
                    .padding(horizontal = 24.dp)
            ) {
                if (progress > 0.05f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_queue_add),
                            contentDescription = "Add to queue",
                            tint = iconTint,
                            modifier = Modifier
                                .size(24.dp)
                                .scale(lerp(0.8f, 1.1f, progress))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add to queue",
                            color = iconTint,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        content = content,
    )
}

@Composable
fun Snackbar(showMessage : String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ){
        Text(
            fontWeight = FontWeight.W500,
            fontSize = 14.sp,
            color = Color.Black,
            text = showMessage
        )
    }
}