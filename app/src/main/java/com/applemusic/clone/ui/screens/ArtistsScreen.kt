package com.applemusic.clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.viewmodel.MusicViewModel
import com.applemusic.clone.ui.components.EmptyStateView
import com.applemusic.clone.ui.components.LoadingStateView

@Composable
fun ArtistsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val artistMap = remember(songs) { viewModel.songsByArtist().toSortedMap() }
    val isLoading by viewModel.isLoading.collectAsState()

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
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBackIosNew, contentDescription = "返回", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = "艺术家",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (isLoading) {
            LoadingStateView(
                message = "正在加载艺术家...",
                modifier = Modifier.weight(1f)
            )
        } else if (artistMap.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Person,
                title = "没有艺术家",
                message = "您的设备上没有找到任何艺术家。",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 140.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(artistMap.entries.toList()) { (artist, artistSongs) ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToArtist(artist) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 艺术家头像占位圆形
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = artist.take(1).uppercase(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artist,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${artistSongs.size} 首歌曲",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.25f)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.08f)
                        )
                    }
                }
            }
        }
    }
}
