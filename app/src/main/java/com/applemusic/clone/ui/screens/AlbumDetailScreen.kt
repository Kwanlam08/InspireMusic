package com.applemusic.clone.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.applemusic.clone.R
import com.applemusic.clone.data.OnlineMetadataManager
import com.applemusic.clone.data.AlbumOnlineInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.applemusic.clone.ui.components.FloatingGlassIconButton
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetDragHandle
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetFrame
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetModifier
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetShape
import com.applemusic.clone.ui.components.LiquidGlassMenuRow
import com.applemusic.clone.ui.components.liquidGlassBottomSheetColor
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.viewmodel.MusicViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.palette.graphics.Palette

/**
 * 专辑详情页 — 含视差 Hero 封面效果
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val albumSongs = songs.filter { it.album == albumName }.sortedBy { it.trackNumber }
    val currentSong by viewModel.currentSong.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val firstSong = albumSongs.firstOrNull()
    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current

    var selectedSong by remember { mutableStateOf<AudioItem?>(null) }
    var showAddToPlaylistFor by remember { mutableStateOf<AudioItem?>(null) }
    var albumAccentColor by remember(albumName) { mutableStateOf<Color?>(null) }
    LaunchedEffect(firstSong?.albumArtUri) {
        albumAccentColor = extractAlbumAccentColor(context, firstSong?.albumArtUri)
    }

    // Queue action toast state
    var toastVisible by remember { mutableStateOf(false) }
    var toastType by remember { mutableStateOf(QueueToastType.PLAY_NEXT) }
    val toastScope = rememberCoroutineScope()
    var toastJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun showToast(type: QueueToastType) {
        toastJob?.cancel()
        toastType = type
        toastVisible = true
        toastJob = toastScope.launch {
            kotlinx.coroutines.delay(1500)
            toastVisible = false
        }
    }

    val listState = rememberLazyListState()

    // 轻微视差：封面跟随页面，只保留一点沉浸感，避免上滑时封面先飞走。
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                Float.MAX_VALUE
            }
        }
    }

    val sortedSongs = albumSongs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
    val localAlbumGenre = remember(albumSongs) {
        albumSongs
            .asSequence()
            .map { it.genre.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()
    }

    // 专辑元数据
    val totalDurationMs = albumSongs.sumOf { it.duration }
    val totalMin = totalDurationMs / 1000 / 60
    val formattedDuration = if (totalMin >= 60) {
        "${totalMin / 60} 小时 ${totalMin % 60} 分钟"
    } else {
        "$totalMin 分钟"
    }

    // 在线信息（流派、简介）提前获取，供 Hero 区域使用
    var onlineInfo by remember { mutableStateOf<com.applemusic.clone.data.AlbumOnlineInfo?>(null) }
    LaunchedEffect(albumName, firstSong?.artist, firstSong?.title) {
        onlineInfo = OnlineMetadataManager.fetchAlbumInfo(
            albumName = albumName,
            artist = firstSong?.artist ?: "",
            firstSongTitle = firstSong?.title ?: ""
        )
    }

    if (albumSongs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("找不到专辑内容")
        }
        return
    }

    val albumBackdropColor = albumAccentColor?.let { albumBackdropColor(it) } ?: MaterialTheme.colorScheme.background
    val albumTextColor = if (albumAccentColor != null) Color.White else MaterialTheme.colorScheme.onBackground
    val albumSecondaryTextColor = if (albumAccentColor != null) Color.White.copy(alpha = 0.70f) else MaterialTheme.colorScheme.onBackground.copy(0.5f)
    val albumDividerColor = if (albumAccentColor != null) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(0.1f)
    val albumAccentTextColor = if (albumAccentColor != null) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val pageGradientStart = with(density) { 260.dp.toPx() }
    val pageGradientEnd = with(density) { 760.dp.toPx() }
    val heroGradientStart = with(density) { 190.dp.toPx() }
    val heroGradientEnd = with(density) { 440.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize().background(albumBackdropColor)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            albumBackdropColor.copy(alpha = 0.58f),
                            albumBackdropColor
                        ),
                        startY = pageGradientStart,
                        endY = pageGradientEnd
                    )
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            // Hero 封面区（视差）
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .background(albumBackdropColor)
                ) {
                    val parallaxOffset = (scrollOffset * 0.12f).coerceIn(0f, with(density) { 28.dp.toPx() })

                    AsyncImage(
                        model = firstSong?.albumArtUri,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(540.dp)
                            .offset(y = (-parallaxOffset / with(density) { 1.dp.toPx() }).toInt().dp)
                    )

                    // 渐变遮罩
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        albumBackdropColor.copy(alpha = 0.12f),
                                        albumBackdropColor.copy(alpha = 0.62f),
                                        albumBackdropColor
                                    ),
                                    startY = heroGradientStart,
                                    endY = heroGradientEnd
                                )
                            )
                    )
                    // 专辑信息
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            textAlign = TextAlign.Center,
                            color = albumTextColor
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = firstSong?.artist ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = albumAccentTextColor,
                            modifier = Modifier.clickable {
                                firstSong?.artist?.let { onNavigateToArtist(it) }
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                        // 元数据行：流派（如有） · 曲目数 · 总时长
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val genre = localAlbumGenre.ifBlank { onlineInfo?.genre.orEmpty() }
                            if (!genre.isNullOrBlank()) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = albumAccentTextColor.copy(alpha = 0.82f)
                                )
                                Text(
                                    text = "  ·  ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = albumSecondaryTextColor.copy(alpha = 0.45f)
                                )
                            }
                            Text(
                                text = "${albumSongs.size} 首歌曲",
                                style = MaterialTheme.typography.bodySmall,
                                color = albumSecondaryTextColor
                            )
                            Text(
                                text = "  ·  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = albumSecondaryTextColor.copy(alpha = 0.45f)
                            )
                            Text(
                                text = formattedDuration,
                                style = MaterialTheme.typography.bodySmall,
                                color = albumSecondaryTextColor
                            )
                        }
                    }
                }
            }

            // 播放按钮
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.playList(sortedSongs, 0) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.album_detail_play), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.playShuffledList(sortedSongs) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.album_detail_shuffle), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = albumDividerColor
                )
            }

            // 歌曲列表 (按碟片分组)
            val songsByDisc = sortedSongs.groupBy { it.discNumber }

            songsByDisc.forEach { (disc, songsInDisc) ->
                if (songsByDisc.size > 1) {
                    item {
                        Column {
                            if (disc != songsByDisc.keys.first()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                                    thickness = 0.5.dp,
                                    color = albumDividerColor
                                )
                            }
                            Text(
                                text = stringResource(R.string.disc_label, disc),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                                color = albumTextColor,
                                modifier = Modifier.padding(start = 20.dp, top = if (disc == songsByDisc.keys.first()) 16.dp else 4.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
                items(songsInDisc) { song ->
                    val songIndex = sortedSongs.indexOf(song)
                    val isFav = favoriteIds.contains(song.id)
                    SwipeToPlayNextWrapper(
                        onPlayNext = { viewModel.playNext(song); showToast(QueueToastType.PLAY_NEXT) },
                        onAddLast = { viewModel.addToQueue(song); showToast(QueueToastType.ADD_TO_QUEUE) }
                    ) {
                        AlbumSongListItem(
                            song = song,
                            isPlaying = currentSong?.id == song.id,
                            contentColor = albumTextColor,
                            secondaryColor = albumSecondaryTextColor,
                            dividerColor = albumDividerColor,
                            playingColor = albumAccentTextColor,
                            onClick = { viewModel.playList(sortedSongs, songIndex.coerceAtLeast(0)) },
                            onLongPress = { selectedSong = song }
                        )
                    }
                }
            }
            item {
                AlbumDescriptionSection(
                    artistName = firstSong?.artist ?: "",
                    onlineInfo = onlineInfo
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TopBackButton(onBack = onBack)
        }

        // Queue action toast overlay - placed at bottom but with high enough bottom padding
        // 避开 MiniPlayer(64dp) + BottomBar(80dp) + 状态栏安全区
        Box(
            modifier = Modifier.fillMaxSize().zIndex(10f),
            contentAlignment = Alignment.BottomCenter
        ) {
            QueueActionToast(
                visible = toastVisible,
                type = toastType,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 180.dp)
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

                AlbumMenuRow(
                    icon = Icons.Default.Person,
                    label = stringResource(R.string.menu_nav_artist),
                    onClick = {
                        selectedSong = null
                        onNavigateToArtist(song.artist)
                    }
                )
                AlbumMenuRow(
                    icon = Icons.Default.PlaylistAdd,
                    label = stringResource(R.string.menu_add_playlist),
                    onClick = {
                        showAddToPlaylistFor = song
                    }
                )
                AlbumMenuRow(
                    icon = Icons.Default.QueueMusic,
                    label = stringResource(R.string.menu_play_later),
                    onClick = {
                        viewModel.playNext(song)
                        selectedSong = null
                    }
                )
                AlbumMenuRow(
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
                        AlbumMenuRow(
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
private fun AlbumMenuRow(
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

@Composable
private fun TopBackButton(onBack: () -> Unit) {
    FloatingGlassIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.action_back),
        onClick = onBack,
        useSharedBackdrop = true
    )
}

@Composable
fun AlbumSongListItem(
    song: AudioItem,
    isPlaying: Boolean,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    secondaryColor: Color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
    dividerColor: Color = MaterialTheme.colorScheme.onSurface.copy(0.08f),
    playingColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress?.invoke() }
                )
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 曲目序号 或 正在播放图标
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlaying) {
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = "Playing",
                    tint = playingColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = if (song.trackNumber > 0) song.trackNumber.toString() else "-",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isPlaying) playingColor else contentColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        // 时长
        val totalSec = song.duration / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        Text(
            text = "%d:%02d".format(min, sec),
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor.copy(alpha = 0.82f)
        )
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = dividerColor
    )
}

private val PlayNextCardShape = RoundedCornerShape(16.dp)

private val playNextExitSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMedium
)

private val playNextReturnSpring = spring<Float>(
    dampingRatio = 0.9f,
    stiffness = Spring.StiffnessHigh
)

private val playNextCancelTween = tween<Float>(240, easing = FastOutSlowInEasing)

/**
 * 仿 Apple Music 两段式右滑动作：
 * 阶段 1：向右滑动 > revealThreshold 后，停留显示两个圆角矩形按钮（插播 / 添加到末尾），松开回弹。
 * 阶段 2：继续滑动 > triggerThreshold 自动触发插播。
 */
