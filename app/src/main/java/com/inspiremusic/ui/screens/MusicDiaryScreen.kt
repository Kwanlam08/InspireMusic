package com.inspiremusic.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.inspiremusic.ui.theme.LocalAppIsDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import com.inspiremusic.ui.components.AppCornerTokens
import com.inspiremusic.ui.components.ContinuousRoundedCornerShape
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inspiremusic.R
import com.inspiremusic.ui.components.appChromeContentPadding
import com.inspiremusic.model.AudioItem
import com.inspiremusic.model.DiaryAiLog
import com.inspiremusic.model.ListeningRecord
import com.inspiremusic.model.FavoriteLyricLine
import com.inspiremusic.model.MusicMemoryBuilder
import com.inspiremusic.model.MusicMemoryCollection
import com.inspiremusic.model.MusicMemoryKind
import com.inspiremusic.model.MusicMemoryMix
import com.inspiremusic.ui.components.BackdropLiquidGlass
import com.inspiremusic.ui.components.FloatingGlassIconButton
import com.inspiremusic.ui.components.LocalAppChromeController
import com.inspiremusic.ui.components.LiquidGlassSegmentedControl
import com.inspiremusic.viewmodel.MusicViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class DiaryMode {
    Day,
    Week,
    Month,
    Memory
}

private val DiaryMode.modeKey: String
    get() = when (this) {
        DiaryMode.Day -> "day"
        DiaryMode.Week -> "week"
        DiaryMode.Month -> "month"
        DiaryMode.Memory -> "memory"
    }

private val DiaryMode.defaultLabel: String
    get() = when (this) {
        DiaryMode.Day -> "日记"
        DiaryMode.Week -> "周记"
        DiaryMode.Month -> "月记"
        DiaryMode.Memory -> "回忆"
    }

private fun String.toDiaryMode(): DiaryMode = when (this) {
    "week" -> DiaryMode.Week
    "month" -> DiaryMode.Month
    "memory" -> DiaryMode.Memory
    else -> DiaryMode.Day
}

private val DiaryMode.cleanLabel: String
    get() = when (this) {
        DiaryMode.Day -> "\u65e5\u8bb0"
        DiaryMode.Week -> "\u5468\u8bb0"
        DiaryMode.Month -> "\u6708\u8bb0"
        DiaryMode.Memory -> "\u56de\u5fc6"
    }

private data class DiarySummary(
    val key: String,
    val label: String,
    val records: List<ListeningRecord>,
    val artworkBySongId: Map<Long, Any?> = emptyMap()
) {
    private val songStats = records.groupBy { it.songId }.let { groups ->
        groups.size to groups.maxByOrNull { entry ->
            entry.value.sumOf { it.duration.coerceAtLeast(0L) }
        }?.value?.firstOrNull()
    }
    val playCount: Int = records.size
    val uniqueSongCount: Int = songStats.first
    val totalDuration: Long = records.sumOf { it.duration.coerceAtLeast(0L) }
    val topSong: ListeningRecord? = songStats.second
    val topArtist: String = records
        .groupBy { it.artist.ifBlank { "-" } }
        .maxByOrNull { it.value.size }
        ?.key
        .orEmpty()
}

