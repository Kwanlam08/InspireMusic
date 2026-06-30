package com.applemusic.clone.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.R
import com.applemusic.clone.model.LyricsCacheEntry
import com.applemusic.clone.model.Playlist
import com.applemusic.clone.settings.AccentColorStyle
import com.applemusic.clone.settings.LocalAppSettingsController
import com.applemusic.clone.settings.ThemeMode
import com.applemusic.clone.ui.components.BackdropLiquidGlass
import com.applemusic.clone.ui.components.FloatingGlassIconButton
import com.applemusic.clone.viewmodel.MusicViewModel
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsPage {
    Main,
    PlaylistBackup,
    LyricsCache
}

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val controller = LocalAppSettingsController.current
    val appSettings by controller.settings.collectAsState()
    val lyricsCache by viewModel.lyricsCacheEntries.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var page by remember { mutableStateOf(SettingsPage.Main) }
    var selectedPlaylistIds by remember(playlists) { mutableStateOf(playlists.map { it.id }.toSet()) }
    var includePlaylistBackup by remember { mutableStateOf(true) }
    var includeListeningHistoryBackup by remember { mutableStateOf(true) }
    var pendingBackupContent by remember { mutableStateOf("") }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(pendingBackupContent.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open backup file")
        }
        Toast.makeText(
            context,
            context.getString(
                if (result.isSuccess) R.string.settings_backup_export_success else R.string.settings_backup_export_failed
            ),
            Toast.LENGTH_SHORT
        ).show()
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Unable to read backup file")
            viewModel.importPlaylistBackup(json)
        }
        val message = result.fold(
            onSuccess = {
                context.getString(
                    R.string.settings_backup_import_success,
                    it.importedPlaylists,
                    it.importedSongs,
                    it.missingSongs,
                    it.importedListeningRecords
                )
            },
            onFailure = { context.getString(R.string.settings_backup_import_failed) }
        )
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    fun sharePlaylistBackup() {
        val result = runCatching {
            val backupContent = viewModel.buildPlaylistBackup(
                selectedPlaylistIds = selectedPlaylistIds,
                includePlaylists = includePlaylistBackup,
                includeListeningHistory = includeListeningHistoryBackup
            )
            val dir = File(context.cacheDir, "playlist_backups").apply { mkdirs() }
            val file = File(dir, "inspire_music_playlists_backup.json")
            file.writeText(backupContent, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_playlist_backup))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.settings_backup_share)
                )
            )
        }
        if (result.isFailure) {
            Toast.makeText(context, context.getString(R.string.settings_backup_share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLyricsCache()
    }

    BackHandler(enabled = page != SettingsPage.Main) {
        page = SettingsPage.Main
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 160.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingGlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = {
                        if (page == SettingsPage.Main) onBack() else page = SettingsPage.Main
                    }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    when (page) {
                        SettingsPage.Main -> stringResource(R.string.settings_title)
                        SettingsPage.PlaylistBackup -> stringResource(R.string.settings_playlist_backup)
                        SettingsPage.LyricsCache -> stringResource(R.string.settings_cache_title)
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val direction = if (forward) 1 else -1
                    (
                        slideInHorizontally(tween(260)) { width -> width * direction / 5 } + fadeIn(tween(220))
                    ) togetherWith (
                        slideOutHorizontally(tween(220)) { width -> -width * direction / 5 } + fadeOut(tween(180))
                    ) using SizeTransform(clip = false)
                },
                label = "settingsPageTransition"
            ) { targetPage ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (targetPage) {
                        SettingsPage.Main -> {
                            SettingsGlassSection(
                                title = stringResource(R.string.settings_appearance),
                                icon = Icons.Default.Palette
                            ) {
                                SettingsChoiceRow(
                                    title = stringResource(R.string.settings_display_mode),
                                    options = listOf(
                                        stringResource(R.string.settings_mode_system) to ThemeMode.SYSTEM,
                                        stringResource(R.string.settings_mode_light) to ThemeMode.LIGHT,
                                        stringResource(R.string.settings_mode_dark) to ThemeMode.DARK
                                    ),
                                    selected = appSettings.themeMode,
                                    onSelected = controller::setThemeMode
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Default.AutoAwesome,
                                    title = stringResource(R.string.settings_dynamic_color_title),
                                    subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                                    checked = appSettings.useDynamicColor,
                                    onCheckedChange = controller::setUseDynamicColor
                                )
                                if (!appSettings.useDynamicColor) {
                                    SettingsAccentChoiceRow(
                                        title = stringResource(R.string.settings_accent_color),
                                        selected = appSettings.accentColorStyle,
                                        onSelected = controller::setAccentColorStyle
                                    )
                                }
                                ThemePreviewRow()
                            }

                            SettingsGlassSection(
                                title = stringResource(R.string.settings_lyrics),
                                icon = Icons.Default.Lyrics
                            ) {
                                SettingsSwitchRow(
                                    icon = Icons.Default.WbSunny,
                                    title = stringResource(R.string.settings_online_lyrics_title),
                                    subtitle = stringResource(R.string.settings_online_lyrics_subtitle),
                                    checked = appSettings.onlineLyricsEnabled,
                                    onCheckedChange = controller::setOnlineLyricsEnabled
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Default.LightMode,
                                    title = stringResource(R.string.settings_prefer_synced_title),
                                    subtitle = stringResource(R.string.settings_prefer_synced_subtitle),
                                    checked = appSettings.preferSyncedLyrics,
                                    onCheckedChange = controller::setPreferSyncedLyrics
                                )
                                SettingsNavigationRow(
                                    icon = Icons.Default.Lyrics,
                                    title = stringResource(R.string.settings_cache_title),
                                    subtitle = stringResource(
                                        R.string.settings_cache_summary,
                                        lyricsCache.size,
                                        formatBytes(lyricsCache.sumOf { it.sizeBytes })
                                    ),
                                    onClick = { page = SettingsPage.LyricsCache }
                                )
                            }

                            SettingsGlassSection(
                                title = stringResource(R.string.settings_data),
                                icon = Icons.Default.Settings
                            ) {
                                SettingsNavigationRow(
                                    icon = Icons.Default.QueueMusic,
                                    title = stringResource(R.string.settings_playlist_backup),
                                    subtitle = stringResource(R.string.settings_playlist_backup_subtitle),
                                    onClick = { page = SettingsPage.PlaylistBackup }
                                )
                            }

                            SettingsGlassSection(
                                title = stringResource(R.string.settings_about),
                                icon = Icons.Default.Info
                            ) {
                                Text(
                                    stringResource(R.string.app_name),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.settings_about_desc),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                SettingsNavigationRow(
                                    icon = Icons.Default.OpenInNew,
                                    title = stringResource(R.string.settings_release_page),
                                    subtitle = stringResource(R.string.settings_release_page_subtitle),
                                    onClick = {
                                        uriHandler.openUri("https://github.com/Kwanlam08/InspireMusic-Releases/releases")
                                    }
                                )
                            }
                        }

                        SettingsPage.PlaylistBackup -> {
                            SettingsGlassSection(
                                title = stringResource(R.string.settings_playlist_backup),
                                icon = Icons.Default.QueueMusic
                            ) {
                                PlaylistBackupPanel(
                                    playlists = playlists,
                                    selectedPlaylistIds = selectedPlaylistIds,
                                    includePlaylistBackup = includePlaylistBackup,
                                    includeListeningHistoryBackup = includeListeningHistoryBackup,
                                    onSelectionChange = { selectedPlaylistIds = it },
                                    onIncludePlaylistBackupChange = { includePlaylistBackup = it },
                                    onIncludeListeningHistoryBackupChange = { includeListeningHistoryBackup = it },
                                    onExport = {
                                        pendingBackupContent = viewModel.buildPlaylistBackup(
                                            selectedPlaylistIds = selectedPlaylistIds,
                                            includePlaylists = includePlaylistBackup,
                                            includeListeningHistory = includeListeningHistoryBackup
                                        )
                                        exportBackupLauncher.launch("inspire_music_playlists_backup.json")
                                    },
                                    onShare = { sharePlaylistBackup() },
                                    onImport = { importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
                                )
                            }
                        }

                        SettingsPage.LyricsCache -> {
                            SettingsGlassSection(
                                title = stringResource(R.string.settings_cache_title),
                                icon = Icons.Default.Lyrics
                            ) {
                                LyricsCacheToolbar(
                                    count = lyricsCache.size,
                                    sizeBytes = lyricsCache.sumOf { it.sizeBytes },
                                    onRefresh = { viewModel.refreshLyricsCache() },
                                    onClearAll = { viewModel.clearAllLyricsCache() }
                                )
                                Spacer(Modifier.height(8.dp))
                                if (lyricsCache.isEmpty()) {
                                    EmptyCacheMessage()
                                }
                            }

                            lyricsCache.forEach { entry ->
                                LyricsCacheRow(
                                    entry = entry,
                                    onDelete = { viewModel.deleteLyricsCache(entry) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGlassSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    BackdropLiquidGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 26.dp,
        blurRadius = 10.dp,
        surfaceAlpha = 0.032f,
        highlightAlpha = 0.62f,
        shadowAlpha = 0.13f,
        useSharedBackdrop = false
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    options: List<Pair<String, ThemeMode>>,
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit
) {
    Text(
        title,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, mode) ->
            val isSelected = selected == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.46f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelected(mode) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        label,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsAccentChoiceRow(
    title: String,
    selected: AccentColorStyle,
    onSelected: (AccentColorStyle) -> Unit
) {
    Text(
        title,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            stringResource(R.string.settings_accent_apple_red) to AccentColorStyle.APPLE_RED,
            stringResource(R.string.settings_accent_android_blue) to AccentColorStyle.ANDROID_BLUE
        ).forEach { (label, style) ->
            val isSelected = selected == style
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.46f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelected(style) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        label,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Text(
            ">",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun PlaylistBackupPanel(
    playlists: List<Playlist>,
    selectedPlaylistIds: Set<String>,
    includePlaylistBackup: Boolean,
    includeListeningHistoryBackup: Boolean,
    onSelectionChange: (Set<String>) -> Unit,
    onIncludePlaylistBackupChange: (Boolean) -> Unit,
    onIncludeListeningHistoryBackupChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onImport: () -> Unit
) {
    val hasBackupSelection = includeListeningHistoryBackup || (includePlaylistBackup && selectedPlaylistIds.isNotEmpty())
    Text(
        stringResource(R.string.settings_playlist_backup_desc),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.settings_backup_import))
        }
        OutlinedButton(
            onClick = onExport,
            enabled = hasBackupSelection,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.settings_backup_export))
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = onShare,
        enabled = hasBackupSelection,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.settings_backup_share))
    }
    Spacer(Modifier.height(16.dp))
    BackupCategoryRow(
        icon = Icons.Default.QueueMusic,
        title = stringResource(R.string.settings_backup_include_playlists),
        subtitle = stringResource(R.string.settings_backup_include_playlists_subtitle),
        checked = includePlaylistBackup,
        onCheckedChange = onIncludePlaylistBackupChange
    )
    BackupCategoryRow(
        icon = Icons.Default.History,
        title = stringResource(R.string.settings_backup_include_diary),
        subtitle = stringResource(R.string.settings_backup_include_diary_subtitle),
        checked = includeListeningHistoryBackup,
        onCheckedChange = onIncludeListeningHistoryBackupChange
    )
    Spacer(Modifier.height(10.dp))
    if (!includePlaylistBackup) {
        return
    }
    if (playlists.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.settings_backup_no_playlists),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                fontWeight = FontWeight.Medium
            )
        }
        return
    }
    val allSelected = selectedPlaylistIds.size == playlists.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                onSelectionChange(if (allSelected) emptySet() else playlists.map { it.id }.toSet())
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = allSelected,
            onCheckedChange = {
                onSelectionChange(if (it) playlists.map { playlist -> playlist.id }.toSet() else emptySet())
            }
        )
        Text(
            stringResource(R.string.settings_backup_select_all),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
    }
    playlists.forEach { playlist ->
        val selected = playlist.id in selectedPlaylistIds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    onSelectionChange(
                        if (selected) selectedPlaylistIds - playlist.id else selectedPlaylistIds + playlist.id
                    )
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = {
                    onSelectionChange(
                        if (it) selectedPlaylistIds + playlist.id else selectedPlaylistIds - playlist.id
                    )
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.settings_backup_song_count, playlist.songIds.size),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun BackupCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemePreviewRow() {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.settings_current_accent),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary
        ).forEach { color ->
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun LyricsCacheToolbar(
    count: Int,
    sizeBytes: Long,
    onRefresh: () -> Unit,
    onClearAll: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_cache_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    stringResource(R.string.settings_cache_summary, count, formatBytes(sizeBytes)),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                    fontSize = 12.sp
                )
            }
            TextButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_refresh))
            }
        }
        if (count > 0) {
            OutlinedButton(
                onClick = onClearAll,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_clear_all_cached_lyrics))
            }
        }
    }
}

@Composable
private fun EmptyCacheMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.settings_no_cached_lyrics),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LyricsCacheRow(
    entry: LyricsCacheEntry,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (entry.isSynced) stringResource(R.string.settings_cache_type_synced) else stringResource(R.string.settings_cache_type_plain),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                entry.artist,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.preview.isNotBlank()) {
                Text(
                    entry.preview,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.42f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                stringResource(R.string.settings_cache_row_meta, entry.lineCount, formatBytes(entry.sizeBytes), formatDate(entry.updatedAt)),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.36f),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        FloatingGlassIconButton(
            icon = Icons.Default.DeleteOutline,
            contentDescription = stringResource(R.string.settings_delete_cached_lyrics),
            onClick = onDelete,
            width = 42.dp,
            height = 34.dp,
            cornerRadius = 14.dp,
            tint = MaterialTheme.colorScheme.error
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(Locale.US, kb)
    return "%.1f MB".format(Locale.US, kb / 1024.0)
}

private fun formatDate(timeMs: Long): String {
    if (timeMs <= 0L) return "-"
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timeMs))
}
