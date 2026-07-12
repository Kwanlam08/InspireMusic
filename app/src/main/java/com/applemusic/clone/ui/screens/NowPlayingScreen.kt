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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.applemusic.clone.R
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.model.LrcLine
import com.applemusic.clone.ui.components.FloatingGlassIconButton
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetDragHandle
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetFrame
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetModifier
import com.applemusic.clone.ui.components.LiquidGlassBottomSheetShape
import com.applemusic.clone.ui.components.LiquidGlassMenuRow
import com.applemusic.clone.ui.components.liquidGlassBottomSheetColor
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
    var blurredBitmapSongId by remember { mutableStateOf<Long?>(null) }

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
    // 整屏下滑返回累计
    var wholeScreenDragOffset by remember { mutableFloatStateOf(0f) }

    // 返回键拦截
    BackHandler {
        when {
            showMoreMenu -> showMoreMenu = false
            currentTab != 0 -> currentTab = 0
            else -> onClose()
        }
    }

    val duration = currentSong?.duration ?: 1L

    // ── 屏幕适配 ───────────────────────────────────────────
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val isLandscape = screenWidthDp > screenHeightDp
    val hPaddingValue = (screenWidthDp * 0.05f).coerceIn(12f, 40f)
    val hPadding = hPaddingValue.dp
    val coverWidthDp = (screenWidthDp - hPaddingValue * 2f).coerceAtLeast(280f)
    val tallScreenExtraDp = ((screenHeightDp - 760f).coerceIn(0f, 180f) * 0.16f)
    val coverArtworkAreaHeight = (coverWidthDp + 30f + tallScreenExtraDp)
        .coerceAtMost(screenHeightDp * 0.56f)
        .coerceAtLeast(310f)
        .dp

    // ── 主容器（整片可下滑返回，不只靠拉扣）──────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            // 整屏捕获垂直拖拽手势，下滑 > 60dp 触发返回
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { /* 启动：开始累计 */ },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) {
                            wholeScreenDragOffset += dragAmount
                            if (wholeScreenDragOffset > 60f) onClose()
                        } else {
                            wholeScreenDragOffset = 0f
                        }
                    },
                    onDragEnd = { wholeScreenDragOffset = 0f },
                    onDragCancel = { wholeScreenDragOffset = 0f }
                )
            }
    ) {
        // ── 模糊封面背景层 ─────────────────────────────────
        Crossfade(
            targetState = blurredBitmap,
            animationSpec = tween(420, easing = FastOutSlowInEasing),
            label = "blurredBackground"
        ) { bmp ->
            bmp?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(50.dp) else Modifier),
                    alpha = 0.72f
                )
            }
        }
        // 半透明蒙版让文字更清晰
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )
        if (isLandscape) {
            LandscapeNowPlayingContent(
                currentSong = currentSong,
                isPlaying = isPlaying,
                positionMs = positionMs,
                duration = duration,
                isFav = isFav,
                currentTab = currentTab,
                queue = queue,
                lyrics = lyrics,
                volumeLevel = volumeLevel,
                maxVol = maxVol,
                audioManager = audioManager,
                viewModel = viewModel,
                context = context,
                onBlurredSource = { song, bmp ->
                    if (blurredBitmapSongId != song.id) {
                        val ready = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bmp
                        } else {
                            val cfg = bmp.config ?: Bitmap.Config.ARGB_8888
                            blurBitmap(context, bmp.copy(cfg, true), 25f)
                        }
                        blurredBitmap = ready
                        blurredBitmapSongId = song.id
                    }
                },
                onVolumeLevelChange = { volumeLevel = it },
                onToggleTab = { tab -> currentTab = if (currentTab == tab) 0 else tab },
                onToggleFav = { currentSong?.let { viewModel.toggleFavorite(it.id) } },
                onMore = { showMoreMenu = true },
                onClose = onClose,
                albumScale = albumScale,
                playBtnScale = playBtnScale,
                onPlayPausePressed = {
                    isPressingPlay = true
                    viewModel.playPause()
                },
                onPlayPressSettled = { isPressingPlay = false }
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = hPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // 顶部小拉扣（仅视觉提示，整屏都已捕获下滑返回）
        var verticalDragTotal by remember { mutableFloatStateOf(0f) }
        var verticalDragActive by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)            // 顶部 20dp 放拉扣，视觉上紧贴顶部
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            verticalDragTotal = 0f
                            verticalDragActive = true
                        },
                        onVerticalDrag = { _, dragAmount ->
                            if (!verticalDragActive) return@detectVerticalDragGestures
                            verticalDragTotal += dragAmount
                            if (verticalDragTotal > 12f) onClose()    // 累计 12dp 触发
                        },
                        onDragEnd = {
                            verticalDragActive = false
                            verticalDragTotal = 0f
                        },
                        onDragCancel = {
                            verticalDragActive = false
                            verticalDragTotal = 0f
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // pill 拉扣：48×5dp 居中
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.45f))
            )
        }

            // ── 封面 / 歌词 / 队列：专辑图从封面位（顶部对齐）飞到左上角（无淡入淡出）────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (currentTab == 0) {
                            Modifier.height(coverArtworkAreaHeight)
                        } else {
                            Modifier.weight(1f)
                        }
                    )
                    .onGloballyPositioned { artworkAreaSize = it.size }
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
                                        currentPositionMs = positionMs,
                                        isPlaying = isPlaying,
                                        onSeek = viewModel::seekTo
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
                            fallbackArtwork = blurredBitmap,
                            onBlurredSource = { bmp ->
                                if (blurredBitmapSongId != song.id) {
                                    val ready = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        bmp
                                    } else {
                                        val cfg = bmp.config ?: Bitmap.Config.ARGB_8888
                                        blurBitmap(context, bmp.copy(cfg, true), 25f)
                                    }
                                    blurredBitmap = ready
                                    blurredBitmapSongId = song.id
                                }
                            }
                        )
                    }
                }
            }

            // ── 封面 tab: 歌曲信息 + 控件 ──────────────────
            if (currentTab == 0) {
                Spacer(Modifier.height(2.dp))   // 封面与标题间距（封面 100% 大）
                // 歌曲信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // 切歌时标题也淡入（避免突兀）
                        val titleAlpha = remember(currentSong?.id) { Animatable(0f) }
                        LaunchedEffect(currentSong?.id) {
                            titleAlpha.snapTo(0f)
                            titleAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                        }
                        Text(
                            text = currentSong?.title ?: stringResource(R.string.np_not_playing),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = titleAlpha.value }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = currentSong?.artist ?: stringResource(R.string.np_unknown_artist),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = titleAlpha.value }
                        )
                    }
                    IconButton(
                        onClick = { currentSong?.let { viewModel.toggleFavorite(it.id) } },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.np_favorite),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(46.dp)) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = stringResource(R.string.np_more),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
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
                        .height(34.dp)
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
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White.copy(0.18f))
                            .border(
                                0.7.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(0.32f),
                                        Color.Black.copy(0.18f)
                                    )
                                ),
                                RoundedCornerShape(99.dp)
                            )
                    )
                    // 已播放轨
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = displayFraction.coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(0.92f),
                                        Color.White.copy(0.70f)
                                    )
                                )
                            )
                    )
                    // 滑块圆点
                    if (barWidthPx > 0f) {
                        val thumbPx = with(density) { 20.dp.toPx() }
                        val thumbOffsetX = (barWidthPx * displayFraction - thumbPx / 2f)
                            .coerceIn(0f, barWidthPx - thumbPx)
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(thumbOffsetX.roundToInt(), 0) }
                                .width(20.dp)
                                .height(12.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(99.dp),
                                    spotColor = Color.Black.copy(alpha = 0.28f),
                                    ambientColor = Color.Black.copy(alpha = 0.10f)
                                )
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White.copy(0.92f))
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

                // 播放控制（圆角图标、略上移）。SkipPrev/Next 缩小到 56dp
                // 防止误触切歌（之前 72dp 太大，很容易碰到就切）。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-8).dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.skipPrev() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.np_previous),
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
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
                    IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.np_next),
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
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
                    var volumeBarWidthPx by remember { mutableFloatStateOf(0f) }
                    fun setVolumeFraction(fraction: Float) {
                        val v = fraction.coerceIn(0f, 1f)
                        volumeLevel = v
                        if (maxVol > 0) {
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (v * maxVol).roundToInt().coerceIn(0, maxVol),
                                0
                            )
                        }
                    }
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .padding(horizontal = 8.dp)
                            .onGloballyPositioned { volumeBarWidthPx = it.size.width.toFloat() }
                            .pointerInput(maxVol) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        setVolumeFraction(offset.x / size.width)
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        setVolumeFraction(volumeLevel + dragAmount / size.width)
                                    }
                                )
                            }
                            .pointerInput(maxVol) {
                                detectTapGestures { offset ->
                                    setVolumeFraction(offset.x / size.width)
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White.copy(0.15f))
                                .border(
                                    0.7.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(0.26f),
                                            Color.Black.copy(0.16f)
                                        )
                                    ),
                                    RoundedCornerShape(99.dp)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = volumeLevel.coerceIn(0f, 1f))
                                .height(7.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White.copy(0.72f))
                        )
                        if (volumeBarWidthPx > 0f) {
                            val volumeThumbPx = with(density) { 18.dp.toPx() }
                            val thumbOffsetX = (volumeBarWidthPx * volumeLevel.coerceIn(0f, 1f) - volumeThumbPx / 2f)
                                .coerceIn(0f, volumeBarWidthPx - volumeThumbPx)
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(thumbOffsetX.roundToInt(), 0) }
                                    .width(18.dp)
                                    .height(11.dp)
                                    .shadow(
                                        elevation = 7.dp,
                                        shape = RoundedCornerShape(99.dp),
                                        spotColor = Color.Black.copy(alpha = 0.22f),
                                        ambientColor = Color.Black.copy(alpha = 0.08f)
                                    )
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Color.White.copy(0.88f))
                            )
                        }
                    }
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White.copy(0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } // end if currentTab == 0

            // 底部 tab 切换（始终显示）
            if (currentTab == 0) {
                Spacer(Modifier.weight(1f))
            }

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
    }

    // ── 睡眠定时器设置弹窗 (预设按钮) ─────────────────
    if (showSleepTimerMenu) {
        SleepTimerSheet(
            onDismiss = { showSleepTimerMenu = false },
            remainingMs = sleepTimerMs,
            onStart = { durationMs -> viewModel.startSleepTimer(durationMs) },
            onCancel = { viewModel.cancelSleepTimer() }
        )
    }

    // ── 更多菜单（自定义滑入动画：每行 stagger） ─────────────
    if (showMoreMenu && !showSleepTimerMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            modifier = LiquidGlassBottomSheetModifier,
            containerColor = Color.Transparent,
            shape = LiquidGlassBottomSheetShape,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = 0.46f)
        ) {
            LiquidGlassBottomSheetFrame(useSharedBackdrop = false) {
                Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp, top = 8.dp)
                ) {
                // 标题：fadeIn 立即出现
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

                // 菜单项：依次 stagger 进入
                data class MenuItem(
                    val icon: androidx.compose.ui.graphics.vector.ImageVector,
                    val label: String,
                    val iconTint: Color,
                    val onClick: () -> Unit
                )
                val menuItems = listOf(
                    MenuItem(
                        Icons.Default.Album,
                        stringResource(R.string.menu_view_album),
                        MaterialTheme.colorScheme.primary
                    ) {
                        showMoreMenu = false
                        currentSong?.album?.let { onNavigateToAlbum(it) }
                    },
                    MenuItem(
                        Icons.Default.Person,
                        stringResource(R.string.menu_view_artist),
                        MaterialTheme.colorScheme.primary
                    ) {
                        showMoreMenu = false
                        currentSong?.artist?.let { onNavigateToArtist(it) }
                    },
                    MenuItem(
                        if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (isFav) stringResource(R.string.menu_remove_fav) else stringResource(R.string.menu_add_fav),
                        MaterialTheme.colorScheme.primary
                    ) {
                        showMoreMenu = false
                        currentSong?.let { viewModel.toggleFavorite(it.id) }
                    },
                    MenuItem(
                        Icons.Default.Timer,
                        when {
                            viewModel.isPauseAfterSongMode -> "播完当前歌曲后暂停 (已开启)"
                            sleepTimerMs != null -> stringResource(R.string.sleep_timer_active_label, viewModel.formatDuration(sleepTimerMs!!))
                            else -> stringResource(R.string.menu_sleep_timer)
                        },
                        MaterialTheme.colorScheme.primary
                    ) {
                        showMoreMenu = false
                        showSleepTimerMenu = true
                    }
                )

                menuItems.forEachIndexed { index, item ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(50L + index * 50L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(220)) + androidx.compose.animation.slideInHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) { -it / 3 },
                        exit = fadeOut(tween(150))
                    ) {
                        LiquidGlassMenuRow(
                            icon = item.icon,
                            label = item.label,
                            iconTint = item.iconTint,
                            onClick = item.onClick
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun LandscapeNowPlayingContent(
    currentSong: AudioItem?,
    isPlaying: Boolean,
    positionMs: Long,
    duration: Long,
    isFav: Boolean,
    currentTab: Int,
    queue: List<AudioItem>,
    lyrics: List<LrcLine>,
    volumeLevel: Float,
    maxVol: Int,
    audioManager: AudioManager,
    viewModel: MusicViewModel,
    context: Context,
    onBlurredSource: (AudioItem, Bitmap) -> Unit,
    onVolumeLevelChange: (Float) -> Unit,
    onToggleTab: (Int) -> Unit,
    onToggleFav: () -> Unit,
    onMore: () -> Unit,
    onClose: () -> Unit,
    albumScale: Float,
    playBtnScale: Float,
    onPlayPausePressed: () -> Unit,
    onPlayPressSettled: () -> Unit
) {
    val configuration = LocalConfiguration.current
    // Keep a generous right-hand control area on short landscape screens. The old
    // ratio let the artwork consume the lyric/queue pane and caused clipping.
    val coverSide = minOf(configuration.screenHeightDp * 0.56f, configuration.screenWidthDp * 0.31f)
        .coerceIn(156f, 460f)
        .dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .clipToBounds()
            .padding(horizontal = 32.dp, vertical = 10.dp)
    ) {
        LandscapeGrabber(
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(coverSide)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                currentSong?.let { song ->
                    LandscapeAlbumArtwork(
                        song = song,
                        context = context,
                        onBlurredSource = { onBlurredSource(song, it) },
                        modifier = Modifier
                            .size(coverSide)
                            .scale(albumScale)
                    )
                } ?: Box(
                    modifier = Modifier
                        .size(coverSide)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
            Spacer(Modifier.width(34.dp))
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                label = "landscapeNowPlayingTabs",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { tab ->
                when (tab) {
                    1 -> LandscapeLyricsPane(
                        currentSong = currentSong,
                        isFav = isFav,
                        lyrics = lyrics,
                        positionMs = positionMs,
                        isPlaying = isPlaying,
                        viewModel = viewModel,
                        onToggleFav = onToggleFav,
                        onMore = onMore,
                        onToggleTab = onToggleTab
                    )
                    2 -> LandscapeQueuePane(
                        currentSong = currentSong,
                        isFav = isFav,
                        queue = queue,
                        viewModel = viewModel,
                        onToggleFav = onToggleFav,
                        onMore = onMore,
                        onToggleTab = onToggleTab
                    )
                    else -> LandscapePlayerPane(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        duration = duration,
                        isFav = isFav,
                        volumeLevel = volumeLevel,
                        maxVol = maxVol,
                        audioManager = audioManager,
                        viewModel = viewModel,
                        onVolumeLevelChange = onVolumeLevelChange,
                        onToggleFav = onToggleFav,
                        onMore = onMore,
                        playBtnScale = playBtnScale,
                        onPlayPausePressed = onPlayPausePressed,
                        onPlayPressSettled = onPlayPressSettled,
                        onToggleTab = onToggleTab
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeGrabber(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var verticalDragTotal by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .width(86.dp)
            .height(20.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { verticalDragTotal = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) {
                            verticalDragTotal += dragAmount
                            if (verticalDragTotal > 12f) onClose()
                        }
                    },
                    onDragEnd = { verticalDragTotal = 0f },
                    onDragCancel = { verticalDragTotal = 0f }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.48f))
        )
    }
}

@Composable
private fun LandscapeAlbumArtwork(
    song: AudioItem,
    context: Context,
    onBlurredSource: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    var displayedArtwork by remember { mutableStateOf<Bitmap?>(null) }
    var displayedArtworkSongId by remember { mutableStateOf<Long?>(null) }
    Box(
        modifier = modifier
            .shadow(
                elevation = 22.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.16f),
                spotColor = Color.Black.copy(alpha = 0.22f),
                clip = false
            )
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
    ) {
        coil.compose.SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.albumArtUri)
                .size(1024)
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
                    if (displayedArtworkSongId != song.id) {
                        displayedArtwork = bmp
                        displayedArtworkSongId = song.id
                    }
                    onBlurredSource(bmp)
                }
                SubcomposeAsyncImageContent()
            } else {
                displayedArtwork?.let { previous ->
                    Image(
                        bitmap = previous.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                if (displayedArtwork == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2A2A3E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeSongHeader(
    currentSong: AudioItem?,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentSong?.title ?: stringResource(R.string.np_not_playing),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentSong?.artist ?: stringResource(R.string.np_unknown_artist),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleFav, modifier = Modifier.size(48.dp)) {
            Icon(if (isFav) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.np_favorite), tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onMore, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.MoreHoriz, stringResource(R.string.np_more), tint = Color.White, modifier = Modifier.size(29.dp))
        }
    }
}

@Composable
private fun LandscapePlayerPane(
    currentSong: AudioItem?,
    isPlaying: Boolean,
    positionMs: Long,
    duration: Long,
    isFav: Boolean,
    volumeLevel: Float,
    maxVol: Int,
    audioManager: AudioManager,
    viewModel: MusicViewModel,
    onVolumeLevelChange: (Float) -> Unit,
    onToggleFav: () -> Unit,
    onMore: () -> Unit,
    playBtnScale: Float,
    onPlayPausePressed: () -> Unit,
    onPlayPressSettled: () -> Unit,
    onToggleTab: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 22.dp)
    ) {
        LandscapeTabSwitcher(
            currentTab = 0,
            onToggleTab = onToggleTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 2.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center
        ) {
            LandscapeSongHeader(
                currentSong = currentSong,
                isFav = isFav,
                onToggleFav = onToggleFav,
                onMore = onMore
            )
            Spacer(Modifier.height(28.dp))
            LandscapeProgressControl(duration = duration, positionMs = positionMs, viewModel = viewModel)
            Spacer(Modifier.height(18.dp))
            LandscapePlaybackControls(
                isPlaying = isPlaying,
                playBtnScale = playBtnScale,
                onPrevious = viewModel::skipPrev,
                onPlayPausePressed = onPlayPausePressed,
                onPlayPressSettled = onPlayPressSettled,
                onNext = viewModel::skipNext
            )
            Spacer(Modifier.height(16.dp))
            LandscapeVolumeControl(
                volumeLevel = volumeLevel,
                maxVol = maxVol,
                audioManager = audioManager,
                onVolumeLevelChange = onVolumeLevelChange
            )
        }
    }
}

@Composable
private fun LandscapeLyricsPane(
    currentSong: AudioItem?,
    isFav: Boolean,
    lyrics: List<LrcLine>,
    positionMs: Long,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    onToggleFav: () -> Unit,
    onMore: () -> Unit,
    onToggleTab: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 20.dp)
    ) {
        LandscapeSongHeader(
            currentSong = currentSong,
            isFav = isFav,
            onToggleFav = onToggleFav,
            onMore = onMore,
            modifier = Modifier.padding(top = 16.dp)
        )
        Spacer(Modifier.height(20.dp))
        LyricsView(
            lyrics = lyrics,
            currentPositionMs = positionMs,
            isPlaying = isPlaying,
            onSeek = viewModel::seekTo,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            lyricHorizontalPadding = 52.dp,
            lyricFocusFraction = 0.28f,
            lyricTopPadding = 6.dp,
            lyricBottomPadding = 18.dp
        )
        LandscapeTabSwitcher(
            currentTab = 1,
            onToggleTab = onToggleTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }
}