private data class DiaryDisplaySong(
    val record: ListeningRecord,
    val totalDuration: Long,
    val playCount: Int,
    val artwork: Any?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDiaryScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    diaryBackRequest: MutableSharedFlow<Unit>,
    onSubPageOpenChange: (Boolean) -> Unit
) {
    val records by viewModel.listeningRecords.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val favoriteLyrics by viewModel.favoriteLyricLines.collectAsState()
    val diaryAiIsLoading by viewModel.diaryAiIsLoading.collectAsState()
    val diaryAiResult by viewModel.diaryAiResult.collectAsState()
    val diaryAiError by viewModel.diaryAiError.collectAsState()
    val diaryAiLogs by viewModel.diaryAiLogs.collectAsState()
    val chromeController = LocalAppChromeController.current
    var mode by remember { mutableStateOf(DiaryMode.Day) }
    var aiAnalysisTarget by remember { mutableStateOf<Pair<DiaryMode, DiarySummary>?>(null) }
    var analysisOwner by remember { mutableStateOf<Pair<DiaryMode, String>?>(null) }
    var showLogPage by remember { mutableStateOf(false) }
    var logMode by remember { mutableStateOf(DiaryMode.Day) }
    var selectedLogId by remember { mutableStateOf<String?>(null) }
    val daySummaries = remember(records, songs) { buildDiarySummaries(records, songs, DiaryMode.Day) }
    val weekSummaries = remember(records, songs) { buildDiarySummaries(records, songs, DiaryMode.Week) }
    val monthSummaries = remember(records, songs) { buildDiarySummaries(records, songs, DiaryMode.Month) }
    val memories = remember(records, songs, favoriteIds) {
        MusicMemoryBuilder.build(
            records = records,
            availableSongIds = songs.mapTo(mutableSetOf()) { it.id },
            favoriteSongIds = favoriteIds
        )
    }
    val summariesByMode = remember(daySummaries, weekSummaries, monthSummaries) {
        mapOf(
            DiaryMode.Day.modeKey to daySummaries,
            DiaryMode.Week.modeKey to weekSummaries,
            DiaryMode.Month.modeKey to monthSummaries
        )
    }
    val modeTransition = updateTransition(targetState = mode, label = "diaryMode")
    fun summariesFor(value: DiaryMode): List<DiarySummary> = when (value) {
        DiaryMode.Day -> daySummaries
        DiaryMode.Week -> weekSummaries
        DiaryMode.Month -> monthSummaries
        DiaryMode.Memory -> emptyList()
    }
    fun itemCount(value: DiaryMode): Int = if (value == DiaryMode.Memory) 1 else summariesFor(value).size.coerceAtLeast(1)


    DisposableEffect(Unit) {
        onDispose { chromeController.setVisible(true) }
    }

    // 日志列表/日志详情打开时，通知 AppNavigation 显示全局返回按钮
    LaunchedEffect(showLogPage, selectedLogId) {
        onSubPageOpenChange(showLogPage || selectedLogId != null)
    }
    // 全局返回按钮点击：先走日记内部返回（分析页 → 关闭；日志详情 → 回列表；列表 → 回主页面）
    LaunchedEffect(diaryBackRequest) {
        diaryBackRequest.collect {
            when {
                selectedLogId != null -> selectedLogId = null
                showLogPage -> showLogPage = false
                aiAnalysisTarget != null -> aiAnalysisTarget = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_diary")
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = appChromeContentPadding()
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.diary_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    DiaryLogButton(
                        onClick = {
                            logMode = mode.takeUnless { it == DiaryMode.Memory } ?: DiaryMode.Day
                            selectedLogId = null
                            showLogPage = true
                        }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.diary_intro),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    DiarySegmentedControl(mode = mode, onModeChange = { mode = it }, includeMemories = true)
                }
            }

            if (favoriteLyrics.isNotEmpty()) {
                item {
                    FavoriteLyricsDiaryCard(
                        favorites = favoriteLyrics.take(5),
                        onPlay = viewModel::playFavoriteLyric
                    )
                }
            }

            // Keep each summary as a separate lazy item so off-screen cards do
            // not need to be composed. The existing slide/fade remains active
            // for both the outgoing and incoming modes.
            items(
                count = maxOf(itemCount(modeTransition.currentState), itemCount(modeTransition.targetState)),
                key = { index -> "diary-summary-$index" }
            ) { index ->
                modeTransition.AnimatedContent(
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (
                            fadeIn(tween(180)) + slideInHorizontally(
                                spring(stiffness = Spring.StiffnessMediumLow)
                            ) { it / 5 * direction }
                        ) togetherWith (
                            fadeOut(tween(140)) + slideOutHorizontally(
                                spring(stiffness = Spring.StiffnessMediumLow)
                            ) { -it / 7 * direction }
                        ) using SizeTransform(clip = false)
                    }
                ) { targetMode ->
                    if (targetMode == DiaryMode.Memory) {
                        if (index == 0) {
                            MusicMemoryCenter(
                                collection = memories,
                                songs = songs,
                                onPlayMix = viewModel::playList,
                                onPlaySong = viewModel::playInserted,
                                onOpenArtist = onNavigateToArtist,
                                onOpenAlbum = onNavigateToAlbum
                            )
                        }
                    } else {
                        val targetSummaries = summariesFor(targetMode)
                        if (targetSummaries.isEmpty()) {
                            if (index == 0) DiaryEmptyState()
                        } else {
                            targetSummaries.getOrNull(index)?.let { summary ->
                                DiarySummaryCard(
                                    summary = summary,
                                    onPlaySong = { songId ->
                                        songs.firstOrNull { it.id == songId }?.let(viewModel::playInserted)
                                    },
                                    onOpenArtist = onNavigateToArtist,
                                    aiExpanded = aiAnalysisTarget?.let { it.first == targetMode && it.second.key == summary.key } == true,
                                    isLoading = diaryAiIsLoading,
                                    result = if (analysisOwner == (targetMode to summary.key)) diaryAiResult else "",
                                    error = if (analysisOwner == (targetMode to summary.key)) diaryAiError else null,
                                    onToggleAi = {
                                        aiAnalysisTarget = if (aiAnalysisTarget?.let { it.first == targetMode && it.second.key == summary.key } == true) null else targetMode to summary
                                    },
                                    onAnalyze = {
                                        if (!diaryAiIsLoading) {
                                            analysisOwner = targetMode to summary.key
                                            viewModel.analyzeDiaryWithAi(
                                                prompt = buildDiaryAnalysisPrompt(targetMode, listOf(summary)),
                                                modeKey = targetMode.modeKey,
                                                modeLabel = targetMode.cleanLabel,
                                                summaryKey = summary.key,
                                                summaryLabel = summary.label,
                                                summaryText = buildDiarySummaryText(summary)
                                            )
                                        }
                                    },
                                    onOpenLogs = {
                                        aiAnalysisTarget = null
                                        logMode = targetMode
                                        selectedLogId = null
                                        showLogPage = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

        }

        AnimatedContent(
            targetState = when {
                !showLogPage -> 0
                selectedLogId == null -> 1
                else -> 2
            },
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (
                    slideInHorizontally(tween(260)) { width -> width * direction / 5 } +
                        fadeIn(tween(220))
                ) togetherWith (
                    slideOutHorizontally(tween(220)) { width -> -width * direction / 6 } +
                        fadeOut(tween(180))
                ) using SizeTransform(clip = false)
            },
            label = "diaryLogNavigation"
        ) { logPageState ->
            if (logPageState == 0) return@AnimatedContent
            val selectedLog = selectedLogId?.let { id -> diaryAiLogs.firstOrNull { it.id == id } }
            if (logPageState == 1 || selectedLog == null) {
                DiaryAiLogListPage(
                    logs = diaryAiLogs.filter { it.modeKey == logMode.modeKey },
                    mode = logMode,
                    onModeChange = {
                        logMode = it
                        selectedLogId = null
                    },
                    onBack = { showLogPage = false },
                    onOpenLog = {
                        analysisOwner = null
                        viewModel.clearDiaryAiAnalysis()
                        selectedLogId = it.id
                    }
                )
            } else {
                DiaryAiLogDetailPage(
                    log = selectedLog,
                    isLoading = diaryAiIsLoading,
                    latestResult = diaryAiResult,
                    latestError = diaryAiError,
                    onBack = { selectedLogId = null },
                    onNoteChange = { note ->
                        viewModel.updateDiaryAiNote(selectedLog.id, note)
                    },
                    onRegenerate = {
                        analysisOwner = null
                        viewModel.clearDiaryAiAnalysis()
                        val summary = summariesByMode[selectedLog.modeKey]
                            ?.firstOrNull { it.key == selectedLog.summaryKey }
                        if (summary != null) {
                            viewModel.analyzeDiaryWithAi(
                                prompt = buildDiaryAnalysisPrompt(selectedLog.modeKey.toDiaryMode(), listOf(summary)),
                                modeKey = selectedLog.modeKey,
                                modeLabel = selectedLog.modeKey.toDiaryMode().cleanLabel,
                                summaryKey = summary.key,
                                summaryLabel = summary.label,
                                summaryText = buildDiarySummaryText(summary)
                            )
                        } else {
                            viewModel.regenerateDiaryAiLog(selectedLog)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DiaryAiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackdropLiquidGlass(
        modifier = modifier
            .clip(ContinuousRoundedCornerShape(AppCornerTokens.Control))
            .clickable(onClick = onClick),
        cornerRadius = AppCornerTokens.Control,
        blurRadius = 12.dp,
        surfaceAlpha = 0.020f,
        highlightAlpha = 0.38f,
        shadowAlpha = 0.12f,
        useSharedBackdrop = true
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = stringResource(R.string.diary_ai_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DiaryLogButton(onClick: () -> Unit) {
    BackdropLiquidGlass(
        modifier = Modifier
            .size(42.dp)
            .clip(ContinuousRoundedCornerShape(AppCornerTokens.Control))
            .clickable(onClick = onClick),
        cornerRadius = AppCornerTokens.Control,
        blurRadius = 12.dp,
        surfaceAlpha = 0.030f,
        highlightAlpha = 0.34f,
        shadowAlpha = 0.12f,
        useSharedBackdrop = true
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = stringResource(R.string.diary_log_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }

}

@Composable
private fun DiaryAiLogListPage(
    logs: List<DiaryAiLog>,
    mode: DiaryMode,
    onModeChange: (DiaryMode) -> Unit,
    onBack: () -> Unit,
    onOpenLog: (DiaryAiLog) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = appChromeContentPadding()
        ) {
            item {
                DiaryLogHeader(
                    title = stringResource(R.string.diary_log_title),
                    subtitle = stringResource(R.string.diary_log_subtitle),
                    onBack = onBack
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    DiarySegmentedControl(mode = mode, onModeChange = onModeChange)
                }
            }
            if (logs.isEmpty()) {
                item {
                    DiaryLogEmptyState()
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    DiaryLogMemoCard(log = log, onClick = { onOpenLog(log) })
                }
            }
        }
    }
}

@Composable
private fun DiaryAiLogDetailPage(
    log: DiaryAiLog,
    isLoading: Boolean,
    latestResult: String,
    latestError: String?,
    onBack: () -> Unit,
    onNoteChange: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    val displayResult = if (isLoading || latestError != null || latestResult.isNotBlank()) latestResult else log.result
    val displayError = latestError
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = appChromeContentPadding()
        ) {
            item {
                DiaryLogHeader(
                    title = log.summaryLabel,
                    subtitle = "${log.modeKey.toDiaryMode().cleanLabel} · ${formatDiaryLogTime(log.updatedAt)}",
                    onBack = onBack
                )
            }
            item {
                DiaryLogDetailCard(
                    log = log,
                    result = displayResult,
                    error = displayError,
                    isLoading = isLoading,
                    onNoteChange = onNoteChange,
                    onRegenerate = onRegenerate
                )
            }
        }
    }
}

@Composable
private fun DiaryLogHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮显示在 AppNavigation 全局位置（真玻璃），此处让位
        Spacer(Modifier.width(74.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiaryLogMemoCard(
    log: DiaryAiLog,
    onClick: () -> Unit
) {
    val shape = ContinuousRoundedCornerShape(AppCornerTokens.Card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
                    )
                ),
                shape
            )
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f), shape)
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = log.summaryLabel,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${log.modeKey.toDiaryMode().cleanLabel} · ${formatDiaryLogTime(log.updatedAt)}",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = log.result.lineSequence().firstOrNull { it.isNotBlank() } ?: log.summaryText,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiaryLogDetailCard(
    log: DiaryAiLog,
    result: String,
    error: String?,
    isLoading: Boolean,
    onNoteChange: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    val shape = ContinuousRoundedCornerShape(AppCornerTokens.Card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), shape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.diary_ai_result_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = log.summaryText,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(18.dp))
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.diary_ai_analyzing),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = error ?: result,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f),
                    fontSize = 15.sp,
                    lineHeight = 23.sp
                )
            }
        }
        DiaryPersonalNoteCard(
            note = log.personalNote,
            onSave = onNoteChange
        )
        Button(
            onClick = onRegenerate,
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = ContinuousRoundedCornerShape(AppCornerTokens.Control),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(19.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.diary_ai_analyzing), fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.diary_log_regenerate), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DiaryPersonalNoteCard(
    note: String,
    onSave: (String) -> Unit
) {
    var editing by remember(note) { mutableStateOf(note.isBlank()) }
    var draft by remember(note) { mutableStateOf(note) }
    val shape = ContinuousRoundedCornerShape(AppCornerTokens.Card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), shape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.diary_personal_note_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (editing) {
                        onSave(draft)
                        editing = false
                    } else {
                        editing = true
                    }
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (editing) Icons.Outlined.Check else Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (editing && note.isNotBlank()) {
                IconButton(
                    onClick = {
                        draft = note
                        editing = false
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f)
                    )
                }
            }

        }
        Spacer(Modifier.height(10.dp))
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(stringResource(R.string.diary_personal_note_placeholder)) },
                minLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                shape = ContinuousRoundedCornerShape(AppCornerTokens.Control),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = note.ifBlank { stringResource(R.string.diary_personal_note_empty) },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (note.isBlank()) 0.48f else 0.86f),
                fontSize = 15.sp,
                lineHeight = 23.sp
            )
        }
    }
}

