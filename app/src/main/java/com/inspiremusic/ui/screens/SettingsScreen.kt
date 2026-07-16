package com.inspiremusic.ui.screens

import android.widget.Toast
import android.os.Environment
import android.os.StatFs
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.inspiremusic.ui.theme.LocalAppIsDark
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inspiremusic.R
import com.inspiremusic.model.AudioItem
import com.inspiremusic.model.LyricsCacheEntry
import com.inspiremusic.model.ArtworkCacheEntry
import com.inspiremusic.model.Playlist
import com.inspiremusic.settings.AccentColorStyle
import com.inspiremusic.settings.AiProvider
import com.inspiremusic.settings.AiSavedProfile
import com.inspiremusic.settings.AiSettingsController
import com.inspiremusic.settings.LocalAppSettingsController
import com.inspiremusic.settings.ThemeMode
import com.inspiremusic.settings.GlassRenderingMode
import com.inspiremusic.ui.components.BackdropLiquidGlass
import com.inspiremusic.ui.components.FloatingGlassIconButton
import com.inspiremusic.ui.components.LiquidGlassSegmentedControl
import com.inspiremusic.ui.components.glassClickable
import com.inspiremusic.viewmodel.MusicViewModel
import com.inspiremusic.data.LocalSendBackupSender
import com.inspiremusic.data.AiClient
import com.inspiremusic.data.DiagnosticLogger
import com.inspiremusic.data.LocalSendDevice
import com.inspiremusic.data.LocalSendReceiveSession
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

