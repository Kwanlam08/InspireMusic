package com.applemusic.clone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.viewmodel.MusicViewModel
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(viewModel: MusicViewModel, onNavigateTo: (String) -> Unit) {
    val query by viewModel.searchQuery.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    // 防抖搜索：300ms 延迟后才触发实际过滤
    var debouncedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        delay(300)
        debouncedQuery = query
    }

    val allSongs = remember(debouncedQuery) { viewModel.filteredSongs() }

    // 分组：歌曲 / 专辑 / 艺术家
    val matchedSongs = remember(allSongs, debouncedQuery) {
        if (debouncedQuery.isEmpty()) emptyList()
        else allSongs.filter {
            it.title.contains(debouncedQuery, ignoreCase = true)
        }.take(6)
    }
    val matchedAlbums = remember(allSongs, debouncedQuery) {
        if (debouncedQuery.isEmpty()) emptyList()
        else allSongs
            .filter { it.album.contains(debouncedQuery, ignoreCase = true) }
            .groupBy { it.album }
            .keys.toList()
            .take(5)
    }
    val matchedArtists = remember(allSongs, debouncedQuery) {
        if (debouncedQuery.isEmpty()) emptyList()
        else allSongs
            .filter { it.artist.contains(debouncedQuery, ignoreCase = true) }
            .groupBy { it.artist }
            .keys.toList()
            .take(5)
    }

    val hasResults = matchedSongs.isNotEmpty() || matchedAlbums.isNotEmpty() || matchedArtists.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 标题
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        // 搜索框
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = {
                Text(
                    "歌曲、艺术家、专辑",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            },
            trailingIcon = {
                AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, null)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(16.dp))

        if (query.isEmpty()) {
            // 空状态
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Search,
                        null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.2f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "搜索音乐库",
                        color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        } else if (!hasResults) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.25f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("未找到「$debouncedQuery」",
                        color = MaterialTheme.colorScheme.onBackground.copy(0.4f))
                }
            }
        } else {
            // 分组结果
            LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {

                // ── 歌曲 ──────────────────────────────────
                if (matchedSongs.isNotEmpty()) {
                    item {
                        SearchSectionHeader("歌曲")
                    }
                    items(matchedSongs) { song ->
                        SwipeToPlayNextWrapper(
                            onPlayNext = { viewModel.playNext(song) },
                            onAddLast = { viewModel.addToQueue(song) }
                        ) {
                            SongListItemWithLongPress(
                                song = song,
                                isPlaying = currentSong?.id == song.id,
                                isFavorite = favoriteIds.contains(song.id),
                                viewModel = viewModel,
                                onClick = { viewModel.play(song) },
                                onLongPress = {},
                                onAddToPlaylist = {}
                            )
                        }
                    }
                }

                // ── 专辑 ──────────────────────────────────
                if (matchedAlbums.isNotEmpty()) {
                    item {
                        SearchSectionHeader("专辑")
                    }
                    items(matchedAlbums) { albumName ->
                        val albumSong = allSongs.first { it.album == albumName }
                        SearchAlbumRow(
                            albumName = albumName,
                            artistName = albumSong.artist,
                            artUri = albumSong.albumArtUri,
                            onClick = { onNavigateTo("library/album/${android.net.Uri.encode(albumName)}") }
                        )
                    }
                }

                // ── 艺术家 ────────────────────────────────
                if (matchedArtists.isNotEmpty()) {
                    item {
                        SearchSectionHeader("艺术家")
                    }
                    items(matchedArtists) { artistName ->
                        SearchArtistRow(artistName = artistName, onClick = { onNavigateTo("library/artist/${android.net.Uri.encode(artistName)}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        ),
        color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun SearchAlbumRow(
    albumName: String,
    artistName: String,
    artUri: android.net.Uri?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            coil.compose.AsyncImage(
                model = artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(albumName, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground)
            Text(artistName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.25f))
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(0.08f)
    )
}

@Composable
private fun SearchArtistRow(artistName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                artistName.take(1).uppercase(),
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(artistName, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground)
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.25f))
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(0.08f)
    )
}