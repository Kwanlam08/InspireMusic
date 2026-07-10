package com.applemusic.clone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.applemusic.clone.R
import com.applemusic.clone.viewmodel.MusicViewModel
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(viewModel: MusicViewModel, onNavigateTo: (String) -> Unit) {
    val query by viewModel.searchQuery.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val lyricsSearchIndex by viewModel.lyricsSearchIndex.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()

    var debouncedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        delay(220)
        debouncedQuery = query.trim()
    }

    val allSongs = remember(songs, lyricsSearchIndex, debouncedQuery) {
        if (debouncedQuery.isBlank()) emptyList() else viewModel.filteredSongs()
    }
    val matchedSongs = remember(allSongs, debouncedQuery) { allSongs.take(10) }
    val matchedAlbums = remember(allSongs, debouncedQuery) {
        allSongs
            .filter { it.album.contains(debouncedQuery, ignoreCase = true) }
            .groupBy { it.album }
            .keys
            .take(6)
    }
    val matchedArtists = remember(allSongs, debouncedQuery) {
        allSongs
            .filter { it.artist.contains(debouncedQuery, ignoreCase = true) }
            .groupBy { it.artist }
            .keys
            .take(6)
    }
    val hasResults = matchedSongs.isNotEmpty() || matchedAlbums.isNotEmpty() || matchedArtists.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 160.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_search),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 16.dp, end = 82.dp, bottom = 12.dp)
            )
        }

        item {
            SearchField(
                query = query,
                onQueryChange = viewModel::setSearchQuery,
                onClear = { viewModel.setSearchQuery("") }
            )
            Spacer(Modifier.height(16.dp))
        }

        if (query.isBlank()) {
            if (recentlyPlayed.isNotEmpty()) {
                item { SearchSectionHeader(stringResource(R.string.search_recently_played)) }
                items(recentlyPlayed.take(6), key = { it.id }) { song ->
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

            val recentAlbums = songs.groupBy { it.album }.values.map { it.first() }.take(5)
            if (recentAlbums.isNotEmpty()) {
                item { SearchSectionHeader(stringResource(R.string.search_recent_albums)) }
                items(recentAlbums, key = { it.album }) { song ->
                    SearchAlbumRow(
                        albumName = song.album,
                        artistName = song.artist,
                        artUri = song.albumArtUri,
                        onClick = { onNavigateTo("library/search/album/${android.net.Uri.encode(song.album)}") }
                    )
                }
            }
        } else if (!hasResults) {
            item {
                SearchEmptyState()
            }
        } else {
            if (matchedSongs.isNotEmpty()) {
                item { SearchSectionHeader(stringResource(R.string.search_section_songs)) }
                items(matchedSongs, key = { it.id }) { song ->
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

            if (matchedAlbums.isNotEmpty()) {
                item { SearchSectionHeader(stringResource(R.string.search_section_albums)) }
                items(matchedAlbums, key = { it }) { albumName ->
                    val albumSong = allSongs.first { it.album == albumName }
                    SearchAlbumRow(
                        albumName = albumName,
                        artistName = albumSong.artist,
                        artUri = albumSong.albumArtUri,
                        onClick = { onNavigateTo("library/search/album/${android.net.Uri.encode(albumName)}") }
                    )
                }
            }

            if (matchedArtists.isNotEmpty()) {
                item { SearchSectionHeader(stringResource(R.string.search_section_artists)) }
                items(matchedArtists, key = { it }) { artistName ->
                    SearchArtistRow(
                        artistName = artistName,
                        onClick = { onNavigateTo("library/artist/${android.net.Uri.encode(artistName)}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                stringResource(R.string.search_placeholder),
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, null)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(22.dp),
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
}

@Composable
private fun SearchBrowseCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SearchOff,
            null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(0.25f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.search_empty),
            color = MaterialTheme.colorScheme.onBackground.copy(0.4f)
        )
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
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                albumName,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.25f)
        )
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
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                artistName.take(1).uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            artistName,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.25f)
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(0.08f)
    )
}
