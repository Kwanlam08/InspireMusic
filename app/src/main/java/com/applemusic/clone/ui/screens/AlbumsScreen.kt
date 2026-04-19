package com.applemusic.clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.applemusic.clone.viewmodel.MusicViewModel
import com.applemusic.clone.ui.components.EmptyStateView
import com.applemusic.clone.ui.components.LoadingStateView

@Composable
fun AlbumsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val albumMap = remember(songs) { viewModel.songsByAlbum() }
    val isLoading by viewModel.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部栏
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
                text = "专辑",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (isLoading) {
            LoadingStateView(
                message = "正在加载专辑...",
                modifier = Modifier.weight(1f)
            )
        } else if (albumMap.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Album,
                title = "没有专辑",
                message = "您的设备上没有找到任何专辑。",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(albumMap.entries.toList()) { (albumName, albumSongs) ->
                    val firstSong = albumSongs.first()
                    Column(
                        modifier = Modifier.clickable { onNavigateToAlbum(albumName) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            coil.compose.SubcomposeAsyncImage(
                                model = firstSong.albumArtUri,
                                contentDescription = albumName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                error = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Album, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
                                            modifier = Modifier.size(48.dp))
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = firstSong.artist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                        )
                    }
                }
            }
        }
    }
}
