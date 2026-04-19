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
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.model.LrcLine
import com.applemusic.clone.viewmodel.MusicViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
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
    // 当歌曲切换时清空旧的模糊图，让新封面重新加载
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
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "albumMorph"
    )
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    var inputHours by remember { mutableStateOf("") }
    var inputMinutes by remember { mutableStateOf("") }
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
            coroutineScope.launch {
                try {
                    lyricsListState.animateScrollToItem(
                        index = (currentLyricIdx - 2).coerceAtLeast(0)
                    )
                } catch (_: Exception) {}
            }
        }
    }

    val duration = currentSong?.duration ?: 1L

    // ── 主容器 ────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 60f) onClose()
                }
            }
    ) {
        // ── 模糊封面背景层 ─────────────────────────────────
        blurredBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(150.dp),
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 顶部栏拉扣 (Pill) ───────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
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
                            when (currentTab) {
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
                                    blurredBitmap = bmp
                                }
                            }
                        )
                    }
                }
            }

            // ── 封面 tab: 歌曲信息 + 控件 ──────────────────
            if (currentTab == 0) {
                Spacer(Modifier.height(20.dp))
                // 歌曲信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong?.title ?: "未在播放",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = currentSong?.artist ?: "—",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
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
                            contentDescription = "收藏",
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
                            contentDescription = "更多",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                val progress = if (duration > 0) positionMs.toFloat() / duration.toFloat() else 0f
                var isSeeking by remember { mutableStateOf(false) }
                var seekPosition by remember { mutableStateOf(0f) }
                val sliderValue = if (isSeeking) seekPosition else progress.coerceIn(0f, 1f)
                val displayPositionMs = if (isSeeking) (seekPosition * duration).toLong() else positionMs

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        isSeeking = true
                        seekPosition = it
                    },
                    onValueChangeFinished = {
                        isSeeking = false
                        viewModel.seekTo((seekPosition * duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(0.85f),
                        inactiveTrackColor = Color.White.copy(0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
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
                            contentDescription = "上一首",
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
                            contentDescription = "下一首",
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

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
                            thumbColor = Color.White.copy(0.8f),
                            activeTrackColor = Color.White.copy(0.7f),
                            inactiveTrackColor = Color.White.copy(0.15f)
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
                        contentDescription = "歌词",
                        tint = if (currentTab == 1) Color.White else Color.White.copy(0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { currentTab = if (currentTab == 2) 0 else 2 }) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = "播放列表",
                        tint = if (currentTab == 2) Color.White else Color.White.copy(0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // ── 睡眠定时器弹窗 (Apple Music 风格) ────────────────────
    if (showSleepTimerMenu) {
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerMenu = false },
            containerColor = Color(0xFF1C1C1E),
            scrimColor = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 40.dp, top = 0.dp)
            ) {
                // 标题
                Text(
                    "睡眠定时器",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 20.dp),
                    color = Color.White.copy(0.5f)
                )

                // 时间选项卡组（圆角矩形风格）
                val options = listOf(
                    5 to "5 分钟",
                    10 to "10 分钟",
                    15 to "15 分钟",
                    30 to "30 分钟",
                    45 to "45 分钟",
                    60 to "1 小时"
                )

                // 分组卡片
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2C2C2E))
                ) {
                    Column {
                        options.forEachIndexed { i, (minutes, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.startSleepTimer(minutes * 60 * 1000L)
                                        showSleepTimerMenu = false
                                        showMoreMenu = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 15.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    color = Color.White,
                                    fontSize = 17.sp
                                )
                                Icon(
                                    Icons.Default.Timer,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary.copy(0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (i < options.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 20.dp),
                                    thickness = 0.5.dp,
                                    color = Color.White.copy(0.08f)
                                )
                            }
                        }
                    }
                }

                if (sleepTimerMs != null) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2C2C2E))
                            .clickable {
                                viewModel.cancelSleepTimer()
                                showSleepTimerMenu = false
                                showMoreMenu = false
                            }
                            .padding(horizontal = 20.dp, vertical = 15.dp)
                    ) {
                        Text(
                            "取消定时器",
                            color = Color(0xFFFF375F),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // ── 更多菜单 (Apple Music 风格) ─────────────────────────
    if (showMoreMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            containerColor = Color(0xFF1C1C1E),
            scrimColor = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 40.dp, top = 0.dp)
            ) {
                // 歌曲信息 Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(0.1f))
                    ) {
                        currentSong?.albumArtUri?.let {
                            coil.compose.AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong?.title ?: "",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentSong?.artist ?: "",
                            fontSize = 14.sp,
                            color = Color.White.copy(0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 第一组菜单项
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2C2C2E))
                ) {
                    Column {
                        AppleMusicMenuItem(
                            icon = Icons.Default.Album,
                            iconBg = Color(0xFF5E5CE6),
                            label = "查看专辑",
                            onClick = {
                                showMoreMenu = false
                                currentSong?.album?.let { onNavigateToAlbum(it) }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 58.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(0.08f)
                        )
                        AppleMusicMenuItem(
                            icon = Icons.Default.Person,
                            iconBg = Color(0xFF636366).copy(0.7f),
                            label = "查看艺人",
                            onClick = {
                                showMoreMenu = false
                                currentSong?.artist?.let { onNavigateToArtist(it) }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 第二组菜单项
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2C2C2E))
                ) {
                    Column {
                        AppleMusicMenuItem(
                            icon = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            iconBg = Color(0xFFFF9F0A),
                            label = if (isFav) "取消喜爱" else "加入喜爱项目",
                            onClick = {
                                currentSong?.let { viewModel.toggleFavorite(it.id) }
                                showMoreMenu = false
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 58.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(0.08f)
                        )
                        AppleMusicMenuItem(
                            icon = Icons.Default.Timer,
                            iconBg = Color(0xFF32D74B).copy(0.85f),
                            label = if (sleepTimerMs != null)
                                "取消睡眠定时 (${viewModel.formatDuration(sleepTimerMs!!)})"
                            else "睡眠定时器",
                            onClick = {
                                if (sleepTimerMs != null) {
                                    viewModel.cancelSleepTimer()
                                    showMoreMenu = false
                                } else {
                                    showSleepTimerMenu = true
                                    inputHours = ""
                                    inputMinutes = ""
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// Apple Music 风格单个菜单项（圆形彩色图标 + 文字）
@Composable
private fun AppleMusicMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 17.sp
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(0.25f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 专辑封面：从「正在播放」大图位置（与 flex 区域顶部对齐、宽度铺满）插值到歌词/队列左上角小封面。
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

    val playPulseScale = if (p < 0.04f) albumScale else 1f
    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { side.toDp() })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = playPulseScale
                    scaleY = playPulseScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    
                    val shadowElev = 15f * (1f - p)
                    if (shadowElev > 1f) {
                        shadowElevation = shadowElev
                        this.shape = shape
                        clip = true
                        spotShadowColor = Color.Black.copy(alpha = 0.48f)
                        ambientShadowColor = Color.Black.copy(alpha = 0.4f)
                    } else {
                        this.shape = shape
                        clip = true
                    }
                }
        ) {
            coil.compose.SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .crossfade(false)
                    .allowHardware(false)
                    .build(),
                contentDescription = "专辑封面",
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
        Modifier.blur((22.dp * (1f - reveal)))
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
                text = "暂无歌词\n将同名 .lrc 文件放在音乐文件旁",
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
            contentPadding = PaddingValues(top = 40.dp, bottom = 120.dp, start = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            itemsIndexed(
                lyrics,
                key = { index, _ -> "lyric_$index" }
            ) { index, line ->
                val isActive = index == currentIndex
                val dist = kotlin.math.abs(index - currentIndex)

                val lineAlpha = when {
                    isActive -> 1f
                    dist == 1 -> 0.52f
                    dist == 2 -> 0.32f
                    else -> 0.18f
                }

                Text(
                    text = line.text,
                    color = Color.White.copy(alpha = lineAlpha),
                    fontSize = if (isActive) 24.sp else 19.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    lineHeight = if (isActive) 34.sp else 28.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 0.dp)
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
                "队列为空",
                color = Color.White.copy(0.3f),
                fontSize = 16.sp
            )
        }
    } else {
        val listState = rememberLazyListState()
        
        // 自动滚动到当前播放的歌曲
        LaunchedEffect(currentSong) {
            val idx = queue.indexOfFirst { it.id == currentSong?.id }
            if (idx >= 0) {
                try {
                    listState.animateScrollToItem(idx)
                } catch (_: Exception) {}
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
                    // Shuffle
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
                            contentDescription = "随机",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Repeat
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
                            contentDescription = "循环",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 继续播放标题
            item {
                Text(
                    text = "继续播放",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            itemsIndexed(
                items = queue,
                key = { _, song -> song.id }
            ) { index, song ->
                val isActive = song.id == currentSong?.id

                SwipeToDeleteWrapper(
                    onDelete = { viewModel.removeFromQueue(song) }
                ) {
                    val itemBg = if (isActive) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(itemBg)
                            .clickable { viewModel.skipToQueueIndex(index) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧专辑封面
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(0.1f))
                        ) {
                            coil.compose.AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isActive) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "正在播放",
                                tint = Color.White.copy(0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val SwipeRevealCardShape = RoundedCornerShape(12.dp)

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
    val threshold = 80f
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val thresholdPx = with(density) { threshold.dp.toPx() }
    val exitRightPx = with(density) { configuration.screenWidthDp.dp.toPx() * 1.25f }

    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val bgAlpha = (offset.value / (thresholdPx * 2)).coerceIn(0f, 1f)

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
                    contentDescription = "删除",
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("删除", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
