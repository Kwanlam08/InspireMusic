package com.inspiremusic.ui.screens

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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inspiremusic.R
import com.inspiremusic.viewmodel.MusicViewModel
import com.inspiremusic.ui.components.EmptyStateView
import com.inspiremusic.ui.components.FloatingGlassIconButton
import com.inspiremusic.ui.components.LiquidGlassDialogModifier
import com.inspiremusic.ui.components.LiquidGlassDialogShape
import com.inspiremusic.ui.components.LoadingStateView
import com.inspiremusic.ui.components.liquidGlassDialogColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hiddenAlbums by viewModel.hiddenAlbums.collectAsState()
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    val albumMap = remember(songs, hiddenAlbums) {
        viewModel.songsByAlbum().filterKeys { it !in hiddenAlbums }
    }

    // 长按要删除的专辑
    var pendingDelete by remember { mutableStateOf<String?>(null) }

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
            FloatingGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                useSharedBackdrop = true
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.albums_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (isLoading) {
            LoadingStateView(message = "Loading...", modifier = Modifier.weight(1f))
        } else if (albumMap.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Album,
                title = stringResource(R.string.albums_empty),
                message = stringResource(R.string.albums_empty),
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
                items(albumMap.entries.toList(), key = { it.key }) { (albumName, albumSongs) ->
                    val firstSong = albumSongs.first()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onNavigateToAlbum(albumName) },
                                onLongClick = {
                                    // 长按：震动反馈 + 弹删除确认
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pendingDelete = albumName
                                }
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            coil.compose.AsyncImage(
                                model = firstSong.albumArtUri,
                                contentDescription = albumName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
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
                            text = firstSong.albumArtist.ifBlank { firstSong.artist },
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

    // ── 长按删除专辑确认弹窗（app 风格，与播放清单一致） ──
    pendingDelete?.let { albumName ->
        val songCount = albumMap[albumName]?.size ?: 0
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
                ) {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            title = {
                Text(
                    "从资料库移除专辑",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )
            },
            text = {
                Text(
                    "确定要从资料库隐藏「$albumName」吗？\n专辑中的 $songCount 首歌曲不会被删除。\n（可在 App 设置中恢复）",
                    color = if (isDark) Color.White.copy(0.65f) else Color.Black.copy(0.6f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hideAlbum(albumName)
                    pendingDelete = null
                }) {
                    Text("隐藏", color = Color(0xFFFF3B30), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}
