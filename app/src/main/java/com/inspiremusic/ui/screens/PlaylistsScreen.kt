package com.inspiremusic.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import com.inspiremusic.ui.theme.LocalAppIsDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil.compose.AsyncImage
import com.inspiremusic.R
import com.inspiremusic.model.Playlist
import com.inspiremusic.ui.components.FloatingGlassIconButton
import com.inspiremusic.ui.components.LiquidGlassDialogModifier
import com.inspiremusic.ui.components.LiquidGlassDialogShape
import com.inspiremusic.ui.components.liquidGlassDialogColor
import com.inspiremusic.viewmodel.MusicViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val haptic = LocalHapticFeedback.current
    val isDark = LocalAppIsDark.current
    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }
    val newPlaylistName = stringResource(R.string.playlist_new_default_name, playlists.size + 1)

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
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.playlists_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground
            )
            FloatingGlassIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.playlist_create),
                onClick = {
                    val newId = viewModel.createPlaylist(newPlaylistName)
                    onNavigateToPlaylist(newId)
                }
            )
        }

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.playlists_empty),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    val firstSong = playlist.songIds.firstNotNullOfOrNull(songsById::get)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            .combinedClickable(
                                onClick = { onNavigateToPlaylist(playlist.id) },
                                onLongClick = {
                                    if (!playlist.isSmart) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pendingDelete = playlist
                                    }
                                }
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                playlist.coverUri != null -> AsyncImage(
                                    model = playlist.coverUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                firstSong?.albumArtUri != null -> AsyncImage(
                                    model = firstSong.albumArtUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = if (playlist.isSmart && playlist.subtitle.isNotBlank()) playlist.subtitle
                                    else stringResource(R.string.playlist_song_count, playlist.songIds.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f)
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            modifier = LiquidGlassDialogModifier,
            shape = LiquidGlassDialogShape,
            containerColor = liquidGlassDialogColor(),
            icon = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFF3B30).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.playlist_delete_title),
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.playlist_delete_message, playlist.name, playlist.songIds.size),
                    color = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.60f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(playlist.id)
                        pendingDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = Color(0xFFFF3B30),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}
