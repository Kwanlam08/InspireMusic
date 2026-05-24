@file:OptIn(ExperimentalFoundationApi::class)

package com.applemusic.clone.ui.screens

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.applemusic.clone.R
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.model.LrcLine
import com.applemusic.clone.viewmodel.MusicViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 使用 RenderScript 对 Bitmap 进行高斯模糊（API < 31 回退方案）。
 * RenderScript 从 API 31 起被标记为 deprecated，但在低版本上仍可用。
 */
@Suppress("DEPRECATION")
private fun blurBitmap(context: Context, source: Bitmap, radius: Float = 25f): Bitmap {
    return try {
        val rs = RenderScript.create(context)
        val input = Allocation.createFromBitmap(rs, source)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        script.setRadius(radius.coerceIn(1f, 25f))
        script.setInput(input)
        script.forEach(output)
        output.copyTo(source)
        rs.destroy()
        source
    } catch (_: Exception) {
        source
    }
}

private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    onClose: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.currentPositionMs.collectAsState()
    val isShuffleOn by viewModel.isShuffleOn.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val currentLyricIdx by viewModel.currentLyricIndex.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val isFav = currentSong?.let { favoriteIds.contains(it.id) } ?: false
    val sleepTimerMs by viewModel.sleepTimerRemainingMs.collectAsState()

    val context = LocalContext.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ── 专辑封面缩放（播放 → 大，暂停 → 小）──────────────
    val albumScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.82f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "albumScale"
    )

    // ── Play/Pause 按钮弹跳 ───────────────────────────────
    var isPressingPlay by remember { mutableStateOf(false) }
    val playBtnScale by animateFloatAsState(
        targetValue = if (isPressingPlay) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playBtnScale"
    )

    // ── 模糊背景位图（直接把封面模糊后铺满全屏）─────────────
    var blurredBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentSong?.id) {
        blurredBitmap = null
    }

    // ── 音量 ──────────────────────────────────────────────
    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    var volumeLevel by remember {
        mutableStateOf(
            if (maxVol > 0) {
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
            } else 0f
        )
    }

    DisposableEffect(context, audioManager, maxVol) {
        if (maxVol <= 0) {
            return@DisposableEffect onDispose { }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != VOLUME_CHANGED_ACTION) return
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                if (streamType == AudioManager.STREAM_MUSIC) {
                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVol)
                    volumeLevel = cur.toFloat() / maxVol
                }
            }
        }
        val filter = IntentFilter(VOLUME_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    // ── UI 状态 ───────────────────────────────────────────
    var currentTab by remember { mutableIntStateOf(0) } // 0=Cover, 1=Lyrics, 2=Queue
    var artworkAreaSize by remember { mutableStateOf(IntSize.Zero) }
    val morphProgress by animateFloatAsState(
        targetValue = if (currentTab == 0) 0f else 1f,
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "albumMorph"
    )
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    val lyricsListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 返回键拦截
    BackHandler {
        when {
            showMoreMenu -> showMoreMenu = false
            currentTab != 0 -> currentTab = 0
            else -> onClose()
        }
    }

    // 歌词自动滚动
    LaunchedEffect(currentLyricIdx) {
        if (currentLyricIdx >= 0 && currentTab == 1) {
            val targetIdx = (currentLyricIdx - 2).coerceAtLeast(0)
            coroutineScope.launch {
                lyricsListState.animateScrollToItem(
                    index = targetIdx,
                    scrollOffset = 0
                )
            }
        }
    }

    val duration = currentSong?.duration ?: 1L

    // ── 屏幕适配 ───────────────────────────────────────────
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val hPadding = (screenWidthDp * 0.05f).coerceIn(12f, 40f).dp

    // ── 主容器 ────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
    ) {
        // ── 模糊封面背景层 ─────────────────────────────────
        blurredBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(50.dp) else Modifier),
                alpha = 0.7f
            )
        }
        // 半透明蒙版让文字更清晰
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = hPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 顶部栏拉扣 (Pill) ───────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 60f) onClose()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.3f))
                )
            }

            // ── 封面 / 歌词 / 队列：专辑图从封面位（顶部对齐）飞到左上角（无淡入淡出）────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { artworkAreaSize = it.size }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 60f) onClose()
                        }
                    }
            ) {
                if (currentTab != 0) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        NowPlayingCompactHeader(
                            song = currentSong,
                            isFav = isFav,
                            onToggleFav = { currentSong?.let { viewModel.toggleFavorite(it.id) } },
                            onMore = { showMoreMenu = true },
                            artworkAsPlaceholder = true
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Crossfade(
                                targetState = currentTab,
                                animationSpec = tween(400),
                                label = "lyricsQueue"
                            ) { tab ->
                                when (tab) {
                                    1 -> NowPlayingLyricsWithBlur(
                                        morphProgress = morphProgress,
                                        lyrics = lyrics,
                                        currentIndex = currentLyricIdx,
                                        listState = lyricsListState
                                    )
                                    2 -> QueueView(
                                        queue = queue,
                                        currentSong = currentSong,
                                        viewModel = viewModel,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    else -> Box(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }

                currentSong?.let { song ->
                    if (artworkAreaSize.width > 0) {
                        NowPlayingArtworkMorph(
                            song = song,
                            morphProgress = morphProgress,
                            albumScale = albumScale,
                            isPlaying = isPlaying,
                            containerSize = artworkAreaSize,
                            context = context,
                            onBlurredSource = { bmp ->
                                if (blurredBitmap == null) {
                                    val ready = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        bmp
                                    } else {
                                        blurBitmap(context, bmp.copy(bmp.config, true), 25f)
                                    }
                                    blurredBitmap = ready
                                }
                            }
                        )
                    }
                }
            }

            // ── 封面 tab: 歌曲信息 + 控件 ──────────────────
            if (currentTab == 0) {
                Spacer(Modifier.height(10.dp))
                // 歌曲信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong?.title ?: stringResource(R.string.np_not_playing),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = currentSong?.artist ?: stringResource(R.string.np_unknown_artist),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Default,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { currentSong?.let { viewModel.toggleFavorite(it.id) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.np_favorite),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { showMoreMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = stringResource(R.string.np_more),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 进度条 — 自定义 pointer-based 拖动 + 点击，风格与音量滑块一致
                val progress = if (duration > 0) positionMs.toFloat() / duration.toFloat() else 0f
                var isDragging2 by remember { mutableStateOf(false) }
                var dragFraction by remember { mutableFloatStateOf(0f) }
                val displayFraction = if (isDragging2) dragFraction else progress

                var barWidthPx by remember { mutableFloatStateOf(0f) }
                val density = LocalDensity.current

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .onGloballyPositioned { barWidthPx = it.size.width.toFloat() }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isDragging2 = true
                                    dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    viewModel.seekTo((dragFraction * duration).toLong())
                                    isDragging2 = false
                                },
                                onDragCancel = {
                                    isDragging2 = false
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragFraction = (dragFraction + dragAmount / size.width).coerceIn(0f, 1f)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                isDragging2 = true
                                dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                viewModel.seekTo((dragFraction * duration).toLong())
                                isDragging2 = false
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    // 底轨
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(0.2f))
                    )
                    // 已播放轨
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = displayFraction.coerceIn(0f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(0.85f))
                    )
                    // 滑块圆点
                    if (barWidthPx > 0f) {
                        val thumbPx = with(density) { 12.dp.toPx() }
                        val thumbOffsetX = (barWidthPx * displayFraction - thumbPx / 2f)
                            .coerceIn(0f, barWidthPx - thumbPx)
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(thumbOffsetX.roundToInt(), 0) }
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.85f))
                        )
                    }
                }
                val displayPositionMs = if (isDragging2) (dragFraction * duration).toLong() else positionMs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        viewModel.formatDuration(displayPositionMs),
                        color = Color.White.copy(0.5f),
                        fontSize = 12.sp
                    )
                    Text(
                        "-${viewModel.formatDuration((duration - displayPositionMs).coerceAtLeast(0))}",
                        color = Color.White.copy(0.5f),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 播放控制（圆角图标、略上移）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-8).dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.skipPrev() }, modifier = Modifier.size(72.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.np_previous),
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(playBtnScale)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                isPressingPlay = true
                                viewModel.playPause()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        LaunchedEffect(isPressingPlay) {
                            if (isPressingPlay) {
                                kotlinx.coroutines.delay(120)
                                isPressingPlay = false
                            }
                        }
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "播放/暂停",
                            tint = Color.White,
                            modifier = Modifier.size(68.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(72.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.np_next),
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 音量
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.VolumeDown,
                        contentDescription = null,
                        tint = Color.White.copy(0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Slider(
                        value = volumeLevel,
                        onValueChange = { v ->
                            volumeLevel = v
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (v * maxVol).toInt(),
                                0
                            )
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White.copy(0.85f),
                            activeTrackColor = Color.White.copy(0.85f),
                            inactiveTrackColor = Color.White.copy(0.2f)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                    )
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White.copy(0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } // end if currentTab == 0

            // 底部 tab 切换（始终显示）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentTab = if (currentTab == 1) 0 else 1 }) {
                    Icon(
                        Icons.Default.FormatQuote,
                        contentDescription = stringResource(R.string.np_tab_lyrics),
                        tint = if (currentTab == 1) Color.White else Color.White.copy(0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { currentTab = if (currentTab == 2) 0 else 2 }) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = stringResource(R.string.np_tab_queue),
                        tint = if (currentTab == 2) Color.White else Color.White.copy(0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // ── 睡眠定时器设置弹窗 (预设按钮) ─────────────────
    if (showSleepTimerMenu) {
        SleepTimerSheet(
            onDismiss = { showSleepTimerMenu = false },
            onStart = { h, m -> viewModel.startSleepTimer((h * 3600 + m * 60) * 1000L); showSleepTimerMenu = false; showMoreMenu = false }
        )
    }

    // ── 更多菜单 ────────────────────────────────────────────
    if (showMoreMenu && !showSleepTimerMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        currentSong?.title ?: "",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_view_album)) },
                    leadingContent = {
                        Icon(Icons.Default.Album, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showMoreMenu = false
                        currentSong?.album?.let { onNavigateToAlbum(it) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_view_artist)) },
                    leadingContent = {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showMoreMenu = false
                        currentSong?.artist?.let { onNavigateToArtist(it) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = {
                        Text(if (isFav) stringResource(R.string.menu_remove_fav) else stringResource(R.string.menu_add_fav))
                    },
                    leadingContent = {
                        Icon(
                            if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null,
                            tint = Color(0xFFFF375F)
                        )
                    },
                    modifier = Modifier.clickable {
                        currentSong?.let { viewModel.toggleFavorite(it.id) }
                        showMoreMenu = false
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = {
                        val label = when {
                            viewModel.isPauseAfterSongMode -> "播完当前歌曲后暂停 (已开启)"
                            sleepTimerMs != null -> stringResource(R.string.sleep_timer_active_label, viewModel.formatDuration(sleepTimerMs!!))
                            else -> stringResource(R.string.menu_sleep_timer)
                        }
                        Text(label)
                    },
                    leadingContent = {
                        Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        if (sleepTimerMs != null) {
                            viewModel.cancelSleepTimer()
                            showMoreMenu = false
                        } else {
                            showSleepTimerMenu = true
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

/**
 * 专辑封面 morph 动画：从「正在播放」大图位置飞到歌词/队列左上角小封面。
 * albumScale 仅在无 morph 时应用（播放/暂停缩放），morph 过渡期间始终全尺寸。
 */
@Composable
private fun NowPlayingArtworkMorph(
    song: AudioItem,
    morphProgress: Float,
    albumScale: Float,
    isPlaying: Boolean,
    containerSize: IntSize,
    context: Context,
    onBlurredSource: (Bitmap) -> Unit
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val wPx = containerSize.width.toFloat()
    val hPx = containerSize.height.toFloat()
    // 与封面 tab 一致：横向铺满、1:1、贴在 flex 区域顶部（非整块区域垂直居中）
    val bigSide = minOf(wPx, hPx)
    val bigTop = 0f
    val bigLeft = (wPx - bigSide) / 2f
    val smallPx = with(density) { 52.dp.toPx() }
    val p = morphProgress

    val smallLeft = if (layoutDirection == LayoutDirection.Rtl) wPx - smallPx else 0f
    val startCx = bigLeft + bigSide / 2f
    val startCy = bigTop + bigSide / 2f
    val endCx = smallLeft + smallPx / 2f
    val endCy = smallPx / 2f
    val cx = lerp(startCx, endCx, p)
    val cy = lerp(startCy, endCy, p)
    val side = lerp(bigSide, smallPx, p)
    val left = cx - side / 2f
    val top = cy - side / 2f

    val radiusDp = lerp(12f, 8f, p).dp
    val shape = RoundedCornerShape(radiusDp)

    // morph 过渡期间平滑 blend 到 1f，避免缩放动画与 morph 同时进行造成跳动
    val playPulseScale = lerp(albumScale, 1f, p)
    val shadowElevationPx = lerp(
        with(density) { (if (isPlaying) 48.dp else 18.dp).toPx() },
        0f,
        p.coerceIn(0f, 1f)
    )
    val shadowMod = if (isPlaying) {
        Modifier.shadow(
            elevation = 24.dp,
            shape = shape,
            ambientColor = Color.White.copy(alpha = 0.06f),
            spotColor = Color.White.copy(alpha = 0.04f),
            clip = true
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { side.toDp() })
            .then(shadowMod)
            .clip(shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = playPulseScale
                    scaleY = playPulseScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .clip(shape)
        ) {
            coil.compose.SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .crossfade(false)
                    .allowHardware(false)
                    .build(),
                contentDescription = stringResource(R.string.album_art),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentScale = ContentScale.Crop
            ) {
                val state = painter.state
                if (state is coil.compose.AsyncImagePainter.State.Success) {
                    val bmp = (state.result.drawable as? BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        onBlurredSource(bmp)
                    }
                    SubcomposeAsyncImageContent()
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2A2A3E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            null,
                            tint = Color.White.copy(0.3f),
                            modifier = Modifier.size(with(density) { (side * 0.22f).coerceAtMost(80.dp.toPx()).toDp() })
                        )
                    }
                }
            }
        }
    }
}

/**
 * 歌词区：随封面 morph 进度由模糊过渡到清晰（API 31+ 用 blur，以下用透明度）。
 */
@Composable
private fun NowPlayingLyricsWithBlur(
    morphProgress: Float,
    lyrics: List<LrcLine>,
    currentIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val reveal = morphProgress.coerceIn(0f, 1f)
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur((14.dp * (1f - reveal)))
    } else {
        Modifier.graphicsLayer { alpha = 0.3f + 0.7f * reveal }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(blurModifier)
    ) {
        LyricsView(
            lyrics = lyrics,
            currentIndex = currentIndex,
            listState = listState,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ── 歌词视图 ──────────────────────────────────────────────
@Composable
fun LyricsView(
    lyrics: List<LrcLine>,
    currentIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.lyrics_empty),
                color = Color.White.copy(0.3f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 40.dp),
            horizontalAlignment = Alignment.Start
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == currentIndex

                // 每行的 scale 和 alpha 动画
                val lineScale by animateFloatAsState(
                    targetValue = if (isActive) 1.04f else 1f,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    ),
                    label = "lyricScale_$index"
                )
                val lineAlpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0.28f,
                    animationSpec = tween(200),
                    label = "lyricAlpha_$index"
                )

                Text(
                    text = line.text,
                    color = Color.White.copy(alpha = lineAlpha),
                    fontSize = if (isActive) 24.sp else 20.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Default,
                    lineHeight = 36.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(lineScale)
                        .padding(vertical = 6.dp, horizontal = 12.dp)
                )
            }
        }
    }
}

