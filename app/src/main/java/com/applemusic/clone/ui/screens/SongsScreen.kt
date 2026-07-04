package com.applemusic.clone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.R
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.viewmodel.MusicViewModel
import com.applemusic.clone.ui.components.EmptyStateView
import com.applemusic.clone.ui.components.FloatingGlassIconButton
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetDragHandle
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetFrame
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetModifier
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetShape
import com.applemusic.clone.ui.components.LiquidGlassMenuRow
import com.applemusic.clone.ui.components.LoadingStateView
import com.applemusic.clone.ui.components.OptimizedArtworkImage
import com.applemusic.clone.ui.components.liquidGlassBottomSheetColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

enum class SongSortOrder(val labelResId: Int) {
    TITLE(R.string.songs_sort_title),
    ARTIST(R.string.songs_sort_artist),
    DURATION(R.string.songs_sort_duration)
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

    val sortedSongs = remember(songs, sortOrder) {
        when (sortOrder) {
            SongSortOrder.TITLE -> songs.sortedBy { it.title.lowercase() }
            SongSortOrder.ARTIST -> songs.sortedBy { it.artist.lowercase() }
            SongSortOrder.DURATION -> songs.sortedBy { it.duration }
        }
    }
    val songIndexById = remember(sortedSongs) {
        sortedSongs.withIndex().associate { it.value.id to it.index }
    }