private enum class SettingsPage {
    Main,
    PlaylistBackup,
    LocalSendTransfer,
    MusicStorage,
    LyricsCache,
    ArtworkCache,
    ArtworkSettings,
    AiConfiguration
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
    onBack: () -> Unit,
    onNavigateTo: (String) -> Unit = {}
) {
    val controller = LocalAppSettingsController.current
    val appSettings by controller.settings.collectAsState()
    val lyricsCache by viewModel.lyricsCacheEntries.collectAsState()
    val artworkCache by viewModel.artworkCacheEntries.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val context = LocalContext.current
    val aiSettingsController = remember(context) { AiSettingsController(context) }
    val aiConfiguration by aiSettingsController.configuration.collectAsState()
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val localSendSender = remember(context) { LocalSendBackupSender(context) }
    var page by remember { mutableStateOf(SettingsPage.Main) }
    var selectedArtworkAlbum by remember { mutableStateOf<ArtworkAlbumTarget?>(null) }
    var selectedPlaylistIds by remember(playlists) { mutableStateOf(playlists.map { it.id }.toSet()) }
    var includePlaylistBackup by remember { mutableStateOf(true) }
    var includeListeningHistoryBackup by remember { mutableStateOf(true) }
    var includeRecentlyPlayedBackup by remember { mutableStateOf(true) }
    var includeAiConfigurationBackup by remember { mutableStateOf(true) }
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
            val root = JSONObject(json)
            val imported = viewModel.importPlaylistBackup(json)
            val aiCount = root.optJSONObject("aiConfiguration")?.let(aiSettingsController::importBackup) ?: 0
            imported to aiCount
        }
        val message = result.fold(
            onSuccess = { (imported, aiCount) ->
                context.getString(
                    R.string.settings_backup_import_success,
                    imported.importedPlaylists,
                    imported.importedSongs,
                    imported.missingSongs,
                    imported.importedListeningRecords,
                    imported.importedRecentlyPlayed
                ) + if (aiCount > 0) " · 已恢复 $aiCount 个 AI 配置" else ""
            },
            onFailure = { context.getString(R.string.settings_backup_import_failed) }
        )
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    fun buildBackupContent(): String {
        val root = JSONObject(viewModel.buildPlaylistBackup(
            selectedPlaylistIds = selectedPlaylistIds,
            includePlaylists = includePlaylistBackup,
            includeListeningHistory = includeListeningHistoryBackup,
            includeRecentlyPlayed = includeRecentlyPlayedBackup
        ))
        root.getJSONObject("included").put("aiConfiguration", includeAiConfigurationBackup)
        if (includeAiConfigurationBackup) root.put("aiConfiguration", aiSettingsController.exportBackup())
        return root.toString(2)
    }
    val exportDiagnosticsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(DiagnosticLogger.exportText().toByteArray(Charsets.UTF_8))
            }
        }.onSuccess { Toast.makeText(context, "诊断日志已导出", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "无法导出诊断日志", Toast.LENGTH_SHORT).show() }
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
                    val importResult = runCatching {
                        val root = JSONObject(json)
                        val imported = viewModel.importPlaylistBackup(json)
                        val aiCount = root.optJSONObject("aiConfiguration")?.let(aiSettingsController::importBackup) ?: 0
                        imported to aiCount
                    }
                    receiveMessage = importResult.fold(
                        onSuccess = { (imported, aiCount) ->
                            context.getString(
                                R.string.settings_backup_import_success,
                                imported.importedPlaylists,
                                imported.importedSongs,
                                imported.missingSongs,
                                imported.importedListeningRecords,
                                imported.importedRecentlyPlayed
                            ) + if (aiCount > 0) " · 已恢复 $aiCount 个 AI 配置" else ""
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
            .testTag("screen_settings")
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
                        SettingsPage.AiConfiguration -> "AI 配置"
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
                                title = stringResource(R.string.settings_glass_rendering_title),
                                icon = Icons.Default.BlurOn
                            ) {
                                GlassRenderingChoiceRow(
                                    selected = appSettings.glassRenderingMode,
                                    onSelected = controller::setGlassRenderingMode
                                )
                            }

                            SettingsGlassSection(
                                title = "播放体验",
                                icon = Icons.Default.PlayCircle
                            ) {
                                SettingsStatusRow(
                                    icon = Icons.Default.PlayCircle,
                                    title = "无缝播放",
                                    subtitle = "连续专辑使用 Media3 的无间断衔接",
                                    status = "已启用"
                                )
                                CrossfadeChoiceRow(appSettings.crossfadeSeconds, controller::setCrossfadeSeconds)
                                SettingsSwitchRow(
                                    icon = Icons.Default.Equalizer,
                                    title = "ReplayGain 音量均衡",
                                    subtitle = "为音量标准化预留安全余量，减少曲目切换突兀感",
                                    checked = appSettings.replayGainEnabled,
                                    onCheckedChange = controller::setReplayGainEnabled
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Default.History,
                                    title = "恢复上次队列与位置",
                                    subtitle = "重新打开 App 后保留歌曲顺序和准确进度",
                                    checked = appSettings.restorePlaybackQueue,
                                    onCheckedChange = controller::setRestorePlaybackQueue
                                )
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
                                title = "AI",
                                icon = Icons.Default.AutoAwesome
                            ) {
                                SettingsNavigationRow(
                                    icon = Icons.Default.AutoAwesome,
                                    title = "AI 配置",
                                    subtitle = if (aiConfiguration.hasApiKey) {
                                        "${aiConfiguration.provider.displayName} · ${aiConfiguration.model}"
                                    } else {
                                        "配置自己的 API Key，用于灵感与 AI 日记分析"
                                    },
                                    onClick = { page = SettingsPage.AiConfiguration }
                                )
                            }

                            SettingsGlassSection(
                                title = stringResource(R.string.settings_data),
                                icon = Icons.Default.Settings
                            ) {
                                SettingsNavigationRow(
                                    icon = Icons.Default.AutoFixHigh,
                                    title = "音乐资料管理",
                                    subtitle = "扫描重复专辑、修正资料并管理撤销记录",
                                    onClick = { onNavigateTo("library/organizer") }
                                )
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
                                SettingsNavigationRow(
                                    icon = Icons.Default.BugReport,
                                    title = "导出诊断日志",
                                    subtitle = "包含加载、播放与崩溃线索，不包含音乐文件",
                                    onClick = { exportDiagnosticsLauncher.launch("inspire_music_diagnostics.txt") }
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
                                        uriHandler.openUri("https://github.com/Kwanlam08/InspireMusic/releases")
                                    }
                                )
                                SettingsNavigationRow(
                                    icon = Icons.Default.OpenInNew,
                                    title = stringResource(R.string.settings_github_page),
                                    subtitle = stringResource(R.string.settings_github_page_subtitle),
                                    onClick = {
                                        uriHandler.openUri("https://github.com/Kwanlam08/InspireMusic")
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
                                    includeAiConfigurationBackup = includeAiConfigurationBackup,
                                    onSelectionChange = { selectedPlaylistIds = it },
                                    onIncludePlaylistBackupChange = { includePlaylistBackup = it },
                                    onIncludeListeningHistoryBackupChange = { includeListeningHistoryBackup = it },
                                    onIncludeRecentlyPlayedBackupChange = { includeRecentlyPlayedBackup = it },
                                    onIncludeAiConfigurationBackupChange = { includeAiConfigurationBackup = it },
                                    onExport = {
                                        pendingBackupContent = buildBackupContent()
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
                            AnimatedContent(
                                targetState = target,
                                transitionSpec = {
                                    val forward = targetState != null
                                    (
                                        slideInHorizontally(tween(260)) { width -> if (forward) width / 5 else -width / 5 } + fadeIn(tween(220))
                                    ) togetherWith (
                                        slideOutHorizontally(tween(200)) { width -> if (forward) -width / 6 else width / 6 } + fadeOut(tween(160))
                                    ) using SizeTransform(clip = false)
                                },
                                label = "artworkEditorTransition"
                            ) { selectedTarget ->
                                if (selectedTarget == null) {
                                    ArtworkAlbumList(songs = songs, onSelect = { selectedArtworkAlbum = it })
                                } else {
                                    ArtworkAlbumEditor(
                                        target = selectedTarget,
                                        onChooseImage = { artworkPicker.launch("image/*") },
                                        onUseDefault = { viewModel.resetArtworkForAlbum(selectedTarget.album, selectedTarget.albumId) },
                                        onBack = { selectedArtworkAlbum = null }
                                    )
                                }
                            }
                        }

                        SettingsPage.AiConfiguration -> {
                            SettingsGlassSection(
                                title = "AI 配置",
                                icon = Icons.Default.AutoAwesome
                            ) {
                                AiConfigurationPanel(
                                    controller = aiSettingsController,
                                    configuration = aiConfiguration
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

@Composable
private fun AiConfigurationPanel(
    controller: AiSettingsController,
    configuration: com.inspiremusic.settings.AiConfiguration
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedProfiles by controller.profiles.collectAsState()
    val activeProfileId by controller.activeProfileId.collectAsState()
    var provider by remember { mutableStateOf(configuration.provider) }
    var baseUrl by remember { mutableStateOf(configuration.baseUrl) }
    var model by remember { mutableStateOf(configuration.model) }
    var apiKey by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var renamingProfile by remember { mutableStateOf<AiSavedProfile?>(null) }
    var testState by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    LaunchedEffect(configuration) {
        provider = configuration.provider
        baseUrl = configuration.baseUrl
        model = configuration.model
        apiKey = ""
        testState = null
    }

    Text(
        "API Key 仅加密保存在这台设备，不会进入备份、APK 或 GitHub。",
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    Spacer(Modifier.height(16.dp))

    Text("提供商", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.66f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { providerMenuExpanded = true },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(provider.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text("⌄", fontSize = 18.sp)
    }
    if (providerMenuExpanded) {
        AiProviderGlassDialog(
            selected = provider,
            onDismiss = { providerMenuExpanded = false },
            onSelect = { option ->
                provider = option
                baseUrl = option.defaultBaseUrl
                model = option.defaultModel
                providerMenuExpanded = false
            }
        )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = baseUrl,
        onValueChange = { baseUrl = it },
        label = { Text("Base URL") },
        supportingText = { Text(if (provider.protocol == com.inspiremusic.settings.AiProtocol.OLLAMA) "Ollama 可填写局域网设备地址" else "可按服务商要求修改") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = model,
        onValueChange = { model = it },
        label = { Text("模型") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        label = { Text(if (configuration.hasApiKey) "API Key（留空则保留现有 Key）" else "API Key") },
        supportingText = { Text(if (provider.protocol == com.inspiremusic.settings.AiProtocol.OLLAMA) "Ollama 默认不需要 Key" else "不会显示或导出已保存的 Key") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = profileName,
        onValueChange = { profileName = it.take(36) },
        label = { Text("配置名称") },
        placeholder = { Text("例如：我的 DeepSeek") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = {
                controller.saveProfile(profileName, provider, baseUrl, model, apiKey)
                profileName = ""
                Toast.makeText(context, "AI 配置已保存", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).height(46.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("保存", fontWeight = FontWeight.SemiBold) }
        Button(
            enabled = !testing,
            onClick = {
                controller.save(provider, baseUrl, model, apiKey)
                testing = true
                testState = null
                scope.launch {
                    testState = AiClient.testConnection(context).fold(
                        onSuccess = { "连接成功：$it" },
                        onFailure = { "连接失败：${it.message ?: "请检查配置"}" }
                    )
                    testing = false
                }
            },
            modifier = Modifier.weight(1f).height(46.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("测试连接", fontWeight = FontWeight.SemiBold)
        }
    }
    if (configuration.hasApiKey) {
        TextButton(onClick = { controller.clearApiKey() }) {
            Text("移除已保存的 Key")
        }
    }
    testState?.let { message ->
        Text(
            message,
            color = if (message.startsWith("连接成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    Spacer(Modifier.height(16.dp))
    Text(
        text = "配置档案",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "给这组服务商、地址、模型和密钥起一个名字。之后可一键切换或删除。",
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
    if (savedProfiles.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "已保存的配置",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        savedProfiles.forEach { profile ->
            val isActive = profile.id == activeProfileId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)
                    )
                    .border(
                        1.dp,
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        RoundedCornerShape(18.dp)
                    )
                    .glassClickable {
                        if (controller.activateProfile(profile.id)) {
                            Toast.makeText(context, "已切换到 ${profile.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(start = 14.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.provider.displayName} · ${profile.model.ifBlank { "默认模型" }}${if (profile.hasApiKey) " · 已存 Key" else ""}",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "当前使用中",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { renamingProfile = profile }) {
                    Icon(Icons.Default.Edit, contentDescription = "重命名 ${profile.name}", modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = {
                    controller.duplicateProfile(profile.id)
                    Toast.makeText(context, "已复制 ${profile.name}", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制 ${profile.name}", modifier = Modifier.size(19.dp))
                }
                TextButton(onClick = { controller.deleteProfile(profile.id) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    renamingProfile?.let { profile ->
        AiProfileRenameDialog(
            profile = profile,
            onDismiss = { renamingProfile = null },
            onConfirm = { name ->
                if (controller.renameProfile(profile.id, name)) {
                    renamingProfile = null
                    Toast.makeText(context, "配置已重命名", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "名称不能为空或与现有配置重复", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun AiProviderGlassDialog(
    selected: AiProvider,
    onDismiss: () -> Unit,
    onSelect: (AiProvider) -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val dialogScale by animateFloatAsState(
        if (entered) 1f else .94f,
        spring(dampingRatio = .78f, stiffness = Spring.StiffnessMediumLow),
        label = "providerDialogScale"
    )
    val dialogAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(180), label = "providerDialogAlpha")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackdropLiquidGlass(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .heightIn(max = 620.dp)
                .graphicsLayer { scaleX = dialogScale; scaleY = dialogScale; alpha = dialogAlpha }
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = if (LocalAppIsDark.current) 0.90f else 0.84f),
                    RoundedCornerShape(30.dp)
                ),
            cornerRadius = 30.dp,
            blurRadius = 18.dp,
            surfaceAlpha = if (LocalAppIsDark.current) 0.12f else 0.07f,
            highlightAlpha = 0.72f,
            shadowAlpha = 0.22f,
            useSharedBackdrop = false
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("选择 AI 提供商", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("选择后仍可修改地址和模型", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f), fontSize = 12.sp)
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(AiProvider.entries, key = { it.value }) { option ->
                        val active = option == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(19.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = .14f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)
                                )
                                .glassClickable { onSelect(option) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (active) .20f else .09f)),
                                contentAlignment = Alignment.Center
                            ) { Text(option.displayName.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(option.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    option.defaultModel.ifBlank { "自定义模型" },
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (active) Icon(Icons.Default.Check, "当前提供商", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiProfileRenameDialog(
    profile: AiSavedProfile,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val dialogScale by animateFloatAsState(
        if (entered) 1f else .94f,
        spring(dampingRatio = .78f, stiffness = Spring.StiffnessMediumLow),
        label = "renameDialogScale"
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackdropLiquidGlass(
            modifier = Modifier
                .fillMaxWidth(.88f)
                .graphicsLayer { scaleX = dialogScale; scaleY = dialogScale }
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .90f), RoundedCornerShape(28.dp)),
            cornerRadius = 28.dp,
            blurRadius = 16.dp,
            surfaceAlpha = .10f,
            highlightAlpha = .70f,
            shadowAlpha = .22f,
            useSharedBackdrop = false
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("重命名 AI 配置", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(36) },
                    label = { Text("配置名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("保存") }
                }
            }
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
    LiquidGlassSegmentedControl(
        items = options.map { (label, value) -> value to label },
        selected = selected,
        onSelected = onSelected,
        height = 48.dp
    )
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
    LiquidGlassSegmentedControl(
        items = listOf(
            AccentColorStyle.APPLE_RED to stringResource(R.string.settings_accent_apple_red),
            AccentColorStyle.ANDROID_BLUE to stringResource(R.string.settings_accent_android_blue)
        ),
        selected = selected,
        onSelected = onSelected,
        height = 48.dp
    )
}

@Composable
private fun GlassRenderingChoiceRow(
    selected: GlassRenderingMode,
    onSelected: (GlassRenderingMode) -> Unit
) {
    LiquidGlassSegmentedControl(
        items = listOf(
            GlassRenderingMode.AUTO to stringResource(R.string.settings_glass_rendering_auto),
            GlassRenderingMode.COMPATIBLE to stringResource(R.string.settings_glass_rendering_compatible)
        ),
        selected = selected,
        onSelected = onSelected,
        height = 48.dp
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(
            when (selected) {
                GlassRenderingMode.AUTO -> R.string.settings_glass_rendering_auto_summary
                GlassRenderingMode.COMPATIBLE -> R.string.settings_glass_rendering_compatible_summary
            }
        ),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
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
private fun SettingsStatusRow(icon: ImageVector, title: String, subtitle: String, status: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primary.copy(.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(.52f), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text(status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun CrossfadeChoiceRow(selectedSeconds: Int, onSelected: (Int) -> Unit) {
    Text(
        "交叉淡化",
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
    Text(
        if (selectedSeconds == 0) "关闭，优先保持原始无缝衔接" else "在歌曲末尾用 ${selectedSeconds} 秒平滑淡出并衔接下一首",
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
        fontSize = 12.sp
    )
    Spacer(Modifier.height(8.dp))
    LiquidGlassSegmentedControl(
        items = listOf(0, 3, 6, 12).map { seconds ->
            seconds to if (seconds == 0) "关闭" else "${seconds}s"
        },
        selected = selectedSeconds,
        onSelected = onSelected,
        height = 50.dp
    )
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
            .glassClickable(onClick = onClick)
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
    includeAiConfigurationBackup: Boolean,
    onSelectionChange: (Set<String>) -> Unit,
    onIncludePlaylistBackupChange: (Boolean) -> Unit,
    onIncludeListeningHistoryBackupChange: (Boolean) -> Unit,
    onIncludeRecentlyPlayedBackupChange: (Boolean) -> Unit,
    onIncludeAiConfigurationBackupChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onLocalSend: () -> Unit,
    onImport: () -> Unit
) {
    val hasBackupSelection = includeListeningHistoryBackup ||
        includeRecentlyPlayedBackup ||
        includeAiConfigurationBackup ||
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
    BackupCategoryRow(
        icon = Icons.Default.AutoAwesome,
        title = "AI 配置",
        subtitle = "备份提供商、地址、模型和配置名称；API Key 仍只保存在本机。",
        checked = includeAiConfigurationBackup,
        onCheckedChange = onIncludeAiConfigurationBackupChange
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
            song.album.trim().lowercase().replace(Regex("\\s+"), " ")
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
