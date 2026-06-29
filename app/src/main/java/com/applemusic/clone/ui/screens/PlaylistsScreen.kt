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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.applemusic.clone.R
import com.applemusic.clone.model.Playlist
import com.applemusic.clone.ui.components.FloatingGlassIconButton
import com.applemusic.clone.ui.components.LiquidGlassDialogModifier
import com.applemusic.clone.ui.components.LiquidGlassDialogShape
import com.applemusic.clone.ui.components.liquidGlassDialogColor
import com.applemusic.clone.viewmodel.MusicViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(viewModel: MusicViewModel, onBack: () -> Unit, onNavigateToPlaylist: (String) -> Unit) {
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    // 长按待删除的 playlist
    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.playlists_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground
            )
            FloatingGlassIconButton(
                icon = Icons.Default.Add,
                contentDescription = null,
                onClick = {
                val newId = viewModel.createPlaylist("新建播放清单 ${playlists.size + 1}".trim())
                onNavigateToPlaylist(newId)
            })
        }

        if (playlists.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.playlists_empty), color = MaterialTheme.colorScheme.onBackground.copy(0.4f))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    val firstSong = playlist.songIds.firstNotNullOfOrNull { id -> songs.find { it.id == id } }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onNavigateToPlaylist(playlist.id) },
                                onLongClick = {
                                    // 长按：震动反馈 + 弹删除确认
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pendingDelete = playlist
                                }
                            )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playlist.coverUri != null) coil.compose.AsyncImage(
                                model = playlist.coverUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                            else if (firstSong?.albumArtUri != null) coil.compose.AsyncImage(
                                model = firstSong.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                            else Icon(
                                Icons.Default.QueueMusic, null, modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.3f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${playlist.songIds.size} 首歌曲",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                        )
                    }
                }
            }
        }
    }

    // ── 长按删除确认弹窗（app 风格：深/浅色适配、明确主色+红色删除） ──
    pendingDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            modifier = LiquidGlassDialogModifier,
            shape = LiquidGlassDialogShape,
            containerColor = liquidGlassDialogColor(),
            icon = {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFF3B30).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(22.dp)) }
            },
            title = {
                Text("删除播放清单", fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black)
            },
            text = {
                Text(
                    "确定要删除「${pl.name}」吗？\n该清单中的 ${pl.songIds.size} 首歌曲不会被删除。",
                    color = if (isDark) Color.White.copy(0.65f) else Color.Black.copy(0.6f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(pl.id)
                        pendingDelete = null
                    }
                ) { Text("删除", color = Color(0xFFFF3B30), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}
