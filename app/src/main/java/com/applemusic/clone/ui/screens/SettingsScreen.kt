package com.applemusic.clone.ui.screens

import android.widget.Toast
import android.os.Environment
import android.os.StatFs
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.model.LyricsCacheEntry
import com.applemusic.clone.model.ArtworkCacheEntry
import com.applemusic.clone.model.Playlist
import com.applemusic.clone.settings.AccentColorStyle
import com.applemusic.clone.settings.LocalAppSettingsController
import com.applemusic.clone.settings.ThemeMode
import com.applemusic.clone.ui.components.BackdropLiquidGlass
import com.applemusic.clone.ui.components.FloatingGlassIconButton
import com.applemusic.clone.viewmodel.MusicViewModel
import com.applemusic.clone.data.LocalSendBackupSender
import com.applemusic.clone.data.LocalSendDevice
import com.applemusic.clone.data.LocalSendReceiveSession
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsPage {
    Main,
    PlaylistBackup,
    LocalSendTransfer,
    MusicStorage,
    LyricsCache,
    ArtworkCache,
    ArtworkSettings
}

private data class ArtworkAlbumTarget(
    val album: String,
    val artist: String,
    val albumId: Long,
    val artworkUri: Uri?
)

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val controller = LocalAppSettingsController.current
    val appSettings by controller.settings.collectAsState()
    val lyricsCache by viewModel.lyricsCacheEntries.collectAsState()
    val artworkCache by viewModel.artworkCacheEntries.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val localSendSender = remember(context) { LocalSendBackupSender(context) }
    var page by remember { mutableStateOf(SettingsPage.Main) }
    var selectedArtworkAlbum by remember { mutableStateOf<ArtworkAlbumTarget?>(null) }
    var selectedPlaylistIds by remember(playlists) { mutableStateOf(playlists.map { it.id }.toSet()) }
    var includePlaylistBackup by remember { mutableStateOf(true) }
    var includeListeningHistoryBackup by remember { mutableStateOf(true) }
    var includeRecentlyPlayedBackup by remember { mutableStateOf(true) }
    var pendingBackupContent by remember { mutableStateOf("") }
    var localSendDevices by remember { mutableStateOf<List<LocalSendDevice>>(emptyList()) }
    var isScanningLocalSend by remember { mutableStateOf(false) }
    var isSendingLocalSend by remember { mutableStateOf(false) }
    var receiveSession by remember { mutableStateOf<LocalSendReceiveSession?>(null) }
    var isStartingReceive by remember { mutableStateOf(false) }
    var receiveMessage by remember { mutableStateOf("") }
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
                    it.importedListeningRecords,
                    it.importedRecentlyPlayed
                )
            },
            onFailure = { context.getString(R.string.settings_backup_import_failed) }
        )
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    fun buildBackupContent(): String {
        return viewModel.buildPlaylistBackup(
            selectedPlaylistIds = selectedPlaylistIds,
            includePlaylists = includePlaylistBackup,
            includeListeningHistory = includeListeningHistoryBackup,
            includeRecentlyPlayed = includeRecentlyPlayedBackup
        )
    }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = selectedArtworkAlbum
        if (uri != null && target != null) {
            viewModel.setCustomArtworkForAlbum(target.album, target.albumId, uri)
        }
    }
    fun scanLocalSendDevices() {
        coroutineScope.launch {
            isScanningLocalSend = true
            localSendDevices = localSendSender.discoverNearbyDevices()
            isScanningLocalSend = false
        }
    }
    fun openLocalSendTransferPage() {
        page = SettingsPage.LocalSendTransfer
        localSendDevices = emptyList()
        scanLocalSendDevices()
    }
    fun sendPlaylistBackupWithLocalSend(device: LocalSendDevice) {
        coroutineScope.launch {
            isSendingLocalSend = true
            val backupContent = buildBackupContent()
            val fileName = "inspire_music_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
            val result = localSendSender.sendBackupToDevice(device, fileName, backupContent)
            isSendingLocalSend = false
            val message = result.fold(
                onSuccess = {
                    context.getString(R.string.settings_backup_localsend_success, it.deviceName)
                },
                onFailure = {
                    it.message?.takeIf { message -> message.isNotBlank() }
                        ?: context.getString(R.string.settings_backup_localsend_failed)
                }
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    fun startLocalSendReceive() {
        coroutineScope.launch {
            isStartingReceive = true
            receiveMessage = ""
            val result = localSendSender.startReceiveSession { json ->
                coroutineScope.launch {
                    val importResult = runCatching { viewModel.importPlaylistBackup(json) }
                    receiveMessage = importResult.fold(
                        onSuccess = {
                            context.getString(
                                R.string.settings_backup_import_success,
                                it.importedPlaylists,
                                it.importedSongs,
                                it.missingSongs,
                                it.importedListeningRecords,
                                it.importedRecentlyPlayed
                            )
                        },
                        onFailure = { context.getString(R.string.settings_backup_import_failed) }
                    )
                }
            }
            isStartingReceive = false
            result.fold(
                onSuccess = {
                    receiveSession?.close()
                    receiveSession = it
                    receiveMessage = context.getString(R.string.settings_backup_localsend_receive_ready)
                },
                onFailure = {
                    receiveMessage = context.getString(R.string.settings_backup_localsend_receive_failed)
                }
            )
        }
    }
    fun stopLocalSendReceive() {
        receiveSession?.close()
        receiveSession = null
        receiveMessage = ""
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLyricsCache()
        viewModel.refreshArtworkCache()
    }

    BackHandler(enabled = page != SettingsPage.Main) {
        if (page == SettingsPage.ArtworkSettings && selectedArtworkAlbum != null) {
            selectedArtworkAlbum = null
        } else {
            page = SettingsPage.Main
        }
    }

    LaunchedEffect(page) {
        if (page != SettingsPage.LocalSendTransfer) {
            stopLocalSendReceive()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopLocalSendReceive()
        }
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
                if (page != SettingsPage.Main) {
                    FloatingGlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        onClick = { page = SettingsPage.Main }
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    when (page) {
                        SettingsPage.Main -> stringResource(R.string.settings_title)
                        SettingsPage.PlaylistBackup -> stringResource(R.string.settings_playlist_backup)
                        SettingsPage.LocalSendTransfer -> stringResource(R.string.settings_backup_localsend)
                        SettingsPage.MusicStorage -> stringResource(R.string.settings_music_storage)
                        SettingsPage.LyricsCache -> stringResource(R.string.settings_cache_title)
                        SettingsPage.ArtworkCache -> stringResource(R.string.settings_artwork_cache_title)
                        SettingsPage.ArtworkSettings -> stringResource(R.string.settings_artwork_settings_title)
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
                                title = stringResource(R.string.settings_artwork),
                                icon = Icons.Default.Album
                            ) {
                                SettingsNavigationRow(
                                    icon = Icons.Default.Album,
                                    title = stringResource(R.string.settings_artwork_settings_title),
                                    subtitle = stringResource(R.string.settings_artwork_settings_subtitle),
                                    onClick = { page = SettingsPage.ArtworkSettings }
                                )
                                SettingsNavigationRow(
                                    icon = Icons.Default.Album,
                                    title = stringResource(R.string.settings_artwork_cache_title),
                                    subtitle = stringResource(
                                        R.string.settings_artwork_cache_summary,
                                        artworkCache.size,
                                        formatBytes(artworkCache.sumOf { it.sizeBytes })
                                    ),
                                    onClick = { page = SettingsPage.ArtworkCache }
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
                                SettingsNavigationRow(
                                    icon = Icons.Default.Storage,
                                    title = stringResource(R.string.settings_music_storage),
                                    subtitle = stringResource(
                                        R.string.settings_music_storage_subtitle,
                                        formatBytes(songs.sumOf { it.sizeBytes })
                                    ),
                                    onClick = { page = SettingsPage.MusicStorage }
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
                                SettingsNavigationRow(
                                    icon = Icons.Default.OpenInNew,
                                    title = stringResource(R.string.settings_github_page),
                                    subtitle = stringResource(R.string.settings_github_page_subtitle),
                                    onClick = {
                                        uriHandler.openUri("https://github.com/Kwanlam08/AppleMusicClone")
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
                                    includeRecentlyPlayedBackup = includeRecentlyPlayedBackup,
                                    onSelectionChange = { selectedPlaylistIds = it },
                                    onIncludePlaylistBackupChange = { includePlaylistBackup = it },
                                    onIncludeListeningHistoryBackupChange = { includeListeningHistoryBackup = it },
                                    onIncludeRecentlyPlayedBackupChange = { includeRecentlyPlayedBackup = it },
                                    onExport = {
                                        pendingBackupContent = viewModel.buildPlaylistBackup(
                                            selectedPlaylistIds = selectedPlaylistIds,
                                            includePlaylists = includePlaylistBackup,
                                            includeListeningHistory = includeListeningHistoryBackup,
                                            includeRecentlyPlayed = includeRecentlyPlayedBackup
                                        )
                                        exportBackupLauncher.launch("inspire_music_backup.json")
                                    },
                                        onLocalSend = { openLocalSendTransferPage() },
                                        onImport = { importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
                                    )
                                }
                        }

                        SettingsPage.LocalSendTransfer -> {
                            SettingsGlassSection(
                                title = stringResource(R.string.settings_backup_localsend),
                                icon = Icons.Default.AutoAwesome
                            ) {
                                LocalSendTransferPanel(
                                    devices = localSendDevices,
                                    isScanning = isScanningLocalSend,
                                    isSending = isSendingLocalSend,
                                    isStartingReceive = isStartingReceive,
                                    receiveSession = receiveSession,
                                    receiveMessage = receiveMessage,
                                    onScan = { scanLocalSendDevices() },
                                    onSend = { sendPlaylistBackupWithLocalSend(it) },
                                    onStartReceive = { startLocalSendReceive() },
                                    onStopReceive = { stopLocalSendReceive() }
                                )
                            }
                        }

                        SettingsPage.MusicStorage -> {
                            SettingsGlassSection(
                                title = stringResource(R.string.settings_music_storage),
                                icon = Icons.Default.Storage
                            ) {
                                MusicStoragePanel(songs = songs)
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

                        SettingsPage.ArtworkCache -> {
                            SettingsGlassSection(
                                title = stringResource(R.string.settings_artwork_cache_title),
                                icon = Icons.Default.Album
                            ) {
                                ArtworkCacheToolbar(
                                    count = artworkCache.size,
                                    sizeBytes = artworkCache.sumOf { it.sizeBytes },
                                    onRefresh = { viewModel.refreshArtworkCache() },
                                    onClearAll = { viewModel.clearAllArtworkCache() }
                                )
                                Spacer(Modifier.height(8.dp))
                                if (artworkCache.isEmpty()) ArtworkEmptyCacheMessage()
                            }
                            artworkCache.forEach { entry ->
                                ArtworkCacheRow(entry = entry, onDelete = { viewModel.deleteArtworkCache(entry) })
                            }
                        }

                        SettingsPage.ArtworkSettings -> {
                            val target = selectedArtworkAlbum
                            if (target == null) {
                                ArtworkAlbumList(
                                    songs = songs,
                                    onSelect = { selectedArtworkAlbum = it }
                                )
                            } else {
                                ArtworkAlbumEditor(
                                    target = target,
                                    onChooseImage = { artworkPicker.launch("image/*") },
                                    onUseDefault = {
                                        viewModel.resetArtworkForAlbum(target.album, target.albumId)
                                    },
                                    onBack = { selectedArtworkAlbum = null }
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
        useSharedBackdrop = true
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

private data class MusicStorageInfo(
    val musicBytes: Long,
    val totalBytes: Long,
    val freeBytes: Long,
    val songCount: Int
) {
    val usedBytes: Long = (totalBytes - freeBytes).coerceAtLeast(0L)
    val musicFraction: Float = if (totalBytes > 0L) {
        (musicBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val usedFraction: Float = if (totalBytes > 0L) {
        (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
}

@Composable
private fun MusicStoragePanel(songs: List<AudioItem>) {
    val storageInfo = remember(songs) { calculateMusicStorageInfo(songs) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.settings_music_storage_desc),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    RoundedCornerShape(24.dp)
                )
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_music_storage_music),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            formatBytes(storageInfo.musicBytes),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Black,
                            fontSize = 34.sp,
                            maxLines = 1
                        )
                    }
                    Text(
                        stringResource(R.string.settings_music_storage_of_total, formatBytes(storageInfo.totalBytes)),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(18.dp))
                StorageUsageBar(
                    musicFraction = storageInfo.musicFraction,
                    usedFraction = storageInfo.usedFraction
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StorageLegendDot(
                        color = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.settings_music_storage_music)
                    )
                    StorageLegendDot(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                        label = stringResource(R.string.settings_music_storage_other)
                    )
                    StorageLegendDot(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.065f),
                        label = stringResource(R.string.settings_music_storage_free)
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StorageMetricCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.settings_music_storage_songs),
                value = storageInfo.songCount.toString()
            )
            StorageMetricCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.settings_music_storage_average),
                value = formatBytes(if (storageInfo.songCount > 0) storageInfo.musicBytes / storageInfo.songCount else 0L)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StorageMetricCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.settings_music_storage_used),
                value = formatBytes(storageInfo.usedBytes)
            )
            StorageMetricCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.settings_music_storage_available),
                value = formatBytes(storageInfo.freeBytes)
            )
        }
    }
}

@Composable
private fun StorageUsageBar(
    musicFraction: Float,
    usedFraction: Float
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.065f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(usedFraction.coerceAtLeast(musicFraction))
                .height(18.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(musicFraction)
                .height(18.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
        )
    }
}

@Composable
private fun StorageLegendDot(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StorageMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                RoundedCornerShape(20.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Text(
                label,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun calculateMusicStorageInfo(songs: List<AudioItem>): MusicStorageInfo {
    val musicBytes = songs.sumOf { song ->
        song.sizeBytes.takeIf { it > 0L } ?: runCatching { java.io.File(song.data).length() }.getOrDefault(0L)
    }.coerceAtLeast(0L)
    val statFs = runCatching { StatFs(Environment.getExternalStorageDirectory().absolutePath) }.getOrNull()
    val totalBytes = statFs?.totalBytes?.coerceAtLeast(musicBytes) ?: musicBytes
    val freeBytes = statFs?.availableBytes?.coerceAtLeast(0L) ?: 0L
    return MusicStorageInfo(
        musicBytes = musicBytes,
        totalBytes = totalBytes,
        freeBytes = freeBytes,
        songCount = songs.size
    )
}

@Composable
private fun LocalSendTransferPanel(
    devices: List<LocalSendDevice>,
    isScanning: Boolean,
    isSending: Boolean,
    isStartingReceive: Boolean,
    receiveSession: LocalSendReceiveSession?,
    receiveMessage: String,
    onScan: () -> Unit,
    onSend: (LocalSendDevice) -> Unit,
    onStartReceive: () -> Unit,
    onStopReceive: () -> Unit
) {
    Text(
        stringResource(R.string.settings_backup_localsend_desc),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    Spacer(Modifier.height(14.dp))
    LocalSendReceiveCard(
        receiveSession = receiveSession,
        receiveMessage = receiveMessage,
        isStartingReceive = isStartingReceive,
        onStartReceive = onStartReceive,
        onStopReceive = onStopReceive
    )
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.settings_backup_localsend_nearby),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onScan,
            enabled = !isScanning && !isSending
        ) {
            Text(stringResource(R.string.settings_backup_localsend_rescan))
        }
    }
    if (isScanning && devices.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.settings_backup_localsend_scanning))
        }
    } else if (devices.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.settings_backup_localsend_empty),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
    devices.forEach { device ->
        Spacer(Modifier.height(8.dp))
        LocalSendDeviceRow(
            device = device,
            enabled = !isSending,
            onClick = { onSend(device) }
        )
    }
    if (isSending) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.settings_backup_localsend_sending),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun LocalSendReceiveCard(
    receiveSession: LocalSendReceiveSession?,
    receiveMessage: String,
    isStartingReceive: Boolean,
    onStartReceive: () -> Unit,
    onStopReceive: () -> Unit
) {
    val isReceiving = receiveSession != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isReceiving) 0.14f else 0.08f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = if (isReceiving) 0.28f else 0.12f),
                RoundedCornerShape(20.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.50f)),
            contentAlignment = Alignment.Center
        ) {
            if (isStartingReceive) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isReceiving) stringResource(R.string.settings_backup_localsend_receiving)
                else stringResource(R.string.settings_backup_localsend_receive),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                receiveMessage.ifBlank {
                    if (isReceiving && receiveSession.address.isNotBlank()) {
                        stringResource(R.string.settings_backup_localsend_receive_address, receiveSession.address)
                    } else {
                        stringResource(R.string.settings_backup_localsend_receive_subtitle)
                    }
                },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        TextButton(
            onClick = if (isReceiving) onStopReceive else onStartReceive,
            enabled = !isStartingReceive
        ) {
            Text(
                if (isReceiving) stringResource(R.string.settings_backup_localsend_stop)
                else stringResource(R.string.settings_backup_localsend_start)
            )
        }
    }
}