@Composable
private fun FavoriteLyricsDiaryCard(
    favorites: List<FavoriteLyricLine>,
    onPlay: (FavoriteLyricLine) -> Unit
) {
    BackdropLiquidGlass(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = AppCornerTokens.Card,
        blurRadius = 10.dp,
        surfaceAlpha = 0.045f,
        highlightAlpha = 0.30f,
        shadowAlpha = 0.10f,
        useSharedBackdrop = true
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FormatQuote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("收藏歌词", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("点一句，从原队列中插播并跳到那一刻", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f), fontSize = 12.sp)
                }
            }
            favorites.forEach { favorite ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(ContinuousRoundedCornerShape(AppCornerTokens.Control)).clickable { onPlay(favorite) }.padding(horizontal = 10.dp, vertical = 11.dp)
                ) {
                    Text(favorite.text, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${favorite.title} · ${favorite.artist}", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DiaryLogEmptyState() {
    BackdropLiquidGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(170.dp),
        cornerRadius = AppCornerTokens.Card,
        blurRadius = 10.dp,
        surfaceAlpha = 0.014f,
        highlightAlpha = 0.32f,
        shadowAlpha = 0.10f,
        useSharedBackdrop = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.diary_log_empty),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DiaryAiInlinePanel(
    summary: DiarySummary,
    isLoading: Boolean,
    result: String,
    error: String?,
    onAnalyze: () -> Unit,
    onOpenLogs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DiaryAiSelectedSummary(summary)
        if (result.isNotBlank() || error != null) {
            DiaryAiResultCard(result = result, error = error)
        } else if (!isLoading) {
            DiaryAiWaitingState()
        }
        Button(
            onClick = onAnalyze,
            enabled = !isLoading,
            shape = ContinuousRoundedCornerShape(AppCornerTokens.Control),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.diary_ai_analyzing), fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.diary_ai_start_single), fontWeight = FontWeight.Bold)
            }
        }
        TextButton(onClick = onOpenLogs, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.diary_log_title))
        }
    }
}

