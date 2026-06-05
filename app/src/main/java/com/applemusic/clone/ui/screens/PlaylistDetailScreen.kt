package com.applemusic.clone.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.R
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.viewmodel.MusicViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(playlistId: String, viewModel: MusicViewModel, onBack: () -> Unit) {
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isDark = isSystemInDarkTheme()

    val playlist = playlists.find { it.id == playlistId }
    if (playlist == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("未找到播放列表") }; return }

    val playlistSongs = playlist.songIds.mapNotNull { id -> songs.find { it.id == id } }
    val coverUri = playlist.coverUri ?: playlistSongs.firstOrNull()?.albumArtUri?.toString()
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember(playlist.name) { mutableStateOf(playlist.name) }
    var showAddSongs by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.updatePlaylistCover(playlistId, it.toString()) }
    }

    // ── 重命名弹窗 (深色/浅色自适应) ──
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
            title = { Text("编辑名称", color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isDark) Color.White else Color.Black,
                        unfocusedTextColor = if (isDark) Color.White.copy(0.7f) else Color.Black.copy(0.7f),
                        focusedBorderColor = if (isDark) Color.White.copy(0.5f) else Color.Black.copy(0.3f),
                        unfocusedBorderColor = if (isDark) Color.White.copy(0.2f) else Color.Black.copy(0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.renamePlaylist(playlistId, renameText); showRename = false }) { Text("确定", color = if (isDark) Color.White else Color.Black) } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.action_cancel), color = if (isDark) Color.White.copy(0.5f) else Color.Black.copy(0.5f)) } }
        )
    }

    // ── 删除确认弹窗 ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
            title = { Text("删除播放清单", color = if (isDark) Color.White else Color.Black) },
            text = { Text("确定要删除「${playlist.name}」吗？", color = if (isDark) Color.White.copy(0.6f) else Color.Black.copy(0.6f)) },
            confirmButton = { TextButton(onClick = { viewModel.deletePlaylist(playlistId); showDeleteConfirm = false; onBack() }) { Text("删除", color = Color(0xFFFF3B30)) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel), color = if (isDark) Color.White.copy(0.5f) else Color.Black.copy(0.5f)) } }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
            // 封面 + 编辑图标
            item {
                Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        if (coverUri != null) coil.compose.AsyncImage(model = coverUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    // 编辑封面按钮
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 30.dp).size(36.dp).clip(CircleShape).background(if (isDark) Color.White.copy(0.2f) else Color.Black.copy(0.15f)).clickable { imagePicker.launch("image/*") }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Edit, null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
            }
            // 名称 + 编辑 + 删除
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                        Text("${playlistSongs.size} 首歌曲", color = MaterialTheme.colorScheme.onBackground.copy(0.5f), fontSize = 14.sp)
                    }
                    IconButton(onClick = { renameText = playlist.name; showRename = true }) { Icon(Icons.Default.Edit, "编辑名称", tint = MaterialTheme.colorScheme.onBackground.copy(0.5f)) }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, "删除", tint = Color(0xFFFF3B30).copy(0.7f)) }
                }
            }
            // 按钮
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.playList(playlistSongs, 0) }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.album_detail_play), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = { viewModel.playShuffledList(playlistSongs) }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.album_detail_shuffle), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { showAddSongs = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Add, stringResource(R.string.menu_add_playlist), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
                }
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            // 歌曲列表
            items(playlistSongs, key = { it.id }) { song ->
                val idx = playlistSongs.indexOf(song)
                SwipeToDeleteItem(onDelete = { viewModel.removeFromPlaylist(playlistId, song.id) }) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.playList(playlistSongs, idx) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, fontWeight = if (currentSong?.id == song.id) FontWeight.Bold else FontWeight.Normal, color = if (currentSong?.id == song.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                            Text(song.artist, color = MaterialTheme.colorScheme.onBackground.copy(0.5f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        // 返回按钮
        Box(Modifier.statusBarsPadding().padding(12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(0.35f)).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }

    // 添加歌曲
    if (showAddSongs) AddSongsSheet(songs.filter { it.id !in playlist.songIds }, { showAddSongs = false }) { s -> s.forEach { viewModel.addSongToPlaylist(playlistId, it.id) }; showAddSongs = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(songs: List<AudioItem>, onDismiss: () -> Unit, onDone: (List<AudioItem>) -> Unit) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(songs, q) { if (q.isBlank()) songs else songs.filter { it.title.contains(q, true) || it.artist.contains(q, true) } }
    val selected = remember { mutableStateListOf<AudioItem>() }
    val kb = LocalSoftwareKeyboardController.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Text(if (selected.isEmpty()) "添加歌曲" else "已选 ${selected.size} 首", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = { onDone(selected.toList()); kb?.hide() }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.action_confirm)) }
            }
            OutlinedTextField(value = q, onValueChange = { q = it }, placeholder = { Text("搜索歌曲") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent))
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { song ->
                    val sel = selected.any { it.id == song.id }
                    Row(Modifier.fillMaxWidth().clickable { if (sel) selected.removeAll { it.id == song.id } else selected.add(song) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(song.title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = MaterialTheme.colorScheme.onBackground.copy(0.5f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Checkbox(checked = sel, onCheckedChange = { if (sel) selected.removeAll { it.id == song.id } else selected.add(song) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteItem(onDelete: () -> Unit, content: @Composable () -> Unit) {
    var ox by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val t = with(density) { 120.dp.toPx() }
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp).clip(RoundedCornerShape(8.dp))) {
        if (ox < -20f) Box(Modifier.matchParentSize().background(Color(0xFFFF3B30)), contentAlignment = Alignment.CenterEnd) { Icon(Icons.Default.Delete, stringResource(R.string.swipe_delete), tint = Color.White, modifier = Modifier.padding(end = 20.dp).size(22.dp)) }
        Box(Modifier.offset { IntOffset(ox.roundToInt(), 0) }.background(MaterialTheme.colorScheme.background).pointerInput(Unit) {
            detectHorizontalDragGestures(onDragEnd = { if (ox < -t) onDelete(); ox = 0f }, onDragCancel = { ox = 0f }, onHorizontalDrag = { _, a -> ox = (ox + a).coerceIn(-t * 2, 0f) })
        }) { content() }
    }
}
