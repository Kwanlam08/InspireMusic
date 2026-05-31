package com.applemusic.clone.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.R
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.viewmodel.MusicViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(playlistId: String, viewModel: MusicViewModel, onBack: () -> Unit) {
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    val playlist = playlists.find { it.id == playlistId }
    if (playlist == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("未找到播放列表") }; return }

    val playlistSongs = playlist.songIds.mapNotNull { id -> songs.find { it.id == id } }
    var showAddSongs by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(true) }
    var showImagePicker by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.updatePlaylistCover(playlistId, it.toString())
        }
    }

    LaunchedEffect(showImagePicker) {
        if (showImagePicker) {
            imagePickerLauncher.launch("image/*")
            showImagePicker = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
            // 封面
            item {
                Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                    val displayUri = playlist.coverUri ?: playlistSongs.firstOrNull()?.albumArtUri
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .combinedClickable(
                                onClick = { },
                                onLongClick = { showImagePicker = true }
                            )
                    ) {
                        if (displayUri != null) coil.compose.AsyncImage(model = displayUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            // 标题
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(playlist.name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                    Text("${playlistSongs.size} 首歌曲", color = MaterialTheme.colorScheme.onBackground.copy(0.5f), fontSize = 14.sp)
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
                    IconButton(onClick = { showAddSongs = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Add, stringResource(R.string.menu_add_playlist), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            // 歌曲列表 (可删除)
            items(playlistSongs, key = { it.id }) { song ->
                val idx = playlistSongs.indexOf(song)
                SwipeToDeleteWrapper2(onDelete = { viewModel.removeFromPlaylist(playlistId, song.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.playList(playlistSongs, idx) }
                            .padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
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

    // 添加歌曲 Sheet
    if (showAddSongs) {
        AddSongsToPlaylistSheet(
            songs = songs.filter { it.id !in playlist.songIds },
            onDismiss = { showAddSongs = false },
            onDone = { selected -> selected.forEach { viewModel.addSongToPlaylist(playlistId, it.id) }; showAddSongs = false }
        )
    }

    // 重命名弹窗
    if (showRename) {
        var renameText by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("新建播放清单") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        viewModel.renamePlaylist(playlistId, renameText.trim())
                    }
                    showRename = false
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToPlaylistSheet(songs: List<AudioItem>, onDismiss: () -> Unit, onDone: (List<AudioItem>) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(songs, searchQuery) { if (searchQuery.isBlank()) songs else songs.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) } }
    val selected = remember { mutableStateListOf<AudioItem>() }
    val keyboard = LocalSoftwareKeyboardController.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            // 顶部栏：取消 + 标题 + 完成
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Text(if (selected.isEmpty()) "添加歌曲" else "已选 ${selected.size} 首", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = { onDone(selected.toList()); keyboard?.hide() }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.action_confirm)) }
            }
            // 搜索
            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("搜索歌曲") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent))
            Spacer(Modifier.height(8.dp))
            // 歌曲列表
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { song ->
                    val sel = selected.any { it.id == song.id }
                    Row(Modifier.fillMaxWidth().clickable { if (sel) selected.removeAll { it.id == song.id } else selected.add(song) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = MaterialTheme.colorScheme.onBackground.copy(0.5f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Checkbox(checked = sel, onCheckedChange = { if (sel) selected.removeAll { it.id == song.id } else selected.add(song) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteWrapper2(onDelete: () -> Unit, content: @Composable () -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 120.dp.toPx() }
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp).clip(RoundedCornerShape(8.dp))) {
        if (offsetX < -20f) Box(Modifier.matchParentSize().background(Color(0xFFFF3B30)), contentAlignment = Alignment.CenterEnd) { Icon(Icons.Default.Delete, stringResource(R.string.swipe_delete), tint = Color.White, modifier = Modifier.padding(end = 20.dp).size(22.dp)) }
        Box(Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }.background(MaterialTheme.colorScheme.background).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = { if (offsetX < -thresholdPx) onDelete(); offsetX = 0f },
                onDragCancel = { offsetX = 0f },
                onHorizontalDrag = { _, amount -> offsetX = (offsetX + amount).coerceIn(-thresholdPx * 2, 0f) }
            )
        }) { content() }
    }
}