@Composable
private fun DiaryAiSelectedSummary(summary: DiarySummary) {
    val shape = ContinuousRoundedCornerShape(AppCornerTokens.Card)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.020f)
                    )
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = summary.label,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = stringResource(
                    R.string.diary_summary_sentence,
                    summary.playCount,
                    summary.uniqueSongCount,
                    formatDiaryDuration(summary.totalDuration)
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiaryAiResultCard(result: String, error: String?) {
    val shape = ContinuousRoundedCornerShape(AppCornerTokens.Card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                        Color.White.copy(alpha = 0.025f)
                    )
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), shape)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.diary_ai_result_title),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        } else {
            parseDiaryAiSections(result).forEachIndexed { index, section ->
                if (index > 0) Spacer(Modifier.height(16.dp))
                Text(
                    text = section.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = section.body,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

private data class DiaryAiSection(val title: String, val body: String)

private fun parseDiaryAiSections(result: String): List<DiaryAiSection> {
    val headings = listOf("听歌偏好", "情绪线索", "最近的你")
    val matches = Regex("(?m)^(${headings.joinToString("|")})\\s*$")
        .findAll(result)
        .toList()
    if (matches.isEmpty()) {
        return listOf(DiaryAiSection("本次分析", result.trim()))
    }

    return matches.mapIndexedNotNull { index, match ->
        val bodyStart = match.range.last + 1
        val bodyEnd = matches.getOrNull(index + 1)?.range?.first ?: result.length
        val body = result.substring(bodyStart, bodyEnd).trim()
        body.takeIf { it.isNotBlank() }?.let { DiaryAiSection(match.value.trim(), it) }
    }
}

@Composable
private fun DiaryAiWaitingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.diary_ai_waiting),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DiaryAiEmptySelection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.diary_ai_empty),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DiarySegmentedControl(
    mode: DiaryMode,
    onModeChange: (DiaryMode) -> Unit,
    includeMemories: Boolean = false
) {
    val items = buildList {
        add(DiaryMode.Day to stringResource(R.string.diary_day))
        add(DiaryMode.Week to stringResource(R.string.diary_week))
        add(DiaryMode.Month to stringResource(R.string.diary_month))
        if (includeMemories) add(DiaryMode.Memory to stringResource(R.string.diary_memory))
    }
    LiquidGlassSegmentedControl(
        items = items,
        selected = mode,
        onSelected = onModeChange
    )
}

@Composable
private fun DiarySummaryCard(
    summary: DiarySummary,
    onPlaySong: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    aiExpanded: Boolean,
    isLoading: Boolean,
    result: String,
    error: String?,
    onToggleAi: () -> Unit,
    onAnalyze: () -> Unit,
    onOpenLogs: () -> Unit
) {
    var expanded by remember(summary.key) { mutableStateOf(false) }
    val displaySongs = remember(summary) {
        summary.records
            .groupBy { it.songId }
            .mapNotNull { (songId, records) ->
                val first = records.maxByOrNull { it.playedAt } ?: return@mapNotNull null
                val totalDuration = records.sumOf { it.duration.coerceAtLeast(0L) }
                DiaryDisplaySong(
                    record = first.copy(duration = totalDuration),
                    totalDuration = totalDuration,
                    playCount = records.size,
                    artwork = summary.artworkBySongId[songId]
                )
            }
            .sortedWith(compareByDescending<DiaryDisplaySong> { it.totalDuration }.thenByDescending { it.record.playedAt })
    }
    val visibleSongs = if (expanded) displaySongs else displaySongs.take(5)

    BackdropLiquidGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        cornerRadius = AppCornerTokens.Card,
        blurRadius = 10.dp,
        surfaceAlpha = 0.012f,
        highlightAlpha = 0.34f,
        shadowAlpha = 0.10f,
        useSharedBackdrop = true
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = summary.label,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Black,
                        fontSize = 21.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.diary_summary_sentence,
                            summary.playCount,
                            summary.uniqueSongCount,
                            formatDiaryDuration(summary.totalDuration)
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                DiaryAiButton(
                    onClick = onToggleAi,
                    modifier = Modifier.size(40.dp)
                )
            }
            AnimatedVisibility(visible = aiExpanded) {
                Column(Modifier.padding(top = 16.dp)) {
                    DiaryAiInlinePanel(summary, isLoading, result, error, onAnalyze, onOpenLogs)
                }
            }
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DiaryMetric(
                    label = stringResource(R.string.diary_top_song),
                    value = summary.topSong?.title ?: "-",
                    onClick = summary.topSong?.let { song -> { onPlaySong(song.songId) } },
                    modifier = Modifier.weight(1f)
                )
                DiaryMetric(
                    label = stringResource(R.string.diary_top_artist),
                    value = summary.topArtist.ifBlank { "-" },
                    onClick = summary.topArtist.takeIf { it.isNotBlank() && it != "-" }
                        ?.let { artist -> { onOpenArtist(artist) } },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(14.dp))
            visibleSongs.forEach { item ->
                DiarySongRow(
                    record = item.record,
                    artwork = item.artwork,
                    listenDuration = item.totalDuration,
                    playCount = item.playCount,
                    onClick = { onPlaySong(item.record.songId) }
                )
            }
            if (displaySongs.size > 5) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.diary_collapse_songs)
                        } else {
                            stringResource(R.string.diary_show_all_songs, displaySongs.size)
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiaryMetric(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape = ContinuousRoundedCornerShape(AppCornerTokens.Control)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.030f),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.018f)
                    )
                ),
                shape
            )
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.060f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DiarySongRow(
    record: ListeningRecord,
    artwork: Any?,
    listenDuration: Long,
    playCount: Int,
    onClick: () -> Unit
) {
    val shape = ContinuousRoundedCornerShape(AppCornerTokens.Small)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(ContinuousRoundedCornerShape(AppCornerTokens.Small))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (artwork != null) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.74f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = record.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${record.artist} · ${formatDiaryDuration(listenDuration)} · ${playCount}x",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MusicMemoryCenter(
    collection: MusicMemoryCollection,
    songs: List<AudioItem>,
    onPlayMix: (List<AudioItem>) -> Unit,
    onPlaySong: (AudioItem) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    val songsById = remember(songs) { songs.associateBy { it.id } }
    if (collection.mixes.isEmpty()) {
        BackdropLiquidGlass(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("screen_memories")
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(210.dp),
            cornerRadius = AppCornerTokens.Card,
            useSharedBackdrop = false
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.memory_empty_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.memory_empty_description),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().testTag("screen_memories")) {
        MemoryOverview(collection)
        val hero = collection.mixes.first()
        MemoryHeroCard(
            mix = hero,
            songs = hero.songIds.mapNotNull(songsById::get),
            onPlayMix = onPlayMix,
            onPlaySong = onPlaySong,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum
        )
        collection.mixes.drop(1).forEach { mix ->
            MemoryMixCard(
                mix = mix,
                songs = mix.songIds.mapNotNull(songsById::get),
                onPlayMix = onPlayMix,
                onPlaySong = onPlaySong,
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum
            )
        }
    }
}

