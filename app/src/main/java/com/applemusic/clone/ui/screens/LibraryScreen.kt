package com.applemusic.clone.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.R
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.ui.components.BackdropLiquidGlass
import com.applemusic.clone.viewmodel.MusicViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onNavigateTo: (String) -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 16.dp, end = 82.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LibraryCategoryItem(icon = Icons.Default.QueueMusic, title = stringResource(R.string.library_playlists), onClick = { onNavigateTo("playlists") })
                LibraryCategoryItem(icon = Icons.Default.Person, title = stringResource(R.string.library_artists), onClick = { onNavigateTo("artists") })
                LibraryCategoryItem(icon = Icons.Default.Album, title = stringResource(R.string.library_albums), onClick = { onNavigateTo("albums") })
                LibraryCategoryItem(icon = Icons.Default.MusicNote, title = stringResource(R.string.library_songs), onClick = { onNavigateTo("songs") })
                LibraryCategoryItem(icon = Icons.Default.Star, title = stringResource(R.string.library_favorites), onClick = { onNavigateTo("favorites") })
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
        }

        if (songs.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.library_recently_added)) }
            val recentAlbums = songs
                .groupBy { it.album }
                .values
                .mapNotNull { albumSongs -> albumSongs.maxByOrNull { it.dateModifiedMs } }
                .sortedByDescending { it.dateModifiedMs }
                .take(12)
                .chunked(2)
            items(recentAlbums) { rowItems ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (song in rowItems) {
                        RecentlyAddedCard(song = song, onClick = { onNavigateTo("library/album/${android.net.Uri.encode(song.album)}") }, label = song.album, subLabel = song.artist, modifier = Modifier.weight(1f))
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        item { Spacer(Modifier.height(160.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun RecentlyAddedCard(song: AudioItem, onClick: () -> Unit, label: String, subLabel: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.Start) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
        Text(text = subLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
    }
}

@Composable
fun LibraryCategoryItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    BackdropLiquidGlass(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(onClick = onClick),
        cornerRadius = 22.dp,
        blurRadius = 10.dp,
        surfaceAlpha = if (isDark) 0.055f else 0.010f,
        highlightAlpha = if (isDark) 0.38f else 0.24f,
        shadowAlpha = if (isDark) 0.20f else 0.10f,
        useSharedBackdrop = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.16f else 0.075f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(0.2f), modifier = Modifier.size(24.dp))
        }
    }
}
