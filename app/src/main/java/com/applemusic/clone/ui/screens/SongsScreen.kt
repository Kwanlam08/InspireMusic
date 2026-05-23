package com.applemusic.clone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.viewmodel.MusicViewModel
import com.applemusic.clone.ui.components.EmptyStateView
import com.applemusic.clone.ui.components.LoadingStateView
import kotlinx.coroutines.launch

enum class SongSortOrder(val label: String) {
    TITLE("按标题"),
    ARTIST("按艺术家"),
    DURATION("按时长")
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    var sortOrder by remember { mutableStateOf(SongSortOrder.TITLE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<AudioItem?>(null) }
    var showAddToPlaylistMenuFor by remember { mutableStateOf<AudioItem?>(null) }

    val sortedSongs = remember(songs, sortOrder) {
        when (sortOrder) {
            SongSortOrder.TITLE -> songs.sortedBy { it.title.lowercase() }
            SongSortOrder.ARTIST -> songs.sortedBy { it.artist.lowercase() }
            SongSortOrder.DURATION -> songs.sortedBy { it.duration }
        }
    }

    // 字母索引（仅在按标题排序时显示）
    val alphabetGroups = remember(sortedSongs, sortOrder) {
        if (sortOrder == SongSortOrder.TITLE) {
            sortedSongs.groupBy { song ->
                val c = song.title.firstOrNull()?.uppercaseChar() ?: '#'
                if (c.isLetter()) c.toString() else "#"
            }.toSortedMap()
        } else null
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBackIosNew,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "歌曲",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${songs.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                )
                // 排序按钮
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SongSortOrder.values().forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (sortOrder == order) {
                                            Icon(
                                                Icons.Default.Check,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        } else {
                                            Spacer(Modifier.width(22.dp))
                                        }
                                        Text(order.label)
                                    }
                                },
                                onClick = {
                                    sortOrder = order
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                LoadingStateView(message = "正在加载歌曲...", modifier = Modifier.weight(1f))
            } else if (songs.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.MusicNote,
                    title = "没有歌曲",
                    message = "您的设备上没有找到任何音乐文件。",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 160.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (alphabetGroups != null) {
                        alphabetGroups.forEach { (letter, letterSongs) ->
                            stickyHeader {
                                Text(
                                    text = letter,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.background.copy(0.95f)
                                        )
                                        .padding(horizontal = 20.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                                )
                            }
                            items(letterSongs) { song ->
                                val songIndex = sortedSongs.indexOf(song)
                                SwipeToPlayNextWrapper(
                                    onPlayNext = { viewModel.playNext(song) },
                                    onAddLast = { viewModel.addToQueue(song) }
                                ) {
                                    SongListItemWithLongPress(
                                        song = song,
                                        isPlaying = currentSong?.id == song.id,
                                        isFavorite = favoriteIds.contains(song.id),
                                        viewModel = viewModel,
                                        onClick = { viewModel.playList(sortedSongs, songIndex.coerceAtLeast(0)) },
                                        onLongPress = { selectedSong = song },
                                        onAddToPlaylist = { showAddToPlaylistMenuFor = song }
                                    )
                                }
                            }
                        }
                    } else {
                        items(sortedSongs) { song ->
                            val songIndex = sortedSongs.indexOf(song)
                            SwipeToPlayNextWrapper(
                                onPlayNext = { viewModel.playNext(song) },
                                onAddLast = { viewModel.addToQueue(song) }
                            ) {
                                SongListItemWithLongPress(
                                    song = song,
                                    isPlaying = currentSong?.id == song.id,
                                    isFavorite = favoriteIds.contains(song.id),
                                    viewModel = viewModel,
                                    onClick = { viewModel.playList(sortedSongs, songIndex.coerceAtLeast(0)) },
                                    onLongPress = { selectedSong = song },
                                    onAddToPlaylist = { showAddToPlaylistMenuFor = song }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 字母索引侧边栏
        if (alphabetGroups != null && alphabetGroups.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                alphabetGroups.keys.forEach { letter ->
                    Text(
                        text = letter,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .pointerInput(letter) {
                                detectTapGestures {
                                    // 计算该字母在 LazyColumn 中的位置
                                    val idx = sortedSongs.indexOfFirst { song ->
                                        val c = song.title
                                            .firstOrNull()
                                            ?.uppercaseChar() ?: '#'
                                        (if (c.isLetter()) c.toString() else "#") == letter
                                    }
                                    if (idx >= 0) {
                                        coroutineScope.launch {
                                            listState.scrollToItem(idx)
                                        }
                                    }
                                }
                            }
                    )
                }
            }
        }
    }

    // 长按上下文菜单
    selectedSong?.let { song ->
        val isFav = favoriteIds.contains(song.id)
        ModalBottomSheet(
            onDismissRequest = { selectedSong = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        coil.compose.AsyncImage(
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

                AppleMenuRow(
                    icon = Icons.Default.PlayArrow,
                    label = "立即播放",
                    onClick = {
                        viewModel.play(song)
                        selectedSong = null
                    }
                )
                AppleMenuRow(
                    icon = Icons.Default.QueueMusic,
                    label = "稍后播放",
                    onClick = {
                        viewModel.playNext(song)
                        selectedSong = null
                    }
                )
                AppleMenuRow(
                    icon = Icons.Default.PlaylistAdd,
                    label = "添加到播放列表",
                    onClick = {
                        showAddToPlaylistMenuFor = song
                    }
                )
                AppleMenuRow(
                    icon = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (isFav) "取消喜爱" else "加入喜好项目",
                    onClick = {
                        viewModel.toggleFavorite(song.id)
                        selectedSong = null
                    },
                    showDivider = false
                )
            }
        }
    }

    showAddToPlaylistMenuFor?.let { song ->
        val playlists by viewModel.playlists.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { showAddToPlaylistMenuFor = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
            ) {
                Text(
                    "添加到播放列表",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp, top = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                )
                if (playlists.isEmpty()) {
                    Text(
                        "没有可用的播放列表，请先创建一个。",
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
                        AppleMenuRow(
                            icon = Icons.Default.QueueMusic,
                            label = playlist.name,
                            onClick = {
                                viewModel.addSongToPlaylist(playlist.id, song.id)
                                showAddToPlaylistMenuFor = null
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

@Composable
fun SongListItemWithLongPress(
    song: AudioItem,
    isPlaying: Boolean,
    isFavorite: Boolean,
    viewModel: MusicViewModel,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            coil.compose.AsyncImage(
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
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp
            )
            Text(
                text = "${song.artist} — ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 收藏图标（小）
        if (isFavorite) {
            Icon(
                Icons.Default.Favorite,
                null,
                tint = MaterialTheme.colorScheme.primary.copy(0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
        }

        if (isPlaying) {
            Icon(
                Icons.Default.VolumeUp,
                contentDescription = "正在播放",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
        }

        // 时长
        val totalSec = song.duration / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        Text(
            text = "%d:%02d".format(min, sec),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.4f)
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(0.08f)
    )
}

// 向后兼容原有调用（AlbumDetailScreen / SearchScreen 等）
@Composable
fun SongListItem(
    song: AudioItem,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    onClick: () -> Unit
) {
    SongListItemWithLongPress(
        song = song,
        isPlaying = isPlaying,
        isFavorite = false,
        viewModel = viewModel,
        onClick = onClick,
        onLongPress = {},
        onAddToPlaylist = {}
    )
}

@Composable
private fun AppleMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 17.sp
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 58.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    val favoriteSongs = remember(songs, favoriteIds) {
        songs.filter { favoriteIds.contains(it.id) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBackIosNew,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "我的喜爱",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${favoriteSongs.size} 首",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
            )
        }

        if (favoriteSongs.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.2f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "还没有喜爱的歌曲",
                        color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 160.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(favoriteSongs) { song ->
                    val songIndex = favoriteSongs.indexOf(song)
                    SwipeToPlayNextWrapper(
                        onPlayNext = { viewModel.playNext(song) },
                        onAddLast = { viewModel.addToQueue(song) }
                    ) {
                        SongListItemWithLongPress(
                            song = song,
                            isPlaying = currentSong?.id == song.id,
                            isFavorite = true,
                            viewModel = viewModel,
                            onClick = { viewModel.playList(favoriteSongs, songIndex.coerceAtLeast(0)) },
                            onLongPress = {},
                            onAddToPlaylist = {}
                        )
                    }
                }
            }
        }
    }
}