@Composable
fun SwipeToPlayNextWrapper(
    onPlayNext: () -> Unit,
    onAddLast: () -> Unit,
    content: @Composable () -> Unit
) {
    val revealThreshold = 100f  // 阶段一揭示图标阈值（dp）
    val deadZoneDp = 28f        // 死区：此范围内无任何视觉反馈
    val triggerThreshold = 200f // 阶段二触发阈值（dp）
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val revealPx = with(density) { revealThreshold.dp.toPx() }
    val deadZonePx = with(density) { deadZoneDp.dp.toPx() }
    val triggerPx = with(density) { triggerThreshold.dp.toPx() }
    val exitRightPx = with(density) { configuration.screenWidthDp.dp.toPx() * 1.25f }

    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }

    // 震动反馈
    val view = androidx.compose.ui.platform.LocalView.current
    var hasHapticPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(offset.value) {
        if (offset.value >= triggerPx && !hasHapticPlayed) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            hasHapticPlayed = true
        } else if (offset.value < triggerPx) {
            hasHapticPlayed = false
        }
    }

    val bgAlpha = if (offset.value < deadZonePx) 0f
        else ((offset.value - deadZonePx) / (revealPx - deadZonePx)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .clip(PlayNextCardShape)
            .background(Color.Transparent)
    ) {
        // 底层操作区：两个小圆角矩形按钮（放大并提升 zIndex 让用户容易点中）
        if (bgAlpha > 0.05f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(2f)
                    .graphicsLayer { alpha = bgAlpha }
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // 插播（圆形图标 + 中等大，去掉下方小字）
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Black.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.34f), CircleShape)
                        .clickable {
                            scope.launch {
                                onPlayNext()
                                offset.animateTo(exitRightPx, playNextExitSpring)
                                offset.animateTo(0f, playNextReturnSpring)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = stringResource(R.string.swipe_play_next),
                        tint = Color.White.copy(alpha = 0.94f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                // 加入队列（圆形图标 + 中等大，去掉下方小字）
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Black.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.34f), CircleShape)
                        .clickable {
                            scope.launch {
                                onAddLast()
                                offset.animateTo(exitRightPx, playNextExitSpring)
                                offset.animateTo(0f, playNextReturnSpring)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlaylistPlay,
                        contentDescription = stringResource(R.string.swipe_add_queue),
                        tint = Color.White.copy(alpha = 0.94f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        val draggableState = rememberDraggableState { delta ->
            scope.launch {
                offset.snapTo((offset.value + delta).coerceAtLeast(0f))
            }
        }

        // 内容层：向右位移，留出按钮空间
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .clip(PlayNextCardShape)
                .background(Color.Transparent)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !offset.isRunning,
                    state = draggableState,
                    onDragStopped = {
                        scope.launch {
                            when {
                                offset.value >= triggerPx -> {
                                    onPlayNext()
                                    offset.animateTo(exitRightPx, playNextExitSpring)
                                    offset.animateTo(0f, playNextReturnSpring)
                                }
                                offset.value >= revealPx -> {
                                    // 停留在阶段一，展示按钮。新的按钮总宽 76+8+76=160dp，
                                    // 加 padding 后约 180dp，确保按钮完全显示
                                    offset.animateTo(with(density) { 180.dp.toPx() }, playNextExitSpring)
                                }
                                else -> {
                                    offset.animateTo(0f, playNextCancelTween)
                                }
                            }
                        }
                    }
                )
        ) {
            content()

            // 点击内容区收起
            if (offset.value >= revealPx) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            scope.launch { offset.animateTo(0f, playNextCancelTween) }
                        }
                )
            }
        }
    }
}

