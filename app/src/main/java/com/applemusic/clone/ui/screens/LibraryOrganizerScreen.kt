package com.applemusic.clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.model.*
import com.applemusic.clone.ui.components.FloatingGlassIconButton
import com.applemusic.clone.ui.components.BackdropLiquidGlass
import com.applemusic.clone.ui.components.LiquidGlassSegmentedControl
import com.applemusic.clone.ui.components.LiquidGlassDialogModifier
import com.applemusic.clone.ui.components.LiquidGlassDialogShape
import com.applemusic.clone.ui.components.liquidGlassDialogColor
import com.applemusic.clone.viewmodel.MusicViewModel
import java.text.DateFormat
import java.util.Date

private enum class OrganizerTab { Issues, Songs, History }

@Composable
fun LibraryOrganizerScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val songs by viewModel.songs.collectAsState()
    val report by viewModel.libraryHealth.collectAsState()
    val history by viewModel.organizerHistory.collectAsState()
    val scanning by viewModel.isOrganizerScanning.collectAsState()
    var tab by remember { mutableStateOf(OrganizerTab.Issues) }
    var editingSong by remember { mutableStateOf<AudioItem?>(null) }
    var mergeIssue by remember { mutableStateOf<LibraryIssue?>(null) }
    var filterIssue by remember { mutableStateOf<LibraryIssue?>(null) }
    var pendingUndo by remember { mutableStateOf<OrganizerHistoryBatch?>(null) }

    LaunchedEffect(Unit) { viewModel.scanLibraryHealth() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingGlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("音乐资料整理", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("只修改 App 内显示，不会改动原文件", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.52f))
            }
            IconButton(onClick = viewModel::scanLibraryHealth, enabled = !scanning, modifier = Modifier.size(48.dp)) {
                if (scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "重新扫描")
            }
        }

        OrganizerSummary(report, scanning)
        OrganizerTabs(tab) { tab = it }

        when (tab) {
            OrganizerTab.Issues -> LazyColumn(
                contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 160.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!scanning && report.issues.isEmpty()) item { OrganizerEmpty("资料库状态很好", "暂未发现需要处理的元数据问题。") }
                items(report.issues, key = { "${it.type}:${it.songIds.hashCode()}" }) { issue ->
                    IssueRow(issue) {
                        if (issue.type == LibraryIssueType.DUPLICATE_ALBUM) mergeIssue = issue
                        else { filterIssue = issue; tab = OrganizerTab.Songs }
                    }
                }
            }
            OrganizerTab.Songs -> {
                val visibleSongs = remember(songs, filterIssue) {
                    val ids = filterIssue?.songIds?.toSet()
                    if (ids == null) songs else songs.filter { it.id in ids }
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 160.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filterIssue != null) item {
                        AssistChip(onClick = { filterIssue = null }, label = { Text("${filterIssue!!.title} · 清除筛选") }, leadingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) })
                    }
                    items(visibleSongs, key = { it.id }) { song -> SongMetadataRow(song) { editingSong = song } }
                }
            }
            OrganizerTab.History -> LazyColumn(
                contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (history.isEmpty()) item { OrganizerEmpty("还没有整理记录", "保存元数据或合并专辑后，可在这里撤销。") }
                items(history, key = { it.batchId }) { batch ->
                    HistoryRow(batch) { pendingUndo = batch }
                }
            }
        }
    }

    editingSong?.let { song ->
        MetadataEditorDialog(song, onDismiss = { editingSong = null }) { draft ->
            viewModel.saveSongMetadataOverrides(song, draft)
            editingSong = null
        }
    }
    mergeIssue?.let { issue ->
        MergePreviewDialog(issue, songs, onDismiss = { mergeIssue = null }) { album, artist ->
            viewModel.mergeAlbumIssue(issue.songIds, album, artist)
            mergeIssue = null
        }
    }
    pendingUndo?.let { batch ->
        AlertDialog(
            onDismissRequest = { pendingUndo = null }, modifier = LiquidGlassDialogModifier,
            shape = LiquidGlassDialogShape, containerColor = liquidGlassDialogColor(),
            title = { Text("撤销这次整理？", fontWeight = FontWeight.Bold) },
            text = { Text("将恢复 ${batch.affectedSongs} 首歌曲在「${batch.label}」之前的 App 内元数据。") },
            confirmButton = { TextButton(onClick = { viewModel.undoMetadataBatch(batch.batchId); pendingUndo = null }) { Text("撤销") } },
            dismissButton = { TextButton(onClick = { pendingUndo = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun OrganizerSummary(report: LibraryHealthReport, scanning: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary.copy(.09f)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(62.dp).clip(RoundedCornerShape(31.dp)).background(MaterialTheme.colorScheme.primary.copy(.16f)), contentAlignment = Alignment.Center) {
                Text(if (scanning) "…" else report.healthScore.toString(), fontSize = 23.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (scanning) "正在检查资料库" else "资料库健康度", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${report.totalSongs} 首歌曲 · ${report.issues.size} 类问题", color = MaterialTheme.colorScheme.onSurface.copy(.56f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun OrganizerTabs(selected: OrganizerTab, onSelect: (OrganizerTab) -> Unit) {
    LiquidGlassSegmentedControl(
        items = listOf(
            OrganizerTab.Issues to "问题",
            OrganizerTab.Songs to "手动修正",
            OrganizerTab.History to "撤销记录"
        ),
        selected = selected,
        onSelected = onSelect,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun IssueRow(issue: LibraryIssue, onClick: () -> Unit) {
    val icon = when (issue.type) {
        LibraryIssueType.DUPLICATE_ALBUM -> Icons.Default.Merge
        LibraryIssueType.MISSING_ARTWORK -> Icons.Default.ImageNotSupported
        LibraryIssueType.MISSING_TRACK -> Icons.Default.FormatListNumbered
        LibraryIssueType.MISSING_YEAR -> Icons.Default.CalendarMonth
        LibraryIssueType.MISSING_GENRE -> Icons.Default.Category
        LibraryIssueType.MISSING_ALBUM_ARTIST -> Icons.Default.People
    }
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surfaceVariant.copy(.42f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(issue.title, fontWeight = FontWeight.SemiBold)
                Text(issue.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.55f))
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(.3f))
        }
    }
}

@Composable
private fun SongMetadataRow(song: AudioItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(song.artist, song.album, song.year.takeIf { it > 0 }?.toString(), song.genre.takeIf(String::isNotBlank)).filterNotNull().joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.Edit, "编辑", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HistoryRow(batch: OrganizerHistoryBatch, onUndo: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(.38f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(batch.label, fontWeight = FontWeight.SemiBold)
                Text("${batch.affectedSongs} 首 · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(batch.createdAt))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.52f))
            }
            TextButton(onClick = onUndo) { Text("撤销") }
        }
    }
}

@Composable
private fun OrganizerEmpty(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.LibraryMusic, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary.copy(.6f))
        Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.Bold); Text(detail, color = MaterialTheme.colorScheme.onSurface.copy(.52f), fontSize = 13.sp)
    }
}

@Composable
private fun MetadataEditorDialog(song: AudioItem, onDismiss: () -> Unit, onSave: (MetadataDraft) -> Unit) {
    var title by remember { mutableStateOf(song.title) }; var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }; var albumArtist by remember { mutableStateOf(song.albumArtist) }
    var track by remember { mutableStateOf(song.trackNumber.takeIf { it > 0 }?.toString().orEmpty()) }
    var disc by remember { mutableStateOf(song.discNumber.takeIf { it > 0 }?.toString().orEmpty()) }
    var year by remember { mutableStateOf(song.year.takeIf { it > 0 }?.toString().orEmpty()) }; var genre by remember { mutableStateOf(song.genre) }
    AlertDialog(
        onDismissRequest = onDismiss, modifier = LiquidGlassDialogModifier, shape = LiquidGlassDialogShape, containerColor = liquidGlassDialogColor(),
        title = { Text("修正歌曲信息", fontWeight = FontWeight.Bold) },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.heightIn(max = 480.dp)) {
            item { Text("仅保存 App 内覆盖信息", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
            item { OutlinedTextField(title, { title = it }, label = { Text("歌曲") }, singleLine = true) }
            item { OutlinedTextField(artist, { artist = it }, label = { Text("艺人") }, singleLine = true) }
            item { OutlinedTextField(album, { album = it }, label = { Text("专辑") }, singleLine = true) }
            item { OutlinedTextField(albumArtist, { albumArtist = it }, label = { Text("专辑艺术家") }, singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(track, { track = it.filter(Char::isDigit) }, label = { Text("曲号") }, modifier = Modifier.weight(1f)); OutlinedTextField(disc, { disc = it.filter(Char::isDigit) }, label = { Text("碟号") }, modifier = Modifier.weight(1f)) } }
            item { OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("年份") }, modifier = Modifier.fillMaxWidth()) }
            item {
                OutlinedTextField(genre, { genre = it }, label = { Text("流派（整张专辑）") }, supportingText = { Text("保存后会统一应用到《${song.album}》的全部歌曲") }, modifier = Modifier.fillMaxWidth())
            }
        } },
        confirmButton = { TextButton(onClick = { onSave(MetadataDraft(title, artist, album, albumArtist, track.toIntOrNull(), disc.toIntOrNull(), year.toIntOrNull(), genre)) }, enabled = title.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun MergePreviewDialog(issue: LibraryIssue, songs: List<AudioItem>, onDismiss: () -> Unit, onMerge: (String, String) -> Unit) {
    var album by remember { mutableStateOf(issue.suggestedAlbum.orEmpty()) }; var artist by remember { mutableStateOf(issue.suggestedAlbumArtist.orEmpty()) }
    val affected = remember(issue, songs) { songs.filter { it.id in issue.songIds.toSet() } }
    AlertDialog(
        onDismissRequest = onDismiss, modifier = LiquidGlassDialogModifier, shape = LiquidGlassDialogShape, containerColor = liquidGlassDialogColor(),
        icon = { Icon(Icons.Default.Merge, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("合并预览", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("将 ${affected.size} 首歌曲统一到同一专辑身份；原文件不会被修改。", fontSize = 13.sp)
            OutlinedTextField(album, { album = it }, label = { Text("统一专辑名") }, singleLine = true)
            OutlinedTextField(artist, { artist = it }, label = { Text("统一专辑艺术家") }, singleLine = true)
            affected.take(6).forEach { Text("• ${it.title} — ${it.artist}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (affected.size > 6) Text("以及另外 ${affected.size - 6} 首", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
        } },
        confirmButton = { TextButton(onClick = { onMerge(album, artist) }, enabled = album.isNotBlank() && artist.isNotBlank()) { Text("确认合并") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
