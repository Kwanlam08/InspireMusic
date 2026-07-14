package com.inspiremusic.ui.screens

import com.inspiremusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inspiremusic.ui.components.FloatingGlassIconButton
import com.inspiremusic.ui.components.LiquidGlassBottomSheetDragHandle
import com.inspiremusic.ui.components.LiquidGlassBottomSheetFrame
import com.inspiremusic.ui.components.LiquidGlassBottomSheetModifier
import com.inspiremusic.ui.components.LiquidGlassBottomSheetShape
import com.inspiremusic.ui.components.LiquidGlassMenuRow
import com.inspiremusic.ui.components.liquidGlassBottomSheetColor
import com.inspiremusic.model.AudioItem
import com.inspiremusic.viewmodel.MusicViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 艺术家详情页 — 按专辑分组展示歌曲，含长按上下文菜单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val displayArtistName = viewModel.primaryArtistName(artistName)
    val artistSongs = remember(songs, artistName) { viewModel.songsForArtist(artistName) }
    val currentSong by viewModel.currentSong.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val firstSong = artistSongs.firstOrNull()

    var selectedSong by remember { mutableStateOf<AudioItem?>(null) }
    var showAddToPlaylistFor by remember { mutableStateOf<AudioItem?>(null) }

    // Queue action toast
    var toastVisible by remember { mutableStateOf(false) }
    var toastType by remember { mutableStateOf(QueueToastType.PLAY_NEXT) }
    val toastScope = rememberCoroutineScope()
    var toastJob by remember { mutableStateOf<Job?>(null) }
    fun showToast(type: QueueToastType) {
        toastJob?.cancel()
        toastType = type
        toastVisible = true
        toastJob = toastScope.launch {
            kotlinx.coroutines.delay(1500)
            toastVisible = false
        }
    }

    // 按专辑分组
    val albumGroups = remember(artistSongs) {
        artistSongs
            .groupBy { it.album.trim().lowercase().replace(Regex("\\s+"), " ") }
            .values
            .map { songs -> songs.first().album to songs }
    }

    if (artistSongs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("找不到艺术家内容")
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Queue action toast overlay - placed at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            contentAlignment = Alignment.BottomCenter
        ) {
            QueueActionToast(
                visible = toastVisible,
                type = toastType,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            // 艺术家 Hero 区
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    if (firstSong?.albumArtUri != null) {
                        AsyncImage(
                            model = firstSong.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize()
                                .background(
                                    Brush.verticalGradient(listOf(Color(0xFF2A2A3E), Color(0xFF111118)))
                                )
                        )
                    }

                    // 深度渐变遮罩
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(0.2f), MaterialTheme.colorScheme.background),
                                startY = 100f
                            )
                        )
                    )

                    // 圆形头像 + 名字
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (firstSong?.albumArtUri != null) {
                                AsyncImage(
                                    model = firstSong.albumArtUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = displayArtistName.take(1).uppercase(),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = displayArtistName,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${artistSongs.size} 首歌曲 · ${albumGroups.size} 张专辑",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                        )
                    }
                }
            }

            // 随机播放按钮
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.playShuffledList(artistSongs) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.album_detail_shuffle), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 按专辑分组展示
            albumGroups.forEach { (albumName, albumSongs) ->
                val sortedSongs = albumSongs.sortedBy { it.trackNumber }

                // 专辑小标题
                item(key = "album_$albumName") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAlbum(albumName) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = sortedSongs.firstOrNull()?.albumArtUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = albumName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.songs_count, sortedSongs.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.3f)
                        )
                    }
                }

                // 专辑下的歌曲（最多显示 3 首）
                items(sortedSongs.take(3), key = { it.id }) { song ->
                    val songIdx = artistSongs.indexOf(song)
                    SwipeToPlayNextWrapper(
                        onPlayNext = { viewModel.playNext(song); showToast(QueueToastType.PLAY_NEXT) },
                        onAddLast = { viewModel.addToQueue(song); showToast(QueueToastType.ADD_TO_QUEUE) }
                    ) {
                        SongListItemWithLongPress(
                            song = song,
                            isPlaying = currentSong?.id == song.id,
                            isFavorite = favoriteIds.contains(song.id),
                            viewModel = viewModel,
                            onClick = { viewModel.playList(artistSongs, songIdx.coerceAtLeast(0)) },
                            onLongPress = { selectedSong = song },
                            onAddToPlaylist = { showAddToPlaylistFor = song }
                        )
                    }
                }

                // 如果专辑超过3首，显示"查看全部"
                if (sortedSongs.size > 3) {
                    item(key = "more_$albumName") {
                        Text(
                            text = "查看全部 " + stringResource(R.string.songs_count, sortedSongs.size) + " →",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { onNavigateToAlbum(albumName) }
                                .padding(start = 78.dp, end = 16.dp, bottom = 8.dp, top = 2.dp)
                        )
                    }
                }

                item(key = "divider_$albumName") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(0.08f)
                    )
                }
            }
        }

        // 返回按钮（悬浮）
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            FloatingGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                useSharedBackdrop = true
            )
        }
    }

    selectedSong?.let { song ->
        val isFav = favoriteIds.contains(song.id)
        ModalBottomSheet(
            onDismissRequest = { selectedSong = null },
            modifier = LiquidGlassBottomSheetModifier,
            containerColor = Color.Transparent,
            shape = LiquidGlassBottomSheetShape,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = 0.30f)
        ) {
            LiquidGlassBottomSheetFrame {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp)
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                )

                ArtistMenuRow(
                    icon = Icons.Default.Album,
                    label = stringResource(R.string.menu_view_album),
                    onClick = {
                        selectedSong = null
                        onNavigateToAlbum(song.album)
                    }
                )
                ArtistMenuRow(
                    icon = Icons.Default.PlaylistAdd,
                    label = stringResource(R.string.menu_add_playlist),
                    onClick = {
                        showAddToPlaylistFor = song
                    }
                )
                ArtistMenuRow(
                    icon = Icons.Default.QueueMusic,
                    label = stringResource(R.string.menu_play_later),
                    onClick = {
                        viewModel.playNext(song)
                        selectedSong = null
                    }
                )
                ArtistMenuRow(
                    icon = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (isFav) stringResource(R.string.menu_remove_fav) else stringResource(R.string.menu_add_fav),
                    onClick = {
                        viewModel.toggleFavorite(song.id)
                        selectedSong = null
                    },
                    showDivider = false
                )
                }
            }
        }
    }

    showAddToPlaylistFor?.let { song ->
        ModalBottomSheet(
            onDismissRequest = { showAddToPlaylistFor = null },
            modifier = LiquidGlassBottomSheetModifier,
            containerColor = Color.Transparent,
            shape = LiquidGlassBottomSheetShape,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = 0.30f)
        ) {
            LiquidGlassBottomSheetFrame {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp)
                ) {
                Text(
                    stringResource(R.string.playlist_add_to),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp, top = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                )
                if (playlists.isEmpty()) {
                    Text(
                        stringResource(R.string.playlist_no_available),
                        color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                } else {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                    )
                    playlists.forEachIndexed { i, playlist ->
                        ArtistMenuRow(
                            icon = Icons.Default.QueueMusic,
                            label = playlist.name,
                            onClick = {
                                viewModel.addSongToPlaylist(playlist.id, song.id)
                                showAddToPlaylistFor = null
                                selectedSong = null
                            },
                            showDivider = i < playlists.lastIndex
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ArtistMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    LiquidGlassMenuRow(
        icon = icon,
        label = label,
        labelColor = labelColor,
        onClick = onClick
    )
}