@Composable
private fun AlbumDescriptionSection(
    artistName: String,
    onlineInfo: com.applemusic.clone.data.AlbumOnlineInfo?
) {
    // 只在有内容时显示
    val desc = onlineInfo?.description
    // 兼容三种：完整日期 "2020-01-15" / 只有年份 "2020" / null
    val releaseRaw = onlineInfo?.releaseDate?.takeIf { it.isNotBlank() }
    val releaseLabel = releaseRaw?.let { raw ->
        when {
            raw.length >= 10 && raw[4] == '-' && raw[7] == '-' -> "${raw.take(4)}年${raw.substring(5, 7)}月${raw.substring(8, 10)}日"
            raw.length == 4 -> "${raw}年"
            else -> raw
        }
    }
    val hasContent = !desc.isNullOrBlank() || !releaseLabel.isNullOrBlank() || !artistName.isBlank()
    if (!hasContent) return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(0.08f),
            modifier = Modifier.padding(bottom = 14.dp)
        )

        // 艺人名
        Text(
            artistName,
            color = MaterialTheme.colorScheme.onBackground.copy(0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        // 发行日期（如有）："2020年01月15日" / "2020年"
        if (!releaseLabel.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                releaseLabel,
                color = MaterialTheme.colorScheme.onBackground.copy(0.3f),
                fontSize = 12.sp
            )
        }

        // 专辑简介（MusicBrainz annotation）
        if (!desc.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = desc,
                color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "来自 MusicBrainz",
                color = MaterialTheme.colorScheme.onBackground.copy(0.2f),
                fontSize = 10.sp
            )
        }
    }
}

private fun albumBackdropColor(accent: Color): Color {
    val softened = accent.blendWith(Color.White, 0.08f)
    return softened.blendWith(Color.Black, 0.46f)
}

private fun Color.blendWith(target: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * t,
        green = green + (target.green - green) * t,
        blue = blue + (target.blue - blue) * t,
        alpha = alpha + (target.alpha - alpha) * t
    )
}

private suspend fun extractAlbumAccentColor(context: Context, artworkUri: Uri?): Color? = withContext(Dispatchers.IO) {
    if (artworkUri == null) return@withContext null
    val bitmap = runCatching {
        context.contentResolver.openInputStream(artworkUri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull() ?: return@withContext null

    try {
        val palette = Palette.from(bitmap)
            .maximumColorCount(16)
            .generate()
        val swatch = palette.vibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkMutedSwatch
        swatch?.rgb?.let { Color(it) }
    } catch (_: Exception) {
        null
    } finally {
        bitmap.recycle()
    }
}
