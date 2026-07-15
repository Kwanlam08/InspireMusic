package com.inspiremusic.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import com.inspiremusic.ui.theme.LocalAppIsDark
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.inspiremusic.R
import com.inspiremusic.model.AudioItem
import com.inspiremusic.ui.components.FloatingGlassIconButton
import com.inspiremusic.ui.components.LiquidGlassBottomSheetDragHandle
import com.inspiremusic.ui.components.LiquidGlassBottomSheetFrame
import com.inspiremusic.ui.components.LiquidGlassBottomSheetModifier
import com.inspiremusic.ui.components.LiquidGlassBottomSheetShape
import com.inspiremusic.ui.components.LiquidGlassDialogModifier
import com.inspiremusic.ui.components.LiquidGlassDialogShape
import com.inspiremusic.ui.components.liquidGlassDialogColor
import com.inspiremusic.ui.components.liquidGlassBottomSheetColor
import com.inspiremusic.viewmodel.MusicViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 播放列表详情页（v2 风格）：
 *  - 顶部栏: 返回 + 右上角「编辑」/「完成」按钮
 *  - 封面: 居中放大封面 + 中心图片按钮（选择封面）
 *  - 标题: 默认像搜索框一样可点击编辑，编辑态变 TextField + 对勾
 *  - 歌曲: 前面有 1/2/3... 数字序号（正常显示时也有）
 *  - 末尾: 上下移动按钮（编辑模式才显示）
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(playlistId: String, viewModel: MusicViewModel, onBack: () -> Unit) {
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val context = LocalContext.current
    val isDark = LocalAppIsDark.current
    val haptic = LocalHapticFeedback.current

    val playlist = playlists.find { it.id == playlistId }
    if (playlist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.playlist_not_found))
        }
        return
    }

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val playlistSongs = remember(playlist.songIds, songsById) {
        playlist.songIds.mapNotNull(songsById::get)
    }
    val coverUri = playlist.coverUri ?: playlistSongs.firstOrNull()?.albumArtUri?.toString()
    val displayPlaylistSongs = remember { mutableStateListOf<AudioItem>() }
    var draggedSongId by remember { mutableStateOf<Long?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playlist.songIds, songs) {
        if (draggedSongId == null) {
            displayPlaylistSongs.clear()
            displayPlaylistSongs.addAll(playlistSongs)
        }
    }

    // ── 编辑模式状态 ──
    var isEditing by remember { mutableStateOf(false) }
    var renameText by remember(playlist.name) { mutableStateOf(playlist.name) }
    var subtitleText by remember(playlist.subtitle) { mutableStateOf(playlist.subtitle) }
    var showDeletePlaylistConfirm by remember { mutableStateOf(false) }
    var showResetCoverConfirm by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    val kb = LocalSoftwareKeyboardController.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingCropUri = uri
    }

    // 退出编辑模式时回滚未保存的标题
    fun exitEdit() {
        if (renameText != playlist.name) renameText = playlist.name
        if (subtitleText != playlist.subtitle) subtitleText = playlist.subtitle
        isEditing = false
        kb?.hide()
    }

    fun saveEdits() {
        val newName = renameText.trim()
        if (newName.isNotBlank() && newName != playlist.name) {
            viewModel.renamePlaylist(playlistId, newName)
        }
        if (subtitleText.trim() != playlist.subtitle) {
            viewModel.updatePlaylistSubtitle(playlistId, subtitleText)
        }
        isEditing = false
        kb?.hide()
    }

    val playlistDurationMinutes = remember(playlistSongs) {
        formatPlaylistDurationMinutes(playlistSongs.sumOf { it.duration.coerceAtLeast(0L) })
    }

    // ── 删除歌单确认 ──
    if (showDeletePlaylistConfirm) {
        AlertDialog(
            onDismissRequest = { showDeletePlaylistConfirm = false },
            modifier = LiquidGlassDialogModifier,
            shape = LiquidGlassDialogShape,
            containerColor = liquidGlassDialogColor(),
            title = { Text(stringResource(R.string.playlist_delete_title), color = if (isDark) Color.White else Color.Black) },
            text = {
                Text(
                    stringResource(R.string.playlist_delete_message, playlist.name, playlistSongs.size),
                    color = if (isDark) Color.White.copy(0.6f) else Color.Black.copy(0.6f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlistId)
                    showDeletePlaylistConfirm = false
                    onBack()
                }) { Text(stringResource(R.string.action_delete), color = Color(0xFFFF3B30)) }
            },
            dismissButton = { TextButton(onClick = { showDeletePlaylistConfirm = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showResetCoverConfirm) {
        AlertDialog(
            onDismissRequest = { showResetCoverConfirm = false },
            modifier = LiquidGlassDialogModifier,
            shape = LiquidGlassDialogShape,
            containerColor = liquidGlassDialogColor(),
            title = { Text(stringResource(R.string.playlist_cover_reset_title), color = if (isDark) Color.White else Color.Black) },
            text = {
                Text(
                    stringResource(R.string.playlist_cover_reset_message),
                    color = if (isDark) Color.White.copy(0.68f) else Color.Black.copy(0.68f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearPlaylistCover(playlistId)
                    showResetCoverConfirm = false
                }) { Text(stringResource(R.string.playlist_cover_reset_confirm), color = Color(0xFFFF3B30)) }
            },
            dismissButton = { TextButton(onClick = { showResetCoverConfirm = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    pendingCropUri?.let { uri ->
        PlaylistCoverCropDialog(
            uri = uri,
            onDismiss = { pendingCropUri = null },
            onConfirm = { crop ->
                saveSquarePlaylistCover(context, uri, playlistId, crop)?.let { cropped ->
                    viewModel.updatePlaylistCover(playlistId, cropped.toString())
                }
                pendingCropUri = null
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 56.dp, bottom = 230.dp)) {

            // ── 封面：默认无中心按钮，点击编辑后才出现"图片"按钮 ──
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (coverUri != null) {
                            coil.compose.AsyncImage(
                                model = coverUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // 中心图片按钮：仅在编辑态出现
                    AnimatedVisibility(
                        visible = isEditing,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    imagePicker.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "更换封面",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = isEditing && playlist.coverUri != null,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 28.dp),
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.06f))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.14f), RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showResetCoverConfirm = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.playlist_cover_reset_title),
                                tint = Color(0xFFFF3B30),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // ── 标题：默认像搜索框一样排版，编辑态才是真的 TextField + 对勾 ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isEditing) {
                        // 编辑态：TextField + 右侧对勾
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                ),
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )
                            Spacer(Modifier.width(8.dp))
                            // 对勾（保存）
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { saveEdits() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            }
                            OutlinedTextField(
                                value = subtitleText,
                                onValueChange = { subtitleText = it },
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.playlist_subtitle_placeholder)) },
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(0.75f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(0.16f),
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )
                        }
                    } else {
                        // 默认态：像搜索框的视觉，但只是普通显示（不 clickable）。
                        // 用户要编辑必须点右上角"画笔"按钮才能进入编辑模式
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = playlist.name,
                                style = TextStyle(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (playlist.subtitle.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = playlist.subtitle,
                                    color = MaterialTheme.colorScheme.onBackground.copy(0.54f),
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                // 歌曲数量
                Text(
                    "${playlistSongs.size} ${stringResource(R.string.playlist_song_unit)} \u00B7 $playlistDurationMinutes ${stringResource(R.string.playlist_minute_unit)}",
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }

            // ── 播放 / 随机播放 ──
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.playList(playlistSongs, 0) },
                        modifier = Modifier.weight(1f).height(44.dp),
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
                        onClick = { viewModel.playShuffledList(playlistSongs) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.album_detail_shuffle), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── 分隔 ──
            item {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(0.08f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // ── 歌曲列表：数字序号靠最左 + 封面 + 标题，编辑态末尾出 ↑/↓ ──
            itemsIndexed(
                items = displayPlaylistSongs,
                key = { _, song -> song.id }
            ) { index, song ->
                val isCurrent = currentSong?.id == song.id
                val isDragged = draggedSongId == song.id
                val rowHeightPx = with(LocalDensity.current) { 60.dp.toPx() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
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
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else Color.Transparent
                        )
                        .then(
                            if (!isEditing) {
                                Modifier.clickable { viewModel.playList(displayPlaylistSongs.toList(), index) }
                            } else {
                                Modifier
                            }
                        )
                        // 紧贴左边：start 6dp → 数字(28dp) → 12dp → 封面
                        .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        modifier = Modifier.width(28.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground.copy(0.4f),
                        fontSize = 15.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 15.sp
                        )
                        Text(
                            song.artist,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // 末尾的上下移按钮（编辑模式才显示）
                    AnimatedVisibility(
                        visible = isEditing,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        Column(
                            modifier = Modifier.pointerInput(song.id, displayPlaylistSongs.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedSongId = song.id
                                        dragStartIndex = displayPlaylistSongs.indexOfFirst { it.id == song.id }
                                        dragOffsetPx = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = {
                                        val from = dragStartIndex
                                        val to = displayPlaylistSongs.indexOfFirst { it.id == song.id }
                                        dragOffsetPx = 0f
                                        draggedSongId = null
                                        dragStartIndex = -1
                                        if (from >= 0 && to >= 0 && from != to) {
                                            viewModel.movePlaylistSong(playlistId, from, to)
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
                                            val from = displayPlaylistSongs.indexOfFirst { it.id == song.id }
                                            val target = (from + direction).coerceIn(0, displayPlaylistSongs.lastIndex)
                                            if (from >= 0 && target != from) {
                                                val moved = displayPlaylistSongs.removeAt(from)
                                                displayPlaylistSongs.add(target, moved)
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy((-6).dp)
                        ) {
                            val canUp = true
                            val canDown = false
                            IconButton(
                                onClick = {
                                    if (canUp) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        // Reordering is handled by the drag gesture on this handle.
                                    }
                                },
                                enabled = canUp,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "上移",
                                    tint = if (canUp) MaterialTheme.colorScheme.onBackground.copy(0.85f)
                                           else MaterialTheme.colorScheme.onBackground.copy(0.2f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (canDown) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        // Reordering is handled by the drag gesture on this handle.
                                    }
                                },
                                enabled = false,
                                modifier = Modifier.size(0.dp)
                            ) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "下移",
                                    tint = if (canDown) MaterialTheme.colorScheme.onBackground.copy(0.85f)
                                           else MaterialTheme.colorScheme.onBackground.copy(0.2f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(0.05f),
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp)
                )
            }

            // 编辑模式下：底部放一个「删除播放清单」按钮
            if (isEditing) {
                item {
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { showDeletePlaylistConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF3B30)
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.playlist_delete_title), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(110.dp))
                }
            }
        }

        // ── 顶部栏（悬浮） ──
        // 左侧：圆形"返回"图标按钮（和左下的"圆形图标"对称）
        // 右侧：默认态显示圆形"画笔"图标（进入编辑），编辑态显示对勾圆形按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：圆形返回按钮
            FloatingGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack
            )

            Spacer(Modifier.weight(1f))

            // 右：默认态是圆形"画笔"图标（进入编辑），编辑态是对勾
            if (isEditing) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 38.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.dp, Color.Black.copy(0.18f), RoundedCornerShape(16.dp))
                        .clickable { saveEdits() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "完成",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (!playlist.isSmart) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 38.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(0.35f))
                        .border(1.dp, Color.Black.copy(0.22f), RoundedCornerShape(16.dp))
                        .clickable { isEditing = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCoverCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (PlaylistCoverCrop) -> Unit
) {
    val isDark = LocalAppIsDark.current
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodePlaylistCoverBitmap(context, uri) }.getOrNull()
        }
    }
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var centerX by remember(bitmap) { mutableFloatStateOf(bitmap?.width?.div(2f) ?: 0f) }
    var centerY by remember(bitmap) { mutableFloatStateOf(bitmap?.height?.div(2f) ?: 0f) }
    var viewportPx by remember { mutableFloatStateOf(1f) }

    fun currentCrop(): PlaylistCoverCrop? {
        val source = bitmap ?: return null
        val minSide = minOf(source.width, source.height).toFloat()
        val cropSize = (minSide / zoom.coerceAtLeast(1f)).roundToInt().coerceAtLeast(1)
        val half = cropSize / 2f
        val safeCenterX = centerX.coerceIn(half, source.width - half)
        val safeCenterY = centerY.coerceIn(half, source.height - half)
        val left = (safeCenterX - half).roundToInt().coerceIn(0, source.width - cropSize)
        val top = (safeCenterY - half).roundToInt().coerceIn(0, source.height - cropSize)
        return PlaylistCoverCrop(left = left, top = top, size = cropSize)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = LiquidGlassDialogModifier,
        shape = LiquidGlassDialogShape,
        containerColor = liquidGlassDialogColor(),
        title = {
            Text(
                text = stringResource(R.string.playlist_cover_crop_title),
                color = if (isDark) Color.White else Color.Black,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .onSizeChanged { viewportPx = it.width.toFloat().coerceAtLeast(1f) }
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                val source = bitmap ?: return@detectTransformGestures
                                val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                val nextCropSize = minOf(source.width, source.height) / nextZoom
                                zoom = nextZoom
                                centerX = (centerX - pan.x / viewportPx * nextCropSize)
                                    .coerceIn(nextCropSize / 2f, source.width - nextCropSize / 2f)
                                centerY = (centerY - pan.y / viewportPx * nextCropSize)
                                    .coerceIn(nextCropSize / 2f, source.height - nextCropSize / 2f)
                            }
                        }
                ) {
                    val source = bitmap
                    if (source == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        val image = remember(source) { source.asImageBitmap() }
                        Canvas(Modifier.fillMaxSize()) {
                            val crop = currentCrop() ?: return@Canvas
                            drawImage(
                                image = image,
                                srcOffset = IntOffset(crop.left, crop.top),
                                srcSize = IntSize(crop.size, crop.size),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                            )
                            val gridColor = Color.White.copy(alpha = 0.46f)
                            val stroke = 1.dp.toPx()
                            drawLine(gridColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), stroke)
                            drawLine(gridColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), stroke)
                            drawLine(gridColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), stroke)
                            drawLine(gridColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), stroke)
                            drawRect(
                                color = Color.White.copy(alpha = 0.86f),
                                size = size,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.playlist_cover_crop_desc),
                    color = if (isDark) Color.White.copy(0.68f) else Color.Black.copy(0.68f),
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { currentCrop()?.let(onConfirm) },
                enabled = bitmap != null
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private data class PlaylistCoverCrop(
    val left: Int,
    val top: Int,
    val size: Int
)

private fun saveSquarePlaylistCover(context: Context, sourceUri: Uri, playlistId: String, crop: PlaylistCoverCrop): Uri? {
    return runCatching {
        val bitmap = decodePlaylistCoverBitmap(context, sourceUri)
        val size = crop.size.coerceAtMost(minOf(bitmap.width, bitmap.height))
        val left = crop.left.coerceIn(0, bitmap.width - size)
        val top = crop.top.coerceIn(0, bitmap.height - size)
        val cropped = Bitmap.createBitmap(bitmap, left, top, size, size)
        val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
        val file = File(dir, "playlist_${playlistId}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (cropped !== bitmap) cropped.recycle()
        bitmap.recycle()
        Uri.fromFile(file)
    }.getOrNull()
}

private fun decodePlaylistCoverBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Unable to decode playlist cover")
    }
}

private fun formatPlaylistDurationMinutes(durationMs: Long): Long {
    val minutes = durationMs / 60_000L
    return if (durationMs > 0L && minutes == 0L) 1L else minutes
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(songs: List<AudioItem>, onDismiss: () -> Unit, onDone: (List<AudioItem>) -> Unit) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(songs, q) { if (q.isBlank()) songs else songs.filter { it.title.contains(q, true) || it.artist.contains(q, true) } }
    val selected = remember { mutableStateListOf<AudioItem>() }
    val kb = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = LiquidGlassBottomSheetModifier,
        containerColor = Color.Transparent,
        shape = LiquidGlassBottomSheetShape,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.30f)
    ) {
        LiquidGlassBottomSheetFrame {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Text(if (selected.isEmpty()) stringResource(R.string.playlist_add_songs_title) else stringResource(R.string.playlist_selected_songs, selected.size), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = { onDone(selected.toList()); kb?.hide() }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.action_confirm)) }
            }
            OutlinedTextField(value = q, onValueChange = { q = it }, placeholder = { Text(stringResource(R.string.playlist_search_songs)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent))
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { song ->
                    val sel = selected.any { it.id == song.id }
                    Row(Modifier.fillMaxWidth().clickable { if (sel) selected.removeAll { it.id == song.id } else selected.add(song) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(song.title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = MaterialTheme.colorScheme.onBackground.copy(0.5f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Checkbox(checked = sel, onCheckedChange = { if (sel) selected.removeAll { it.id == song.id } else selected.add(song) })
                    }
                }
            }
            }
        }
    }
}