    // 字母索引（仅在按标题排序时显示）
    val alphabetGroups = remember(sortedSongs, sortOrder) {
        if (sortOrder == SongSortOrder.TITLE) {
            sortedSongs.groupBy { song ->
                val c = song.title.firstOrNull()?.uppercaseChar() ?: '#'
                if (c in 'A'..'Z') c.toString() else "#"
            }.toSortedMap()
        } else null
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val alphabetIndexLetters = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    val letterToLazyIndex = remember(alphabetGroups) {
        val result = mutableMapOf<String, Int>()
        var lazyIndex = 0
        alphabetGroups?.forEach { (letter, letterSongs) ->
            result[letter] = lazyIndex
            lazyIndex += letterSongs.size + 1
        }
        result
    }
    var lastIndexLetter by remember { mutableStateOf<String?>(null) }
    fun scrollToLetter(letter: String, force: Boolean = false) {
        if (!force && lastIndexLetter == letter) return
        val lazyIndex = letterToLazyIndex[letter] ?: return
        lastIndexLetter = letter
        coroutineScope.launch {
            listState.scrollToItem(lazyIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Queue action toast overlay - placed at bottom
        Box(
            modifier = Modifier.fillMaxSize().align(Alignment.BottomCenter),
            contentAlignment = Alignment.BottomCenter
        ) {
            QueueActionToast(
                visible = toastVisible,
                type = toastType,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp)
                    .zIndex(10f)
            )
        }
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
                FloatingGlassIconButton(
                    icon = Icons.Default.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.songs_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.songs_count, songs.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                )
                // 排序按钮
                FloatingGlassIconButton(
                    icon = Icons.Default.Sort,
                    contentDescription = stringResource(R.string.songs_sort),
                    onClick = { showSortMenu = true }
                )
            }

            if (isLoading) {
                LoadingStateView(message = stringResource(R.string.songs_loading), modifier = Modifier.weight(1f))
            } else if (songs.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(R.string.songs_empty),
                    message = stringResource(R.string.songs_empty),
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
                            items(letterSongs, key = { it.id }) { song ->
                                val songIndex = songIndexById[song.id] ?: 0
                                SwipeToPlayNextWrapper(
                                    onPlayNext = { viewModel.playNext(song); showToast(QueueToastType.PLAY_NEXT) },
                                    onAddLast = { viewModel.addToQueue(song); showToast(QueueToastType.ADD_TO_QUEUE) }
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
                        itemsIndexed(sortedSongs, key = { _, song -> song.id }) { songIndex, song ->
                            SwipeToPlayNextWrapper(
                                onPlayNext = { viewModel.playNext(song); showToast(QueueToastType.PLAY_NEXT) },
                                onAddLast = { viewModel.addToQueue(song); showToast(QueueToastType.ADD_TO_QUEUE) }
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
            val density = LocalDensity.current
            val indexItemHeight = 14.dp
            val indexHeight = indexItemHeight * alphabetIndexLetters.size
            val indexHeightPx = with(density) { indexHeight.toPx() }
            fun letterAt(y: Float): String {
                val index = ((y / indexHeightPx) * alphabetIndexLetters.size)
                    .toInt()
                    .coerceIn(0, alphabetIndexLetters.lastIndex)
                return alphabetIndexLetters[index]
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 108.dp, end = 8.dp, bottom = 174.dp)
                    .width(30.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .height(indexHeight)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.Transparent)
                        .border(
                            0.8.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.24f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.10f)
                                )
                            ),
                            RoundedCornerShape(15.dp)
                        )
                        .pointerInput(letterToLazyIndex) {
                            detectVerticalDragGestures(
                                onDragStart = { offset -> scrollToLetter(letterAt(offset.y)) },
                                onDragEnd = { lastIndexLetter = null },
                                onDragCancel = { lastIndexLetter = null },
                                onVerticalDrag = { change, _ ->
                                    scrollToLetter(letterAt(change.position.y))
                                    change.consume()
                                }
                            )
                        }
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    alphabetIndexLetters.forEach { letter ->
                        val isEnabled = letterToLazyIndex.containsKey(letter)
                        Text(
                            text = letter,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
                            },
                            modifier = Modifier
                                .height(indexItemHeight)
                                .fillMaxWidth()
                                .clickable(enabled = isEnabled) { scrollToLetter(letter, force = true) },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        if (false) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                alphabetGroups.orEmpty().keys.forEach { letter ->
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
    if (showSortMenu) {
        ModalBottomSheet(
            onDismissRequest = { showSortMenu = false },
            modifier = LiquidGlassBottomSheetModifier,
            containerColor = Color.Transparent,
            shape = LiquidGlassBottomSheetShape,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = 0.24f)
        ) {
            LiquidGlassBottomSheetFrame {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 34.dp)
                ) {
                    Text(
                        text = stringResource(R.string.songs_sort),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                    )
                    SongSortOrder.values().forEach { order ->
                        LiquidGlassMenuRow(
                            icon = if (sortOrder == order) Icons.Default.Check else Icons.Default.Sort,
                            label = stringResource(order.labelResId),
                            iconTint = if (sortOrder == order) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f)
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
                        OptimizedArtworkImage(
                            model = song.albumArtUri,
                            contentDescription = null,
                            size = 48.dp,
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
                    label = stringResource(R.string.menu_play_now),
                    onClick = {
                        viewModel.play(song)
                        selectedSong = null
                    }
                )
                AppleMenuRow(
                    icon = Icons.Default.QueueMusic,
                    label = stringResource(R.string.menu_play_later),
                    onClick = {
                        viewModel.playNext(song)
                        selectedSong = null
                    }
                )
                AppleMenuRow(
                    icon = Icons.Default.PlaylistAdd,
                    label = stringResource(R.string.menu_add_playlist),
                    onClick = {
                        showAddToPlaylistMenuFor = song
                    }
                )
                AppleMenuRow(
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

    showAddToPlaylistMenuFor?.let { song ->
        val playlists by viewModel.playlists.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { showAddToPlaylistMenuFor = null },
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
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            OptimizedArtworkImage(
                model = song.albumArtUri,
                contentDescription = null,
                size = 50.dp,
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
                contentDescription = stringResource(R.string.now_playing_indicator),
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
    LiquidGlassMenuRow(
        icon = icon,
        label = label,
        labelColor = labelColor,
        onClick = onClick
    )
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                FloatingGlassIconButton(
                    icon = Icons.Default.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.songs_favorites_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.songs_count, favoriteSongs.size),
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
                            stringResource(R.string.favorites_empty),
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
                    itemsIndexed(favoriteSongs, key = { _, song -> song.id }) { songIndex, song ->
                        SwipeToPlayNextWrapper(
                            onPlayNext = { viewModel.playNext(song); showToast(QueueToastType.PLAY_NEXT) },
                            onAddLast = { viewModel.addToQueue(song); showToast(QueueToastType.ADD_TO_QUEUE) }
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

        // Queue action toast overlay - placed at bottom
        Box(
            modifier = Modifier.fillMaxSize().align(Alignment.BottomCenter),
            contentAlignment = Alignment.BottomCenter
        ) {
            QueueActionToast(
                visible = toastVisible,
                type = toastType,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp)
                    .zIndex(10f)
            )
        }
    }
}