// ── 播放列表视图 (Queue) ──────────────────────────────────
@Composable
fun QueueView(
    queue: List<AudioItem>,
    currentSong: AudioItem?,
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    if (queue.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.queue_empty),
                color = Color.White.copy(0.3f),
                fontSize = 16.sp
            )
        }
    } else {
        val listState = rememberLazyListState()
        var draggedSongId by remember { mutableStateOf<Long?>(null) }
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        
        // 本地可变副本：拖拽中直接改此列表（push-aside 效果），不碰 controller
        val displayQueue = remember { mutableStateListOf<AudioItem>() }
        LaunchedEffect(queue, draggedSongId) {
            if (draggedSongId == null) {
                displayQueue.clear()
                displayQueue.addAll(queue)
            }
        }
        
        // 自动滚动到当前播放的歌曲
        LaunchedEffect(currentSong) {
            val idx = displayQueue.indexOfFirst { it.id == currentSong?.id }
            if (idx >= 0) {
                listState.animateScrollToItem(idx)
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Shuffle / Repeat 控制行（全宽圆角矩形）
            item {
                val isShuffleOn by viewModel.isShuffleOn.collectAsState()
                val repeatMode by viewModel.repeatMode.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isShuffleOn) Color.White.copy(0.25f) else Color.White.copy(0.1f))
                            .clickable { viewModel.toggleShuffle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.np_shuffle),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (repeatMode != Player.REPEAT_MODE_OFF) Color.White.copy(0.25f) else Color.White.copy(0.1f))
                            .clickable { viewModel.cycleRepeat() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = stringResource(R.string.np_repeat),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── 分区：已播放 / 正在播放 / 待播 ──
            val currentIdx = displayQueue.indexOfFirst { it.id == currentSong?.id }
            val playedSongs = if (currentIdx > 0) displayQueue.take(currentIdx) else emptyList()
            val theCurrentSong = if (currentIdx >= 0) displayQueue.getOrNull(currentIdx) else null
            val upcomingSongs = if (currentIdx >= 0) displayQueue.drop(currentIdx + 1) else displayQueue.toList()
            val upcomingStartIdx = currentIdx + 1

            // 已播放（不可拖拽）
            if (playedSongs.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.queue_played)) }
                items(playedSongs.size) { i ->
                    QueueSongItem(
                        song = playedSongs[i],
                        songIndex = i,
                        isActive = false,
                        showDragHandle = false,
                        viewModel = viewModel
                    )
                }
            }

            // 正在播放（不可拖拽）
            theCurrentSong?.let { song ->
                item { SectionHeader(stringResource(R.string.queue_playing)) }
                item {
                    QueueSongItem(
                        song = song,
                        songIndex = currentIdx,
                        isActive = true,
                        showDragHandle = false,
                        viewModel = viewModel
                    )
                }
            }

            // 待播（可拖拽，范围限制 upcomingStartIdx..lastIndex）
            if (upcomingSongs.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.queue_upcoming)) }
                itemsIndexed(
                    items = upcomingSongs,
                    key = { _, song -> song.id }
                ) { i, song ->
                    val globalIdx = upcomingStartIdx + i
                    val isActive = song.id == currentSong?.id
                    val isDragged = song.id == draggedSongId
                    val anyDragging = draggedSongId != null

                    val placementSpec = if (anyDragging) snap<IntOffset>()
                    else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

                    val colMod = Modifier
                        .animateItemPlacement(animationSpec = placementSpec)
                        .zIndex(if (isDragged) 1f else 0f)
                        .then(if (isDragged) Modifier.graphicsLayer { translationY = dragOffsetPx } else Modifier)

                    Column(modifier = colMod) {
                        SwipeToDeleteWrapper(onDelete = { viewModel.removeFromQueue(song) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f))
                                    .clickable { viewModel.skipToQueueIndex(globalIdx) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(0.1f))) {
                                coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = stringResource(R.string.album_art), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = stringResource(R.string.queue_drag_handle),
                                    tint = Color.White.copy(0.3f),
                                    modifier = Modifier.size(24.dp).pointerInput(song.id) {
                                        detectVerticalDragGestures(
                                            onDragStart = { draggedSongId = song.id; dragOffsetPx = 0f },
                                            onDragEnd = {
                                                val songId = draggedSongId ?: return@detectVerticalDragGestures
                                                val itemH = 62.dp.toPx()
                                                val dIdx = displayQueue.indexOfFirst { it.id == songId }
                                                if (dIdx >= upcomingStartIdx && dragOffsetPx != 0f) {
                                                    val steps = (dragOffsetPx / itemH).roundToInt()
                                                    val target = (dIdx + steps).coerceIn(upcomingStartIdx, displayQueue.lastIndex)
                                                    if (target != dIdx) { val item = displayQueue.removeAt(dIdx); displayQueue.add(target, item) }
                                                }
                                                val realIdx = queue.indexOfFirst { it.id == songId }
                                                val dispIdx = displayQueue.indexOfFirst { it.id == songId }
                                                if (realIdx >= 0 && dispIdx >= 0 && realIdx != dispIdx) viewModel.moveQueueItem(realIdx, dispIdx)
                                                draggedSongId = null; dragOffsetPx = 0f
                                            },
                                            onDragCancel = { draggedSongId = null; dragOffsetPx = 0f },
                                            onVerticalDrag = { _, amount ->
                                                dragOffsetPx += amount
                                                val itemH = 62.dp.toPx()
                                                val dIdx = displayQueue.indexOfFirst { it.id == draggedSongId }
                                                if (dIdx < upcomingStartIdx) return@detectVerticalDragGestures
                                                val steps = (dragOffsetPx / itemH).roundToInt()
                                                val target = (dIdx + steps).coerceIn(upcomingStartIdx, displayQueue.lastIndex)
                                                if (target != dIdx && steps != 0) { val item = displayQueue.removeAt(dIdx); displayQueue.add(target, item); dragOffsetPx -= steps * itemH }
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 睡眠定时器（预设按钮 + 自定义选项，深色/浅色自适应）──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    onDismiss: () -> Unit,
    onStart: (hours: Int, minutes: Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    val textColor = if (isDark) Color.White else Color.Black
    val subTextColor = if (isDark) Color.White.copy(0.5f) else Color.Black.copy(0.5f)
    val cardBg = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)
    val btnBg = if (isDark) Color.White else Color.Black
    val btnText = if (isDark) Color.Black else Color.White

    var showCustom by remember { mutableStateOf(false) }
    var customHours by remember { mutableStateOf("") }
    var customMinutes by remember { mutableStateOf("") }

    val presets = listOf(10, 15, 20, 30, 45, 60, 90, 120)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = bgColor
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.sleep_timer_title), color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
                Text(stringResource(R.string.sleep_timer_desc), color = subTextColor, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 32.dp).padding(bottom = 20.dp))

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (row in presets.chunked(4)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { min ->
                                Surface(modifier = Modifier.weight(1f).clickable { onStart(0, min) }, shape = RoundedCornerShape(12.dp), color = cardBg) {
                                    Column(modifier = Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$min", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.sleep_timer_minute), color = subTextColor, fontSize = 11.sp)
                                    }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 10.dp).clickable { showCustom = !showCustom }, shape = RoundedCornerShape(12.dp), color = cardBg) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, null, tint = textColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.sleep_timer_custom), color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Icon(if (showCustom) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = subTextColor, modifier = Modifier.size(20.dp))
                    }
                }

                if (showCustom) {
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customHours, onValueChange = { customHours = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text(stringResource(R.string.sleep_timer_hours)) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor, focusedBorderColor = if (isDark) Color.White.copy(0.5f) else Color.Black.copy(0.3f), unfocusedBorderColor = if (isDark) Color.White.copy(0.2f) else Color.Black.copy(0.15f))
                        )
                        OutlinedTextField(
                            value = customMinutes, onValueChange = { customMinutes = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text(stringResource(R.string.sleep_timer_minutes)) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor, focusedBorderColor = if (isDark) Color.White.copy(0.5f) else Color.Black.copy(0.3f), unfocusedBorderColor = if (isDark) Color.White.copy(0.2f) else Color.Black.copy(0.15f))
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { val h = customHours.toIntOrNull() ?: 0; val m = customMinutes.toIntOrNull() ?: 0; if (h > 0 || m > 0) onStart(h, m) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = btnBg, contentColor = btnText)
                    ) {
                        Text(stringResource(R.string.sleep_timer_start_custom), fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

private val SwipeRevealCardShape = RoundedCornerShape(12.dp)

// ── Queue 分区标题 ─────────────────────────────────────
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

// ── Queue 歌曲行（已播放/正在播放，无拖拽手柄）──────
@Composable
private fun QueueSongItem(
    song: com.applemusic.clone.model.AudioItem,
    songIndex: Int,
    isActive: Boolean,
    showDragHandle: Boolean,
    viewModel: MusicViewModel
) {
    SwipeToDeleteWrapper(onDelete = { viewModel.removeFromQueue(song) }) {
        val itemBg = if (isActive) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
        Row(
            modifier = Modifier.fillMaxWidth().background(itemBg)
                .clickable { viewModel.skipToQueueIndex(songIndex) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(0.1f))) {
                coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = stringResource(R.string.album_art), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isActive) {
                Icon(Icons.Default.VolumeUp, contentDescription = stringResource(R.string.now_playing_indicator), tint = Color.White.copy(0.8f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

private val swipeExitSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMedium
)

private val swipeReturnSpring = spring<Float>(
    dampingRatio = 0.9f,
    stiffness = Spring.StiffnessHigh
)

private val swipeCancelTween = tween<Float>(240, easing = FastOutSlowInEasing)

/**
 * 右滑删除包裹组件（用于 NowPlaying 队列）
 * 圆角卡片向右飞出后再删除，列表项使用 animateItemPlacement 让下方平滑上移。
 */
@Composable
fun SwipeToDeleteWrapper(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val threshold = 90f
    val deadZoneDp = 28f
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val thresholdPx = with(density) { threshold.dp.toPx() }
    val deadZonePx = with(density) { deadZoneDp.dp.toPx() }
    val exitRightPx = with(density) { configuration.screenWidthDp.dp.toPx() * 1.25f }

    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val bgAlpha = if (offset.value < deadZonePx) 0f
        else ((offset.value - deadZonePx) / (thresholdPx * 1.5f - deadZonePx)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .clip(SwipeRevealCardShape)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFFFF3B30).copy(alpha = bgAlpha.coerceAtLeast(0f)))
        )
        if (bgAlpha > 0.02f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.swipe_delete),
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.swipe_delete), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }

        val draggableState = rememberDraggableState { delta ->
            scope.launch {
                offset.snapTo((offset.value + delta).coerceAtLeast(0f))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .clip(SwipeRevealCardShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !offset.isRunning,
                    state = draggableState,
                    onDragStopped = {
                        scope.launch {
                            if (offset.value >= thresholdPx) {
                                offset.animateTo(exitRightPx, swipeExitSpring)
                                onDelete()
                            } else {
                                offset.animateTo(0f, swipeCancelTween)
                            }
                        }
                    }
                )
        ) {
            content()
        }
    }
}

/**
 * 紧凑头部：在歌词/队列视图中显示小封面 + 歌名 + 歌手 + 收藏/更多按钮
 * 参考 Apple Music 截图中歌词页和队列页顶部的那一小排
 */
@Composable
private fun NowPlayingCompactHeader(
    song: AudioItem?,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onMore: () -> Unit,
    artworkAsPlaceholder: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 小封面
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(0.1f))
        ) {
            if (!artworkAsPlaceholder) {
                coil.compose.AsyncImage(
                    model = song?.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: "",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song?.artist ?: "",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleFav) {
            Icon(
                if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onMore) {
            Icon(
                Icons.Default.MoreHoriz,
                null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