@Composable
private fun MemoryOverview(collection: MusicMemoryCollection) {
    val firstPlayedLabel = collection.firstPlayedAt?.let {
        SimpleDateFormat("yyyy.MM", Locale.getDefault()).format(Date(it))
    } ?: "-"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MemoryStat(stringResource(R.string.memory_stat_since), firstPlayedLabel, Modifier.weight(1f))
        MemoryStat(
            stringResource(R.string.memory_stat_heard),
            stringResource(R.string.memory_song_count, collection.uniqueSongCount),
            Modifier.weight(1f)
        )
        MemoryStat(
            stringResource(R.string.memory_stat_together),
            formatDiaryDuration(collection.totalListeningDuration),
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun MemoryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(ContinuousRoundedCornerShape(AppCornerTokens.Control))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f), fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MemoryHeroCard(
    mix: MusicMemoryMix,
    songs: List<AudioItem>,
    onPlayMix: (List<AudioItem>) -> Unit,
    onPlaySong: (AudioItem) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    val copy = memoryUiCopy(mix.kind)
    BackdropLiquidGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = AppCornerTokens.Card,
        blurRadius = 10.dp,
        surfaceAlpha = 0.016f,
        useSharedBackdrop = false
    ) {
        Column(Modifier.padding(18.dp)) {
            MemoryArtworkStrip(songs = songs.take(3), height = 132.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                copy.eyebrow.uppercase(Locale.getDefault()),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                copy.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                copy.description,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onPlayMix(songs) },
                enabled = songs.isNotEmpty(),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.memory_play), fontWeight = FontWeight.SemiBold)
            }
            songs.take(3).forEach { song ->
                MemorySongRow(song, onPlaySong, onOpenArtist, onOpenAlbum)
            }
        }
    }
}

