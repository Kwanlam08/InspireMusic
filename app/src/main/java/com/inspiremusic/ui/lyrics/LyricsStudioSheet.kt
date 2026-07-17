package com.inspiremusic.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiremusic.model.LyricIssueType
import com.inspiremusic.model.LyricTextAlignment
import com.inspiremusic.model.LyricsDisplaySettings
import com.inspiremusic.ui.components.LiquidGlassBottomSheetFrame
import com.inspiremusic.ui.components.LiquidGlassBottomSheetModifier
import com.inspiremusic.ui.components.LiquidGlassBottomSheetShape
import com.inspiremusic.ui.theme.LocalAppIsDark
import com.inspiremusic.viewmodel.MusicViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsStudioSheet(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.lyricsDisplaySettings.collectAsState()
    val raw by viewModel.currentLyricsRaw.collectAsState()
    val source by viewModel.currentLyricsSource.collectAsState()
    val versions by viewModel.lyricVersions.collectAsState()
    val candidates by viewModel.lyricSearchCandidates.collectAsState()
    val busy by viewModel.isLyricsStudioBusy.collectAsState()
    val message by viewModel.lyricsStudioMessage.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val currentIndex by viewModel.currentLyricIndex.collectAsState()
    val currentPosition by viewModel.currentPositionMs.collectAsState()
    val favorites by viewModel.favoriteLyricLines.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isDark = LocalAppIsDark.current
    val textColor = if (isDark) Color.White else Color(0xFF17171A)
    val secondary = textColor.copy(alpha = 0.58f)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf(raw) }
    var editorDirty by remember { mutableStateOf(false) }
    LaunchedEffect(raw) {
        if (!editorDirty) editor = raw
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = LiquidGlassBottomSheetModifier,
        containerColor = Color.Transparent,
        shape = LiquidGlassBottomSheetShape,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.46f)
    ) {
        LiquidGlassBottomSheetFrame(useSharedBackdrop = false) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("歌词工作室", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("编辑、校准、双语与显示", color = secondary, fontSize = 13.sp)
                    }
                    if (busy) Text("处理中…", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                Spacer(Modifier.height(14.dp))
                StudioTabs(tab = tab, onTab = { tab = it }, textColor = textColor)
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.clearLyricsStudioMessage() }
                    ) {
                        Text(it, color = textColor, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().height(540.dp).verticalScroll(rememberScrollState())
                ) {
                    when (tab) {
                        0 -> DisplayPanel(settings, textColor, secondary, viewModel::updateLyricsDisplaySettings, viewModel::resetLyricsDisplaySettings)
                        1 -> EditPanel(
                            editor = editor,
                            onEditor = { editor = it; editorDirty = true },
                            onSave = { viewModel.saveLyricsOverride(editor); editorDirty = false },
                            lyricsCount = lyrics.size,
                            currentIndex = currentIndex,
                            currentPosition = currentPosition,
                            onSyncCurrent = { viewModel.syncLyricLine(currentIndex, currentPosition) },
                            onShift = viewModel::shiftCurrentLyrics,
                            textColor = textColor,
                            secondary = secondary
                        )
                        2 -> SourcePanel(
                            source = source,
                            versions = versions,
                            candidates = candidates,
                            onSearch = viewModel::searchOnlineLyricVersions,
                            onApply = viewModel::applyOnlineLyricCandidate,
                            onRestore = viewModel::restoreLyricVersion,
                            onTranslate = viewModel::translateCurrentLyrics,
                            textColor = textColor,
                            secondary = secondary
                        )
                        else -> CollectionPanel(
                            favoriteCount = favorites.count { it.audioId == currentSong?.id },
                            onReport = { viewModel.reportCurrentLyrics(it) },
                            textColor = textColor,
                            secondary = secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTabs(tab: Int, onTab: (Int) -> Unit, textColor: Color) {
    val labels = listOf("显示", "编辑", "来源", "收藏")
    Row(
        modifier = Modifier.fillMaxWidth().background(textColor.copy(alpha = 0.055f), RoundedCornerShape(18.dp)).padding(3.dp)
    ) {
        labels.forEachIndexed { index, label ->
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = if (tab == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                modifier = Modifier.weight(1f).clickable { onTab(index) }
            ) {
                Text(
                    label,
                    color = if (tab == index) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.58f),
                    fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 11.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DisplayPanel(
    value: LyricsDisplaySettings,
    textColor: Color,
    secondary: Color,
    onUpdate: (LyricsDisplaySettings) -> Unit,
    onReset: () -> Unit
) {
    SectionTitle(Icons.Default.FormatSize, "歌词排版", "当前句默认使用 W900 Black；预先保留行高，不会切句跳动。", textColor, secondary)
    StudioSlider("竖屏字号", "${value.portraitFontSizeSp.toInt()} sp", value.portraitFontSizeSp, 16f..38f) { onUpdate(value.copy(portraitFontSizeSp = it)) }
    StudioSlider("横屏字号", "${value.landscapeFontSizeSp.toInt()} sp", value.landscapeFontSizeSp, 15f..34f) { onUpdate(value.copy(landscapeFontSizeSp = it)) }
    StudioSlider("翻译字号", "${value.translationFontSizeSp.toInt()} sp", value.translationFontSizeSp, 11f..24f) { onUpdate(value.copy(translationFontSizeSp = it)) }
    StudioSlider("行间距", "${value.lineSpacingDp.toInt()} dp", value.lineSpacingDp, 4f..30f) { onUpdate(value.copy(lineSpacingDp = it)) }
    Text("当前句字重", color = textColor, fontWeight = FontWeight.SemiBold)
    ChoiceRow(listOf(600 to "Semi", 700 to "Bold", 900 to "Black"), value.activeFontWeight, textColor) { onUpdate(value.copy(activeFontWeight = it)) }
    Text("对齐方式", color = textColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    ChoiceRow(listOf(LyricTextAlignment.START to "左对齐", LyricTextAlignment.CENTER to "居中"), value.alignment, textColor) { onUpdate(value.copy(alignment = it)) }
    ToggleRow("显示双语翻译", "本地双语、在线版本或 AI 翻译都会使用这一行。", value.showTranslation, textColor, secondary) { onUpdate(value.copy(showTranslation = it)) }
    StudioSlider("蓝牙延迟补偿", "${value.bluetoothDelayMs} ms", value.bluetoothDelayMs.toFloat(), -2_000f..2_000f) { onUpdate(value.copy(bluetoothDelayMs = it.toLong())) }
    OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("恢复默认显示") }
}

@Composable
private fun EditPanel(
    editor: String,
    onEditor: (String) -> Unit,
    onSave: () -> Unit,
    lyricsCount: Int,
    currentIndex: Int,
    currentPosition: Long,
    onSyncCurrent: () -> Unit,
    onShift: (Long) -> Unit,
    textColor: Color,
    secondary: Color
) {
    SectionTitle(Icons.Default.Edit, "App 内编辑", "只写入 App 覆盖文件；保存前自动留下旧版本。支持 LRC、增强 LRC 与 TTML。", textColor, secondary)
    OutlinedTextField(
        value = editor,
        onValueChange = onEditor,
        modifier = Modifier.fillMaxWidth().height(240.dp),
        label = { Text("歌词内容") },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        shape = RoundedCornerShape(18.dp)
    )
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("保存覆盖版本") }
    HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp), color = textColor.copy(alpha = 0.10f))
    Text("时间校准", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text("当前 ${lyricsCount} 行 · 第 ${(currentIndex + 1).coerceAtLeast(0)} 行 · ${formatMs(currentPosition)}", color = secondary, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        OutlinedButton(onClick = { onShift(-500) }, modifier = Modifier.weight(1f)) { Text("整体 -0.5s") }
        OutlinedButton(onClick = { onShift(500) }, modifier = Modifier.weight(1f)) { Text("整体 +0.5s") }
    }
    Button(onClick = onSyncCurrent, enabled = currentIndex >= 0, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("把当前句定点到播放位置")
    }
}

@Composable
private fun SourcePanel(
    source: String,
    versions: List<com.inspiremusic.model.LyricVersion>,
    candidates: List<com.inspiremusic.model.LyricSearchCandidate>,
    onSearch: () -> Unit,
    onApply: (com.inspiremusic.model.LyricSearchCandidate) -> Unit,
    onRestore: (com.inspiremusic.model.LyricVersion) -> Unit,
    onTranslate: (String) -> Unit,
    textColor: Color,
    secondary: Color
) {
    SectionTitle(Icons.Default.CloudDownload, "歌词来源", "当前：$source。在线搜索优先列出同步且包含双语行的版本。", textColor, secondary)
    Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text("搜索在线版本") }
    candidates.forEach { candidate ->
        StudioCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(candidate.trackName, color = textColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${candidate.artistName} · ${candidate.durationSeconds.toInt()}s", color = secondary, fontSize = 12.sp)
                    Text(
                        if (candidate.translationLineCount > 0) "双语 ${candidate.translationLineCount} 行" else if (candidate.syncedLyrics != null) "逐行同步" else "纯文本",
                        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp
                    )
                }
                OutlinedButton(onClick = { onApply(candidate) }) { Text("使用") }
            }
        }
    }
    Text("没有合适的双语版本时", color = textColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
    Text("可调用你已配置的 AI，按行翻译并保存在本机。", color = secondary, fontSize = 13.sp)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("简体中文", "繁體中文", "English", "日本語", "한국어").forEach { language ->
            OutlinedButton(onClick = { onTranslate(language) }) { Text(language) }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp), color = textColor.copy(alpha = 0.10f))
    SectionTitle(Icons.Default.History, "版本历史", "最多为每首歌保留 12 个版本。", textColor, secondary)
    if (versions.isEmpty()) Text("保存或替换一次歌词后，旧版本会出现在这里。", color = secondary, fontSize = 13.sp)
    versions.forEach { version ->
        StudioCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(version.sourceName, color = textColor, fontWeight = FontWeight.SemiBold)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(version.createdAt)), color = secondary, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onRestore(version) }) { Text("恢复") }
            }
        }
    }
}