@Composable
private fun LandscapeQueuePane(
    currentSong: AudioItem?,
    isFav: Boolean,
    queue: List<AudioItem>,
    viewModel: MusicViewModel,
    onToggleFav: () -> Unit,
    onMore: () -> Unit,
    onToggleTab: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 20.dp)
    ) {
        LandscapeSongHeader(
            currentSong = currentSong,
            isFav = isFav,
            onToggleFav = onToggleFav,
            onMore = onMore,
            modifier = Modifier.padding(top = 16.dp)
        )
        Spacer(Modifier.height(16.dp))
        QueueView(
            queue = queue,
            currentSong = currentSong,
            viewModel = viewModel,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        LandscapeTabSwitcher(
            currentTab = 2,
            onToggleTab = onToggleTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }
}

@Composable
private fun LandscapeProgressControl(
    duration: Long,
    positionMs: Long,
    viewModel: MusicViewModel
) {
    val density = LocalDensity.current
    val progress = if (duration > 0) positionMs.toFloat() / duration.toFloat() else 0f
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val displayFraction = if (isDragging) dragFraction else progress
    var barWidthPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .onGloballyPositioned { barWidthPx = it.size.width.toFloat() }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        viewModel.seekTo((dragFraction * duration).toLong())
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { _, dragAmount ->
                        dragFraction = (dragFraction + dragAmount / size.width).coerceIn(0f, 1f)
                    }
                )
            }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    viewModel.seekTo((fraction * duration).toLong())
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(displayFraction.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.86f))
        )
        if (barWidthPx > 0f) {
            val thumbPx = with(density) { 18.dp.toPx() }
            val thumbOffsetX = (barWidthPx * displayFraction.coerceIn(0f, 1f) - thumbPx / 2f)
                .coerceIn(0f, barWidthPx - thumbPx)
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetX.roundToInt(), 0) }
                    .width(18.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.92f))
            )
        }
    }
    val displayPositionMs = if (isDragging) (dragFraction * duration).toLong() else positionMs
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            viewModel.formatDuration(displayPositionMs),
            color = Color.White.copy(alpha = 0.52f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "-${viewModel.formatDuration((duration - displayPositionMs).coerceAtLeast(0))}",
            color = Color.White.copy(alpha = 0.52f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LandscapePlaybackControls(
    isPlaying: Boolean,
    playBtnScale: Float,
    onPrevious: () -> Unit,
    onPlayPausePressed: () -> Unit,
    onPlayPressSettled: () -> Unit,
    onNext: () -> Unit
) {
    LaunchedEffect(playBtnScale) {
        if (playBtnScale < 0.98f) {
            delay(120)
            onPlayPressSettled()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(66.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.np_previous),
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(84.dp)
                .scale(playBtnScale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onPlayPausePressed() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "播放/暂停",
                tint = Color.White,
                modifier = Modifier.size(72.dp)
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(66.dp)) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.np_next),
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun LandscapeVolumeControl(
    volumeLevel: Float,
    maxVol: Int,
    audioManager: AudioManager,
    onVolumeLevelChange: (Float) -> Unit
) {
    val density = LocalDensity.current
    var volumeBarWidthPx by remember { mutableFloatStateOf(0f) }
    fun setVolumeFraction(fraction: Float) {
        val v = fraction.coerceIn(0f, 1f)
        onVolumeLevelChange(v)
        if (maxVol > 0) {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (v * maxVol).roundToInt().coerceIn(0, maxVol),
                0
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.VolumeDown,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.46f),
            modifier = Modifier.size(18.dp)
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .padding(horizontal = 12.dp)
                .onGloballyPositioned { volumeBarWidthPx = it.size.width.toFloat() }
                .pointerInput(maxVol) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> setVolumeFraction(offset.x / size.width) },
                        onHorizontalDrag = { _, dragAmount ->
                            setVolumeFraction(volumeLevel + dragAmount / size.width)
                        }
                    )
                }
                .pointerInput(maxVol) {
                    detectTapGestures { offset -> setVolumeFraction(offset.x / size.width) }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(volumeLevel.coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.72f))
            )
            if (volumeBarWidthPx > 0f) {
                val thumbPx = with(density) { 17.dp.toPx() }
                val thumbOffsetX = (volumeBarWidthPx * volumeLevel.coerceIn(0f, 1f) - thumbPx / 2f)
                    .coerceIn(0f, volumeBarWidthPx - thumbPx)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(thumbOffsetX.roundToInt(), 0) }
                        .width(17.dp)
                        .height(11.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.88f))
                )
            }
        }
        Icon(
            Icons.Default.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.46f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun LandscapeTabSwitcher(
    currentTab: Int,
    onToggleTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onToggleTab(1) }, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Default.FormatQuote,
                contentDescription = stringResource(R.string.np_tab_lyrics),
                tint = if (currentTab == 1) Color.White else Color.White.copy(alpha = 0.48f),
                modifier = Modifier.size(30.dp)
            )
        }
        IconButton(onClick = { onToggleTab(2) }, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = stringResource(R.string.np_tab_queue),
                tint = if (currentTab == 2) Color.White else Color.White.copy(alpha = 0.48f),
                modifier = Modifier.size(30.dp)
            )
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
    fallbackArtwork: Bitmap?,
    onBlurredSource: (Bitmap) -> Unit
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val wPx = containerSize.width.toFloat()
    val hPx = containerSize.height.toFloat()
    // 封面：1:1，宽度 = min(w, h) 的 100%（铺满 flex 区域）
    val bigSide = minOf(wPx, hPx) * 1.0f
    // 顶部只留 8dp 固定间距
    val minTop = with(density) { 8.dp.toPx() }
    val maxTop = with(density) { 34.dp.toPx() }
    val bigTop = ((hPx - bigSide) * 0.44f).coerceIn(minTop, maxTop)
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

    // 切歌时淡入淡出（避免闪屏）：用 song.id 做 key，每次换歌 alpha 0→1
    // Preserve the last decoded cover while the next local file is loading.
    var displayedArtwork by remember { mutableStateOf<Bitmap?>(null) }
    var displayedArtworkSongId by remember { mutableStateOf<Long?>(null) }

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
                    .size(1024)
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
                        if (displayedArtworkSongId != song.id) {
                            displayedArtwork = bmp
                            displayedArtworkSongId = song.id
                        }
                        onBlurredSource(bmp)
                    }
                    SubcomposeAsyncImageContent()
                } else {
                    (displayedArtwork ?: fallbackArtwork)?.let { previous ->
                        Image(
                            bitmap = previous.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (displayedArtwork == null && fallbackArtwork == null) {
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
}

/**
 * 歌词区：随封面 morph 进度由模糊过渡到清晰（API 31+ 用 blur，以下用透明度）。
 */
@Composable
private fun NowPlayingLyricsWithBlur(
    morphProgress: Float,
    lyrics: List<LrcLine>,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit
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
            currentPositionMs = currentPositionMs,
            isPlaying = isPlaying,
            onSeek = onSeek,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ── 歌词视图 ──────────────────────────────────────────────
@Composable
fun LyricsView(
    lyrics: List<LrcLine>,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lyricHorizontalPadding: Dp = 18.dp,
    lyricFocusFraction: Float = 0.32f,
    lyricTopPadding: Dp = 18.dp,
    lyricBottomPadding: Dp = 64.dp
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
    } else if (lyrics.none { it.isSynced }) {
        StaticLyricsView(
            lyrics = lyrics,
            modifier = modifier,
            horizontalPadding = lyricHorizontalPadding,
            topPadding = lyricTopPadding,
            bottomPadding = lyricBottomPadding
        )
    } else {
        SyncedStepLyricsView(
            lyrics = lyrics,
            currentPositionMs = currentPositionMs,
            onSeek = onSeek,
            modifier = modifier,
            horizontalPadding = lyricHorizontalPadding,
            focusFraction = lyricFocusFraction,
            contentTopPadding = lyricTopPadding,
            contentBottomPadding = lyricBottomPadding
        )
    }
}

// ── 播放列表视图 (Queue) ──────────────────────────────────
@Composable
private fun SyncedStepLyricsView(
    lyrics: List<LrcLine>,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 18.dp,
    focusFraction: Float = 0.32f,
    contentTopPadding: Dp = 18.dp,
    contentBottomPadding: Dp = 64.dp
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isBrowsingLyrics by remember { mutableStateOf(false) }
    var isAutoScrolling by remember { mutableStateOf(false) }
    val currentIndex by remember(lyrics, currentPositionMs) {
        derivedStateOf {
            lyrics.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { isScrolling ->
            if (isScrolling && !isAutoScrolling) isBrowsingLyrics = true
        }
    }

    LaunchedEffect(currentIndex, lyrics.size, isBrowsingLyrics) {
        if (isBrowsingLyrics) return@LaunchedEffect
        if (currentIndex !in lyrics.indices) return@LaunchedEffect
        val focusOffsetPx = (listState.layoutInfo.viewportSize.height *
            focusFraction.coerceIn(0.20f, 0.45f)).roundToInt()
        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
        isAutoScrolling = true
        try {
            if (visibleItem == null) {
                listState.animateScrollToItem(index = currentIndex, scrollOffset = -focusOffsetPx)
            } else {
                val delta = (visibleItem.offset - focusOffsetPx).toFloat()
                if (kotlin.math.abs(delta) > with(density) { 2.dp.toPx() }) {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = tween(durationMillis = 430, easing = FastOutSlowInEasing)
                    )
                }
            }
        } finally {
            isAutoScrolling = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = contentTopPadding,
                bottom = contentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        itemsIndexed(
            items = lyrics,
            key = { index, line -> "${line.timeMs}-$index-${line.text}" }
        ) { index, line ->
            val selected = index == currentIndex
            val distance = kotlin.math.abs(index - currentIndex).coerceAtMost(4)
            val targetAlpha = when {
                selected -> 1f
                distance == 1 -> 0.54f
                distance == 2 -> 0.32f
                else -> 0.18f
            }
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(220, easing = LinearOutSlowInEasing),
                label = "stepLyricAlpha"
            )
            Text(
                text = line.text,
                color = Color.White.copy(alpha = alpha),
                // Keep the measured text box stable while the active line changes.
                // Increasing the font size here makes long lyrics reflow mid-scroll.
                fontSize = 24.sp,
                // Keep the active line at the previous bold width so long lyrics do not
                // reflow when the emphasis changes. Nearby lines step down in weight.
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                lineHeight = 32.sp,
                style = TextStyle(
                    lineBreak = LineBreak.Paragraph,
                    hyphens = Hyphens.Auto
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSeek(line.timeMs) }
            )
        }
        }

        AnimatedVisibility(
            visible = isBrowsingLyrics,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentBottomPadding + 14.dp),
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(140))
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.16f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                onClick = {
                    isBrowsingLyrics = false
                    scope.launch {
                        isAutoScrolling = true
                        try {
                            listState.animateScrollToItem(currentIndex.coerceIn(0, lyrics.lastIndex))
                        } finally {
                            isAutoScrolling = false
                        }
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("回到当前歌词", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StaticLyricsView(
    lyrics: List<LrcLine>,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 18.dp,
    topPadding: Dp = 34.dp,
    bottomPadding: Dp = 96.dp
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = topPadding,
                    bottom = bottomPadding
                ),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            lyrics.forEach { line ->
                Text(
                    text = line.text,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                    style = TextStyle(
                        lineBreak = LineBreak.Paragraph,
                        hyphens = Hyphens.Auto
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

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
        // 已播放/正在播放/待播分区共用状态
        var draggedSongId by remember { mutableStateOf<Long?>(null) }
        var dragStartIndex by remember { mutableIntStateOf(-1) }
        // dragOffsetPx: 当前拖拽项相对其所在行的剩余偏移量。
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        val haptic = LocalHapticFeedback.current
        
        // 本地可变副本：拖拽中直接改此列表（push-aside 效果），不碰 controller
        val displayQueue = remember { mutableStateListOf<AudioItem>() }
        // 没有拖拽活动时，始终以播放器真实 queue 为准。
        LaunchedEffect(queue) {
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
                            .clip(RoundedCornerShape(20.dp))
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
                            .clip(RoundedCornerShape(20.dp))
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

            // 已播放（不可拖拽、不可右滑删除：避免误触切歌）
            if (playedSongs.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.queue_played), playedSongs.size) }
                items(playedSongs.size) { i ->
                    QueueSongItem(
                        song = playedSongs[i],
                        isActive = false
                    )
                }
            }

            // 正在播放（不可拖拽、不可右滑删除：避免误触切歌）
            theCurrentSong?.let { song ->
                item { SectionHeader(stringResource(R.string.queue_playing), 1) }
                item {
                    QueueSongItem(
                        song = song,
                        isActive = true
                    )
                }
            }

            // 待播（可拖拽，范围限制 upcomingStartIdx..lastIndex）
            if (upcomingSongs.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.queue_upcoming), upcomingSongs.size) }
                itemsIndexed(
                    items = upcomingSongs,
                    key = { _, song -> song.id }
                ) { _, song ->
                    val isDragged = song.id == draggedSongId
                    val anyDragging = draggedSongId != null

                    val colMod = Modifier
                        .animateItem(
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        .zIndex(if (isDragged) 1f else 0f)
                        .then(
                            if (isDragged) {
                                Modifier.graphicsLayer {
                                    translationY = dragOffsetPx
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                }
                            } else {
                                Modifier
                            }
                        )

                    Column(modifier = colMod) {
                        // 拖拽进行时禁用左滑删除，防止水平手势与垂直拖拽冲突
                        SwipeToDeleteWrapper(
                            onDelete = { viewModel.removeFromQueue(song) },
                            enabled = !anyDragging
                        ) {
                            // 整行不再 clickable：防止误触切歌。拖动换位通过右侧手柄长按实现
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.1f))) {
                                coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = stringResource(R.string.album_art), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                // 行高约 60dp（封面 46dp + 上下 padding）
                                val rowHeightPx: Float = with(LocalDensity.current) { 58.dp.toPx() }
                                // 右侧拖动手柄：3 条横线（DragHandle Icon）+ 整手柄可长按拖动
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .pointerInput(song.id, upcomingSongs.size, upcomingStartIdx) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    val from = displayQueue.indexOfFirst { it.id == song.id }
                                                    draggedSongId = song.id
                                                    dragStartIndex = from
                                                    dragOffsetPx = 0f
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
                                                },
                                                onDragEnd = {
                                                    val from = dragStartIndex
                                                    val to = displayQueue.indexOfFirst { it.id == song.id }
                                                    dragOffsetPx = 0f
                                                    draggedSongId = null
                                                    dragStartIndex = -1
                                                    if (from >= 0 && to != from) {
                                                        viewModel.moveQueueItem(from, to)
                                                    }
                                                },
                                                onDragCancel = {
                                                    dragOffsetPx = 0f
                                                    draggedSongId = null
                                                    dragStartIndex = -1
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetPx += dragAmount.y
                                                    while (dragOffsetPx >= rowHeightPx || dragOffsetPx <= -rowHeightPx) {
                                                        val direction = if (dragOffsetPx > 0f) 1 else -1
                                                        val from = displayQueue.indexOfFirst { it.id == song.id }
                                                        val target = (from + direction).coerceIn(upcomingStartIdx, displayQueue.lastIndex)
                                                        if (from >= 0 && target != from) {
                                                            val item = displayQueue.removeAt(from)
                                                            displayQueue.add(target, item)
                                                            dragOffsetPx -= direction * rowHeightPx
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        } else {
                                                            dragOffsetPx = 0f
                                                            break
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = stringResource(R.string.queue_drag_handle),
                                        tint = Color.White.copy(0.55f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 睡眠定时器（v3：stagger 动画 + 苹果风格大圆角 + 选中态高亮） ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    onDismiss: () -> Unit,
    remainingMs: Long?,
    onStart: (durationMs: Long) -> Unit,
    onCancel: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val subTextColor = if (isDark) Color.White.copy(0.55f) else Color.Black.copy(0.55f)
    val presetBg = if (isDark) Color.White.copy(0.070f) else Color.White.copy(0.36f)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCustom by remember { mutableStateOf(false) }
    var customHours by remember { mutableStateOf("") }
    var customMinutes by remember { mutableStateOf("") }
    // 当前选中的预设（用于高亮显示）
    var selectedPreset by remember { mutableIntStateOf(-1) }
    var startedDurationMs by remember { mutableLongStateOf((remainingMs ?: 0L).coerceAtLeast(0L)) }

    // 预设：8 个 → 4 列 × 2 行
    val presets = listOf(5, 10, 15, 30, 45, 60, 75, 90)
    val isTimerActive = remainingMs != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = LiquidGlassBottomSheetModifier,
        containerColor = Color.Transparent,
        shape = LiquidGlassBottomSheetShape,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.42f)
    ) {
        LiquidGlassBottomSheetFrame(useSharedBackdrop = false) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
            // 标题区：圆形 timer 图标 + 标题 + 副标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.sleep_timer_title),
                        color = textColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.sleep_timer_desc),
                        color = subTextColor,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(0.08f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            // 预设时间：BoxWithConstraints 精准计算每个圆点尺寸 → 严格 4×2 网格对齐
            // 圆形图标只显示纯数字（去掉"分钟"单位）
            if (isTimerActive) {
                SleepTimerCountdownPanel(
                    remainingMs = remainingMs ?: 0L,
                    totalMs = startedDurationMs.takeIf { it > 0L } ?: (remainingMs ?: 1L),
                    onCancel = {
                        selectedPreset = -1
                        startedDurationMs = 0L
                        onCancel()
                    }
                )
            } else {
            val presetCols = 4
            val presetRows = presets.chunked(presetCols)
            val horizontalPadding = 20.dp
            val interItemGap = 16.dp
            val interRowGap = 16.dp
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            ) {
                val available = maxWidth - interItemGap * (presetCols - 1)
                val itemSize = available / presetCols
                Column(
                    verticalArrangement = Arrangement.spacedBy(interRowGap)
                ) {
                    presetRows.forEachIndexed { rowIdx, row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(interItemGap)
                        ) {
                            row.forEachIndexed { colIdx, min ->
                                val globalIdx = rowIdx * presetCols + colIdx
                                var visible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    kotlinx.coroutines.delay(40L + globalIdx * 50L)
                                    visible = true
                                }
                                val isSelected = selectedPreset == globalIdx
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = androidx.compose.animation.fadeIn(tween(220)) +
                                            androidx.compose.animation.scaleIn(
                                                initialScale = 0.6f,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                            ),
                                    exit = androidx.compose.animation.fadeOut(tween(150))
                                ) {
                                    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val pressScale by animateFloatAsState(
                                        targetValue = if (isPressed) 0.88f else 1f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        label = "preset_press"
                                    )
                                    // 圆形图标：宽度由 BoxWithConstraints 算出来，所有按钮大小一致
                                    Box(
                                        modifier = Modifier
                                            .size(itemSize)
                                            .scale(pressScale)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else presetBg)
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null
                                            ) {
                                                selectedPreset = globalIdx
                                                val duration = min * 60_000L
                                                startedDurationMs = duration
                                                onStart(duration)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$min",
                                            color = if (isSelected) Color.White else textColor,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 自定义时间入口（可展开）
            SleepTimerMenuRow(
                icon = Icons.Default.Edit,
                label = stringResource(R.string.sleep_timer_custom),
                expanded = showCustom,
                onClick = { showCustom = !showCustom }
            )

            AnimatedVisibility(
                visible = showCustom,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(260)
                ) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(200)
                ) + androidx.compose.animation.fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customHours,
                            onValueChange = { customHours = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text(stringResource(R.string.sleep_timer_hours)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { customMinutes = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text(stringResource(R.string.sleep_timer_minutes)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val h = customHours.toIntOrNull() ?: 0
                            val m = customMinutes.toIntOrNull() ?: 0
                            if (h > 0 || m > 0) {
                                val duration = h * 3_600_000L + m * 60_000L
                                selectedPreset = -1
                                startedDurationMs = duration
                                onStart(duration)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(stringResource(R.string.sleep_timer_start_custom), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Apple 风格的菜单行：图标 + 文字 + 右侧展开/收起箭头
 */
}

}

@Composable
private fun SleepTimerCountdownPanel(
    remainingMs: Long,
    totalMs: Long,
    onCancel: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val progress by animateFloatAsState(
        targetValue = if (remainingMs < 0L) 1f else (remainingMs.toFloat() / totalMs.coerceAtLeast(1L)).coerceIn(0f, 1f),
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "sleepTimerProgress"
    )
    val textColor = if (isDark) Color.White else Color.Black
    val subTextColor = if (isDark) Color.White.copy(0.58f) else Color.Black.copy(0.56f)
    val ringBase = if (isDark) Color.White.copy(0.13f) else Color.Black.copy(0.10f)
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 11.dp.toPx()
                drawCircle(
                    color = ringBase,
                    radius = (size.minDimension - strokeWidth) / 2f,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (remainingMs < 0L) stringResource(R.string.sleep_timer_waiting) else formatSleepCountdown(remainingMs),
                    color = textColor,
                    fontSize = if (remainingMs < 0L) 13.sp else 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.sleep_timer_title),
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (remainingMs < 0L) {
                    stringResource(R.string.sleep_timer_waiting)
                } else {
                    stringResource(R.string.sleep_timer_active_label, formatSleepCountdown(remainingMs))
                },
                color = subTextColor,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(42.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = accent,
                    containerColor = if (isDark) Color.White.copy(0.055f) else Color.White.copy(0.34f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.34f else 0.56f),
                            Color.Black.copy(alpha = if (isDark) 0.24f else 0.14f)
                        )
                    )
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.sleep_timer_cancel), fontWeight = FontWeight.SemiBold)
            }
        }
        }
    }

@Composable
private fun NowPlayingGlassMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.105f else 0.40f),
                        Color.White.copy(alpha = if (isDark) 0.035f else 0.17f),
                        Color.Black.copy(alpha = if (isDark) 0.070f else 0.022f)
                    )
                ),
                shape
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.34f else 0.60f),
                        Color.Black.copy(alpha = if (isDark) 0.28f else 0.16f)
                    )
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconTint.copy(alpha = if (isDark) 0.13f else 0.09f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = if (isDark) Color.White else Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatSleepCountdown(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun SleepTimerMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    val subTextColor = if (isSystemInDarkTheme()) Color.White.copy(0.55f) else Color.Black.copy(0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            color = textColor,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = subTextColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

private val SwipeRevealCardShape = RoundedCornerShape(16.dp)

// ── Queue 分区标题 ─────────────────────────────────────
@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = count.toString(),
            color = Color.White.copy(alpha = 0.32f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Queue 歌曲行（已播放/正在播放，无拖拽手柄、无右滑删除）──────
@Composable
private fun QueueSongItem(
    song: com.applemusic.clone.model.AudioItem,
    isActive: Boolean
) {
    // 已播放/正在播放行强制禁用 SwipeToDelete（即使手滑也删不掉）
    // 删除当前曲会让播放器自动跳到下一首，严重影响使用
    val noopDelete: () -> Unit = { /* 已播放区/正在播放区禁止删除 */ }
    SwipeToDeleteWrapper(onDelete = noopDelete, enabled = false) {
        val itemBg = if (isActive) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
        Row(
            modifier = Modifier.fillMaxWidth().background(itemBg)
                // 已播放/正在播放行不添加 clickable，防止误触跳歌
                // 用户若想跳到某首，可在待播区拖拽
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.1f))) {
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

private val swipeCancelTween = tween<Float>(240, easing = FastOutSlowInEasing)

/**
 * 右滑删除包裹组件（用于 NowPlaying 队列）
 * 圆角卡片向右飞出后再删除，列表项使用 animateItemPlacement 让下方平滑上移。
 */
@Composable
fun SwipeToDeleteWrapper(
    onDelete: () -> Unit,
    enabled: Boolean = true,   // false 时禁用水平滑动（如正在垂直拖拽排序时）
    content: @Composable () -> Unit
) {
    val threshold = 160f    // 加大触发距离，从 90 像素 → 160 像素（必须明确右滑才删除）
    val deadZoneDp = 40f
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val thresholdPx = with(density) { threshold.dp.toPx() }
    val deadZonePx = with(density) { deadZoneDp.dp.toPx() }
    val exitRightPx = with(density) { configuration.screenWidthDp.dp.toPx() * 1.25f }

    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val bgAlpha = if (!enabled || offset.value < deadZonePx) 0f
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
                    enabled = enabled && !offset.isRunning,
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
                .clip(RoundedCornerShape(12.dp))
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

// ── Apple Music 风格队列操作 Toast ────────────────────────
enum class QueueToastType { PLAY_NEXT, ADD_TO_QUEUE }

@Composable
fun QueueActionToast(
    visible: Boolean,
    type: QueueToastType,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + androidx.compose.animation.scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        ),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        val (icon, label, bgColor) = when (type) {
            QueueToastType.PLAY_NEXT -> Triple(Icons.Default.QueueMusic, "已插播", Color(0xFF5E5CE6))
            QueueToastType.ADD_TO_QUEUE -> Triple(Icons.Default.PlaylistPlay, "已加入队列", Color(0xFFFF9500))
        }
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = bgColor,
            shadowElevation = 12.dp,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