@Composable
private fun MemoryMixCard(
    mix: MusicMemoryMix,
    songs: List<AudioItem>,
    onPlayMix: (List<AudioItem>) -> Unit,
    onPlaySong: (AudioItem) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    val copy = memoryUiCopy(mix.kind)
    val icon = when (mix.kind) {
        MusicMemoryKind.ON_THIS_DAY -> Icons.Outlined.History
        MusicMemoryKind.FORGOTTEN -> Icons.Outlined.Replay
        MusicMemoryKind.ALL_TIME -> Icons.Outlined.Headphones
        MusicMemoryKind.RECENT -> Icons.Outlined.AutoAwesome
        MusicMemoryKind.LATE_NIGHT -> Icons.Outlined.NightsStay
        MusicMemoryKind.FAVORITES -> Icons.Outlined.FavoriteBorder
        MusicMemoryKind.ARTIST_JOURNEY -> Icons.Outlined.MusicNote
    }
    BackdropLiquidGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        cornerRadius = AppCornerTokens.Card,
        blurRadius = 8.dp,
        surfaceAlpha = 0.012f,
        useSharedBackdrop = false
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(copy.eyebrow, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        copy.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onPlayMix(songs) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.memory_play), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                copy.description,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.53f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 9.dp, bottom = 7.dp)
            )
            songs.take(3).forEach { song ->
                MemorySongRow(song, onPlaySong, onOpenArtist, onOpenAlbum)
            }
        }
    }
}

