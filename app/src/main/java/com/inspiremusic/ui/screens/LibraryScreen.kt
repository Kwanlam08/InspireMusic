package com.inspiremusic.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.inspiremusic.ui.theme.LocalAppIsDark
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
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
    val recentAlbums = remember(songs) {
        val whitespace = Regex("\\s+")
        songs.asSequence()
            .groupBy { it.album.trim().lowercase().replace(whitespace, " ") }
            .values
            .mapNotNull { albumSongs -> albumSongs.maxByOrNull { it.dateModifiedMs } }
            .sortedByDescending { it.dateModifiedMs }
            .take(12)
    }

    if (isLoading && songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val configuration = LocalConfiguration.current
    if (configuration.screenWidthDp >= 600 && configuration.screenWidthDp > configuration.screenHeightDp) {
        LandscapeLibrary(recentAlbums, onNavigateTo)
        return
    }
    val recentAlbumRows = remember(recentAlbums) { recentAlbums.chunked(2) }
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
            LibraryCategories(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), onNavigateTo)
        }

        if (recentAlbumRows.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.library_recently_added)) }
            items(recentAlbumRows, key = { row -> row.first().id }) { row ->
                LibraryAlbumRow(row, 2, onNavigateTo, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun LandscapeLibrary(recentAlbums: List<AudioItem>, onNavigateTo: (String) -> Unit) {
    val bottomPadding = appChromeContentPadding().calculateBottomPadding()
    Column(
        Modifier.fillMaxSize().testTag("screen_library")
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 10.dp, end = 82.dp, bottom = 8.dp)
        )
        Row(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                LibraryCategories(Modifier.fillMaxWidth().padding(vertical = 4.dp), onNavigateTo, compact = true)
                Spacer(Modifier.height(bottomPadding))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val columns = ((maxWidth + 12.dp) / 132.dp).toInt().coerceIn(2, 6)
                val rows = remember(recentAlbums, columns) { recentAlbums.chunked(columns) }
                LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding)) {
                    if (rows.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.library_recently_added),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        items(rows, key = { it.first().id }) { row ->
                            LibraryAlbumRow(row, columns, onNavigateTo, Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCategories(modifier: Modifier, onNavigateTo: (String) -> Unit, compact: Boolean = false) {
    BackdropLiquidGlass(
        modifier = modifier,
        cornerRadius = AppCornerTokens.Card,
        blurRadius = 10.dp,
        surfaceAlpha = if (LocalAppIsDark.current) 0.05f else 0.012f,
        highlightAlpha = 0.18f,
        shadowAlpha = 0.10f
    ) {
        Column {
            val height = if (compact) 48.dp else 54.dp
                 LibraryCategoryItem(icon = Icons.AutoMirrored.Outlined.QueueMusic, title = stringResource(R.string.library_playlists), testTag = "library_playlists", minHeight = height, onClick = { onNavigateTo("playlists") })
                LibraryCategoryDivider()
                 LibraryCategoryItem(icon = Icons.Outlined.Person, title = stringResource(R.string.library_artists), testTag = "library_artists", minHeight = height, onClick = { onNavigateTo("artists") })
                LibraryCategoryDivider()
                 LibraryCategoryItem(icon = Icons.Outlined.Album, title = stringResource(R.string.library_albums), testTag = "library_albums", minHeight = height, onClick = { onNavigateTo("albums") })
                LibraryCategoryDivider()
                 LibraryCategoryItem(icon = Icons.Outlined.MusicNote, title = stringResource(R.string.library_songs), testTag = "library_songs", minHeight = height, onClick = { onNavigateTo("songs") })
                LibraryCategoryDivider()
                 LibraryCategoryItem(icon = Icons.Outlined.StarBorder, title = stringResource(R.string.library_favorites), testTag = "library_favorites", minHeight = height, onClick = { onNavigateTo("favorites") })
        }
    }
}

@Composable
private fun LibraryAlbumRow(row: List<AudioItem>, columns: Int, onNavigateTo: (String) -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (song in row) {
            RecentlyAddedCard(song, { onNavigateTo("library/album/${android.net.Uri.encode(song.album)}") }, song.album, song.artist, Modifier.weight(1f))
        }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
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
    minHeight: Dp = 54.dp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
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
