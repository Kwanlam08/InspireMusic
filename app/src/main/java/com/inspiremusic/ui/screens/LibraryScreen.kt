package com.inspiremusic.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.inspiremusic.ui.theme.LocalAppIsDark
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiremusic.R
import com.inspiremusic.model.AudioItem
import com.inspiremusic.ui.components.AppCornerTokens
import com.inspiremusic.ui.components.BackdropLiquidGlass
import com.inspiremusic.ui.components.ContinuousRoundedCornerShape
import com.inspiremusic.ui.components.appChromeContentPadding
import com.inspiremusic.viewmodel.MusicViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onNavigateTo: (String) -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val recentAlbumRows = remember(songs) {
        songs.asSequence()
            .groupBy { it.album.trim().lowercase().replace(Regex("\\s+"), " ") }
            .values
            .mapNotNull { albumSongs -> albumSongs.maxByOrNull { it.dateModifiedMs } }
            .sortedByDescending { it.dateModifiedMs }
            .take(12)
            .chunked(2)
    }

    if (isLoading && songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_library")
            .background(MaterialTheme.colorScheme.background),
        contentPadding = appChromeContentPadding()
    ) {
        item {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 16.dp, end = 82.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            BackdropLiquidGlass(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                cornerRadius = AppCornerTokens.Card,
                blurRadius = 10.dp,
                surfaceAlpha = if (LocalAppIsDark.current) 0.05f else 0.012f,
                highlightAlpha = 0.18f,
                shadowAlpha = 0.10f,
                useSharedBackdrop = true
            ) {
              Column {
                LibraryCategoryItem(icon = Icons.AutoMirrored.Outlined.QueueMusic, title = stringResource(R.string.library_playlists), testTag = "library_playlists", onClick = { onNavigateTo("playlists") })
                LibraryCategoryDivider()
                LibraryCategoryItem(icon = Icons.Outlined.Person, title = stringResource(R.string.library_artists), testTag = "library_artists", onClick = { onNavigateTo("artists") })
                LibraryCategoryDivider()
                LibraryCategoryItem(icon = Icons.Outlined.Album, title = stringResource(R.string.library_albums), testTag = "library_albums", onClick = { onNavigateTo("albums") })
                LibraryCategoryDivider()
                LibraryCategoryItem(icon = Icons.Outlined.MusicNote, title = stringResource(R.string.library_songs), testTag = "library_songs", onClick = { onNavigateTo("songs") })
                LibraryCategoryDivider()
                LibraryCategoryItem(icon = Icons.Outlined.StarBorder, title = stringResource(R.string.library_favorites), testTag = "library_favorites", onClick = { onNavigateTo("favorites") })
              }
            }
        }

        if (songs.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.library_recently_added)) }
            items(recentAlbumRows, key = { row -> row.joinToString(separator = "-") { it.id.toString() } }) { rowItems ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (song in rowItems) {
                        RecentlyAddedCard(song = song, onClick = { onNavigateTo("library/album/${android.net.Uri.encode(song.album)}") }, label = song.album, subLabel = song.artist, modifier = Modifier.weight(1f))
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 0.dp))
}

@Composable
private fun RecentlyAddedCard(song: AudioItem, onClick: () -> Unit, label: String, subLabel: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.Start) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(ContinuousRoundedCornerShape(AppCornerTokens.Control)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
        Text(text = subLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

@Composable
private fun LibraryCategoryDivider() {
    HorizontalDivider(Modifier.padding(start = 58.dp, end = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
}

@Composable
fun LibraryCategoryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    testTag: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(0.2f), modifier = Modifier.size(24.dp))
    }
}