@Composable
private fun MemoryArtworkStrip(songs: List<AudioItem>, height: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().height(height).clip(ContinuousRoundedCornerShape(AppCornerTokens.Card)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val displaySongs = if (songs.isEmpty()) listOf<AudioItem?>(null) else songs
        displaySongs.forEach { song ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (song?.albumArtUri != null) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemorySongRow(
    song: AudioItem,
    onPlaySong: (AudioItem) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedCornerShape(AppCornerTokens.Control))
            .clickable { onPlaySong(song) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(ContinuousRoundedCornerShape(AppCornerTokens.Small)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(song.albumArtUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artist,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { if (song.artist.isNotBlank()) onOpenArtist(song.artist) }
            )
        }
        IconButton(onClick = { if (song.album.isNotBlank()) onOpenAlbum(song.album) }) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.memory_open_album),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.34f)
            )
        }
    }
}

private data class MemoryUiCopy(
    val eyebrow: String,
    val title: String,
    val description: String
)

@Composable
private fun memoryUiCopy(kind: MusicMemoryKind): MemoryUiCopy = when (kind) {
    MusicMemoryKind.ON_THIS_DAY -> MemoryUiCopy(
        stringResource(R.string.memory_on_this_day_eyebrow),
        stringResource(R.string.memory_on_this_day_title),
        stringResource(R.string.memory_on_this_day_description)
    )
    MusicMemoryKind.FORGOTTEN -> MemoryUiCopy(
        stringResource(R.string.memory_forgotten_eyebrow),
        stringResource(R.string.memory_forgotten_title),
        stringResource(R.string.memory_forgotten_description)
    )
    MusicMemoryKind.ALL_TIME -> MemoryUiCopy(
        stringResource(R.string.memory_all_time_eyebrow),
        stringResource(R.string.memory_all_time_title),
        stringResource(R.string.memory_all_time_description)
    )
    MusicMemoryKind.RECENT -> MemoryUiCopy(
        stringResource(R.string.memory_recent_eyebrow),
        stringResource(R.string.memory_recent_title),
        stringResource(R.string.memory_recent_description)
    )
    MusicMemoryKind.LATE_NIGHT -> MemoryUiCopy(
        stringResource(R.string.memory_late_night_eyebrow),
        stringResource(R.string.memory_late_night_title),
        stringResource(R.string.memory_late_night_description)
    )
    MusicMemoryKind.FAVORITES -> MemoryUiCopy(
        stringResource(R.string.memory_favorites_eyebrow),
        stringResource(R.string.memory_favorites_title),
        stringResource(R.string.memory_favorites_description)
    )
    MusicMemoryKind.ARTIST_JOURNEY -> MemoryUiCopy(
        stringResource(R.string.memory_artist_eyebrow),
        stringResource(R.string.memory_artist_title),
        stringResource(R.string.memory_artist_description)
    )
}

