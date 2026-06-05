package com.applemusic.clone.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.applemusic.clone.R
import com.applemusic.clone.viewmodel.MusicViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(viewModel: MusicViewModel, onBack: () -> Unit, onNavigateToPlaylist: (String) -> Unit) {
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (deleteTarget != null) {
        val isDark = isSystemInDarkTheme()
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
            title = { Text("删除播放清单", color = if (isDark) Color.White else Color.Black) },
            text = { Text("确定要删除「${deleteTarget!!.second}」吗？", color = if (isDark) Color.White.copy(0.6f) else Color.Black.copy(0.6f)) },
            confirmButton = { TextButton(onClick = { viewModel.deletePlaylist(deleteTarget!!.first); deleteTarget = null }) { Text("删除", color = Color(0xFFFF3B30)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel), color = if (isDark) Color.White.copy(0.5f) else Color.Black.copy(0.5f)) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.primary) }
            Text(stringResource(R.string.playlists_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = { val newId = viewModel.createPlaylist("新建播放清单 ${playlists.size + 1}".trim()); onNavigateToPlaylist(newId) }) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) }
        }

        if (playlists.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.playlists_empty), color = MaterialTheme.colorScheme.onBackground.copy(0.4f)) }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 160.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(playlists) { playlist ->
                    val firstSong = playlist.songIds.firstNotNullOfOrNull { id -> songs.find { it.id == id } }
                    Column(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onNavigateToPlaylist(playlist.id) }, onLongClick = { deleteTarget = playlist.id to playlist.name })) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            if (playlist.coverUri != null) coil.compose.AsyncImage(model = playlist.coverUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else if (firstSong?.albumArtUri != null) coil.compose.AsyncImage(model = firstSong.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Default.QueueMusic, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(playlist.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.songIds.size} 首歌曲", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                    }
                }
            }
        }
    }
}