@Composable
private fun CollectionPanel(
    favoriteCount: Int,
    onReport: (LyricIssueType) -> Unit,
    textColor: Color,
    secondary: Color
) {
    SectionTitle(Icons.Default.History, "歌词收藏", "长按正在播放的任意一句即可收藏或取消。当前歌曲已收藏 $favoriteCount 句。", textColor, secondary)
    HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp), color = textColor.copy(alpha = 0.10f))
    SectionTitle(Icons.Default.ReportProblem, "歌词有问题", "问题只在本机记录并进入备份，不会在未经确认时上传。", textColor, secondary)
    listOf(
        LyricIssueType.WRONG_SONG to "歌词不属于这首歌",
        LyricIssueType.TIMING to "时间轴不准确",
        LyricIssueType.INCOMPLETE to "歌词不完整",
        LyricIssueType.TRANSLATION to "翻译有误"
    ).forEach { (type, label) ->
        OutlinedButton(onClick = { onReport(type) }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Text(label) }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String, subtitle: String, textColor: Color, secondary: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = secondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun StudioSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
    }
    Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
}

@Composable
private fun <T> ChoiceRow(values: List<Pair<T, String>>, selected: T, textColor: Color, onSelect: (T) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        values.forEach { (value, label) ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (value == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else textColor.copy(alpha = 0.055f),
                modifier = Modifier.weight(1f).clickable { onSelect(value) }
            ) {
                Text(label, color = if (value == selected) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.68f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(vertical = 11.dp))
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, textColor: Color, secondary: Color, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = textColor, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = secondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun StudioCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) { Box(Modifier.padding(12.dp)) { content() } }
}

private fun formatMs(value: Long): String = "%d:%02d.%03d".format(value / 60_000, (value / 1_000) % 60, value % 1_000)
