package com.applemusic.clone.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.R
import com.applemusic.clone.model.LyricsCacheEntry
import com.applemusic.clone.settings.AccentColorStyle
import com.applemusic.clone.settings.LocalAppSettingsController
import com.applemusic.clone.settings.ThemeMode
import com.applemusic.clone.ui.components.FloatingGlassIconButton
import com.applemusic.clone.viewmodel.MusicViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val controller = LocalAppSettingsController.current
    val appSettings by controller.settings.collectAsState()
    val lyricsCache by viewModel.lyricsCacheEntries.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshLyricsCache()
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
                    onClick = onBack
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
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
        }

        item {
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
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
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
        }

        items(
            items = lyricsCache,
            key = { it.path },
            contentType = { "lyrics_cache" }
        ) { entry ->
            LyricsCacheRow(
                entry = entry,
                onDelete = { viewModel.deleteLyricsCache(entry) }
            )
        }

        item {
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
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(
                if (isDark) Color.White.copy(alpha = 0.034f) else Color.White.copy(alpha = 0.20f),
                shape
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.055f else 0.16f),
                        Color.Transparent,
                        Color.Black.copy(alpha = if (isDark) 0.052f else 0.012f)
                    )
                ),
                shape
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.36f else 0.62f),
                        Color.White.copy(alpha = if (isDark) 0.060f else 0.13f),
                        Color.Black.copy(alpha = if (isDark) 0.20f else 0.075f)
                    )
                ),
                shape
            )
            .padding(18.dp)
    ) {
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