@Composable
private fun LocalSendDeviceRow(
    device: LocalSendDevice,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                device.alias,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${device.host}:${device.port} · ${device.protocol.uppercase()}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f),
                fontSize = 12.sp
            )
        }
        Text(
            stringResource(R.string.settings_backup_localsend_send),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
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
    BackdropLiquidGlass(
        modifier = Modifier.fillMaxWidth().height(46.dp),
        cornerRadius = 18.dp,
        blurRadius = 12.dp,
        surfaceAlpha = 0.024f,
        highlightAlpha = 0.62f,
        shadowAlpha = 0.16f,
        useSharedBackdrop = true
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val itemWidth = maxWidth / options.size
            val selectedIndex = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
            val sliderOffset by animateDpAsState(
                itemWidth * selectedIndex,
                spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow),
                label = "themeModeSlider"
            )
            BackdropLiquidGlass(
                modifier = Modifier.offset(x = sliderOffset).width(itemWidth).height(38.dp),
                cornerRadius = 14.dp,
                blurRadius = 7.dp,
                surfaceAlpha = 0.085f,
                highlightAlpha = 0.78f,
                shadowAlpha = 0.12f,
                useSharedBackdrop = true
            ) {}
            Row(Modifier.fillMaxSize()) {
                options.forEach { (label, mode) ->
                    val isSelected = selected == mode
                    Box(
                        modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
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
    includeRecentlyPlayedBackup: Boolean,
    onSelectionChange: (Set<String>) -> Unit,
    onIncludePlaylistBackupChange: (Boolean) -> Unit,
    onIncludeListeningHistoryBackupChange: (Boolean) -> Unit,
    onIncludeRecentlyPlayedBackupChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onLocalSend: () -> Unit,
    onImport: () -> Unit
) {
    val hasBackupSelection = includeListeningHistoryBackup ||
        includeRecentlyPlayedBackup ||
        (includePlaylistBackup && selectedPlaylistIds.isNotEmpty())
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
        onClick = onLocalSend,
        enabled = hasBackupSelection,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.settings_backup_localsend))
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
    BackupCategoryRow(
        icon = Icons.Default.Refresh,
        title = stringResource(R.string.settings_backup_include_recent),
        subtitle = stringResource(R.string.settings_backup_include_recent_subtitle),
        checked = includeRecentlyPlayedBackup,
        onCheckedChange = onIncludeRecentlyPlayedBackupChange
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
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(Locale.US, mb)
    return "%.1f GB".format(Locale.US, mb / 1024.0)
}

@Composable
private fun ArtworkCacheToolbar(
    count: Int,
    sizeBytes: Long,
    onRefresh: () -> Unit,
    onClearAll: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_artwork_cache_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    stringResource(R.string.settings_artwork_cache_summary, count, formatBytes(sizeBytes)),
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
                Text(stringResource(R.string.settings_clear_all_cached_artwork))
            }
        }
    }
}