@Composable
private fun DiaryEmptyState() {
    BackdropLiquidGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(150.dp),
        cornerRadius = AppCornerTokens.Card,
        blurRadius = 10.dp,
        surfaceAlpha = 0.010f,
        highlightAlpha = 0.34f,
        shadowAlpha = 0.10f,
        useSharedBackdrop = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.diary_empty),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun buildDiarySummaries(
    records: List<ListeningRecord>,
    songs: List<AudioItem>,
    mode: DiaryMode
): List<DiarySummary> {
    val locale = Locale.getDefault()
    val songLookup = songs.associateBy { it.id }
    val keyFormat = SimpleDateFormat(
        when (mode) {
            DiaryMode.Day -> "yyyy-MM-dd"
            DiaryMode.Week -> "YYYY-'W'ww"
            DiaryMode.Month -> "yyyy-MM"
            DiaryMode.Memory -> "yyyy-MM-dd"
        },
        locale
    )
    return records
        .filter { it.playedAt > 0L }
        .groupBy { keyFormat.format(Date(it.playedAt)) }
        .map { (key, grouped) ->
            DiarySummary(
                key = key,
                label = formatDiaryLabel(grouped.maxOf { it.playedAt }, mode),
                records = grouped.sortedByDescending { it.playedAt },
                artworkBySongId = grouped
                    .mapNotNull { record ->
                        songLookup[record.songId]?.albumArtUri?.let { record.songId to it }
                    }
                    .toMap(),
            )
        }
        .sortedByDescending { it.key }
}

private fun buildDiaryAnalysisPrompt(
    mode: DiaryMode,
    summaries: List<DiarySummary>
): String {
    val modeName = when (mode) {
        DiaryMode.Day -> "日记"
        DiaryMode.Week -> "周记"
        DiaryMode.Month -> "月记"
        DiaryMode.Memory -> "回忆"
    }
    val allRecords = summaries.flatMap { it.records }
    val topSongs = allRecords
        .groupBy { it.songId }
        .mapNotNull { (_, records) ->
            val first = records.maxByOrNull { it.playedAt } ?: return@mapNotNull null
            first to records.sumOf { it.duration.coerceAtLeast(0L) }
        }
        .sortedByDescending { it.second }
        .take(12)
    val topArtists = allRecords
        .groupBy { it.artist.ifBlank { "-" } }
        .mapValues { entry -> entry.value.sumOf { it.duration.coerceAtLeast(0L) } }
        .toList()
        .sortedByDescending { it.second }
        .take(8)

    return buildString {
        appendLine("请分析下面这些真实听歌记录。")
        appendLine("分析类型：$modeName")
        appendLine("选择数量：${summaries.size}")
        appendLine("总播放次数：${allRecords.size}")
        appendLine("真实累计聆听时长：${formatDiaryDuration(allRecords.sumOf { it.duration.coerceAtLeast(0L) })}")
        appendLine()
        appendLine("选择的周期：")
        summaries.forEach { summary ->
            appendLine("- ${summary.label}: ${summary.playCount} 次播放, ${summary.uniqueSongCount} 首歌, ${formatDiaryDuration(summary.totalDuration)}")
        }
        appendLine()
        appendLine("按真实聆听时长排序的歌曲：")
        topSongs.forEachIndexed { index, pair ->
            val record = pair.first
            appendLine("${index + 1}. ${record.title} - ${record.artist} / ${record.album} / ${formatDiaryDuration(pair.second)}")
        }
        appendLine()
        appendLine("按真实聆听时长排序的艺人：")
        topArtists.forEachIndexed { index, pair ->
            appendLine("${index + 1}. ${pair.first}: ${formatDiaryDuration(pair.second)}")
        }
        appendLine()
        appendLine("请输出：听歌偏好、情绪线索、最近的你、下一首可以走向哪里。")
    }
}

private fun buildDiarySummaryText(summary: DiarySummary): String {
    return "${summary.playCount} plays · ${summary.uniqueSongCount} songs · ${formatDiaryDuration(summary.totalDuration)}"
}

private fun formatDiaryLogTime(timeMs: Long): String {
    if (timeMs <= 0L) return ""
    val locale = Locale.getDefault()
    val pattern = if (locale.language == Locale.CHINESE.language) "MM月dd日 HH:mm" else "MMM d HH:mm"
    return SimpleDateFormat(pattern, locale).format(Date(timeMs))
}

private fun formatDiaryLabel(timeMs: Long, mode: DiaryMode): String {
    val locale = Locale.getDefault()
    if (mode == DiaryMode.Week) {
        val calendar = Calendar.getInstance(locale).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = timeMs
        }
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        val year = calendar.getWeekYear()
        return if (locale.language == Locale.CHINESE.language) {
            "${year}年第${week}周"
        } else {
            "Week $week $year"
        }
    }
    val pattern = if (locale.language == Locale.CHINESE.language) {
        if (mode == DiaryMode.Day) "yyyy年M月d日" else "yyyy年M月"
    } else {
        if (mode == DiaryMode.Day) "MMM d yyyy" else "MMMM yyyy"
    }
    return SimpleDateFormat(pattern, locale).format(Date(timeMs))
}

private fun formatDiaryDuration(ms: Long): String {
    val minutes = (ms / 60000L).coerceAtLeast(0L)
    return if (minutes < 60) {
        "${minutes}m"
    } else {
        "${minutes / 60}h ${minutes % 60}m"
    }
}