@Composable
private fun ArtworkEmptyCacheMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.settings_no_cached_artwork),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ArtworkAlbumList(
    songs: List<AudioItem>,
    onSelect: (ArtworkAlbumTarget) -> Unit
) {
    val albums = remember(songs) {
        songs.groupBy { song ->
            if (song.albumId > 0L) "id:${song.albumId}" else "${song.albumArtist}\u0000${song.album}"
        }
            .values
            .mapNotNull { tracks ->
                val first = tracks.firstOrNull() ?: return@mapNotNull null
                ArtworkAlbumTarget(first.album, first.albumArtist.ifBlank { first.artist }, first.albumId, first.albumArtUri)
            }
            .sortedWith(compareBy({ it.artist.lowercase(Locale.getDefault()) }, { it.album.lowercase(Locale.getDefault()) }))
    }
    SettingsGlassSection(
        title = stringResource(R.string.settings_artwork_settings_title),
        icon = Icons.Default.Album
    ) {
        Text(
            stringResource(R.string.settings_artwork_settings_hint),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        albums.forEach { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelect(album) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = album.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(album.album, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(album.artist, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.36f), modifier = Modifier.graphicsLayer { rotationZ = 180f })
            }
        }
    }
}

@Composable
private fun ArtworkAlbumEditor(
    target: ArtworkAlbumTarget,
    onChooseImage: () -> Unit,
    onUseDefault: () -> Unit,
    onBack: () -> Unit
) {
    SettingsGlassSection(
        title = target.album,
        icon = Icons.Default.Album
    ) {
        AsyncImage(
            model = target.artworkUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(22.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Spacer(Modifier.height(14.dp))
        Text(target.artist, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = onChooseImage,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_artwork_choose_local))
        }
        TextButton(
            onClick = onUseDefault,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.settings_artwork_use_default))
        }
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(stringResource(R.string.action_back)) }
    }
}

@Composable
private fun ArtworkCacheRow(
    entry: ArtworkCacheEntry,
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
        AsyncImage(
            model = entry.path,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.album.ifBlank { entry.title },
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.artist,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatBytes(entry.sizeBytes)} · ${formatDate(entry.updatedAt)}",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.36f),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        FloatingGlassIconButton(
            icon = Icons.Default.DeleteOutline,
            contentDescription = stringResource(R.string.settings_delete_cached_artwork),
            onClick = onDelete,
            width = 42.dp,
            height = 34.dp,
            cornerRadius = 14.dp,
            tint = MaterialTheme.colorScheme.error,
            useSharedBackdrop = true
        )
    }
}

private fun formatDate(timeMs: Long): String {
    if (timeMs <= 0L) return "-"
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timeMs))
}
