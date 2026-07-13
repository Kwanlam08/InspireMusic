package com.applemusic.clone.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.applemusic.clone.R
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.model.DiaryAiLog
import com.applemusic.clone.model.ListeningRecord
import com.applemusic.clone.ui.components.BackdropLiquidGlass
import com.applemusic.clone.ui.components.FloatingGlassIconButton
import com.applemusic.clone.ui.components.LocalAppChromeController
import com.applemusic.clone.ui.components.LiquidGlassSegmentedControl
import com.applemusic.clone.viewmodel.MusicViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class DiaryMode {
    Day,
    Week,
    Month
}

private val DiaryMode.modeKey: String
    get() = when (this) {
        DiaryMode.Day -> "day"
        DiaryMode.Week -> "week"
        DiaryMode.Month -> "month"
    }

private val DiaryMode.defaultLabel: String
    get() = when (this) {
        DiaryMode.Day -> "日记"
        DiaryMode.Week -> "周记"
        DiaryMode.Month -> "月记"
    }

private fun String.toDiaryMode(): DiaryMode = when (this) {
    "week" -> DiaryMode.Week
    "month" -> DiaryMode.Month
    else -> DiaryMode.Day
}

private val DiaryMode.cleanLabel: String
    get() = when (this) {
        DiaryMode.Day -> "\u65e5\u8bb0"
        DiaryMode.Week -> "\u5468\u8bb0"
        DiaryMode.Month -> "\u6708\u8bb0"
    }

private data class DiarySummary(
    val key: String,
    val label: String,
    val records: List<ListeningRecord>,
    val artworkBySongId: Map<Long, Any?> = emptyMap()
) {
    val playCount: Int = records.size
    val uniqueSongCount: Int = records.map { it.songId }.distinct().size
    val totalDuration: Long = records.sumOf { it.duration.coerceAtLeast(0L) }
    val topSong: ListeningRecord? = records
        .groupBy { it.songId }
        .maxByOrNull { it.value.sumOf { record -> record.duration.coerceAtLeast(0L) } }
        ?.value
        ?.firstOrNull()
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
    onBack: () -> Unit
) {
    val records by viewModel.listeningRecords.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val diaryAiIsLoading by viewModel.diaryAiIsLoading.collectAsState()
    val diaryAiResult by viewModel.diaryAiResult.collectAsState()
    val diaryAiError by viewModel.diaryAiError.collectAsState()
    val diaryAiLogs by viewModel.diaryAiLogs.collectAsState()
    val chromeController = LocalAppChromeController.current
    var mode by remember { mutableStateOf(DiaryMode.Day) }
    var aiAnalysisTarget by remember { mutableStateOf<Pair<DiaryMode, DiarySummary>?>(null) }
    var showLogPage by remember { mutableStateOf(false) }
    var logMode by remember { mutableStateOf(DiaryMode.Day) }
    var selectedLogId by remember { mutableStateOf<String?>(null) }
    val daySummaries = remember(records, songs) { buildDiarySummaries(records, songs, DiaryMode.Day) }
    val weekSummaries = remember(records, songs) { buildDiarySummaries(records, songs, DiaryMode.Week) }
    val monthSummaries = remember(records, songs) { buildDiarySummaries(records, songs, DiaryMode.Month) }
    val summariesByMode = remember(daySummaries, weekSummaries, monthSummaries) {
        mapOf(
            DiaryMode.Day.modeKey to daySummaries,
            DiaryMode.Week.modeKey to weekSummaries,
            DiaryMode.Month.modeKey to monthSummaries
        )
    }

    LaunchedEffect(showLogPage) {
        chromeController.setVisible(!showLogPage)
    }
    DisposableEffect(Unit) {
        onDispose { chromeController.setVisible(true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 170.dp)
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
                            logMode = mode
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
                    DiarySegmentedControl(mode = mode, onModeChange = { mode = it })
                }
            }

            item {
                AnimatedContent(
                    targetState = mode,
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
                    },
                    label = "diaryModeContent"
                ) { targetMode ->
                    val targetSummaries = when (targetMode) {
                        DiaryMode.Day -> daySummaries
                        DiaryMode.Week -> weekSummaries
                        DiaryMode.Month -> monthSummaries
                    }
                    if (targetSummaries.isEmpty()) {
                        DiaryEmptyState()
                    } else {
                        Column {
                            targetSummaries.forEach { summary ->
                                DiarySummaryCard(
                                    summary = summary,
                                    onAnalyze = {
                                        viewModel.clearDiaryAiAnalysis()
                                        aiAnalysisTarget = targetMode to summary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        aiAnalysisTarget?.let { target ->
            DiaryAiAnalysisSheet(
                selectedMode = target.first,
                summary = target.second,
                isLoading = diaryAiIsLoading,
                result = diaryAiResult,
                error = diaryAiError,
                onDismiss = { aiAnalysisTarget = null },
                onAnalyze = { selectedMode, selectedSummary ->
                    val prompt = buildDiaryAnalysisPrompt(selectedMode, listOf(selectedSummary))
                    viewModel.analyzeDiaryWithAi(
                        prompt = prompt,
                        modeKey = selectedMode.modeKey,
                        modeLabel = selectedMode.cleanLabel,
                        summaryKey = selectedSummary.key,
                        summaryLabel = selectedSummary.label,
                        summaryText = buildDiarySummaryText(selectedSummary)
                    )
                }
            )
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
        blurRadius = 12.dp,
        surfaceAlpha = 0.020f,
        highlightAlpha = 0.38f,
        shadowAlpha = 0.12f,
        useSharedBackdrop = true
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
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
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        cornerRadius = 15.dp,
        blurRadius = 12.dp,
        surfaceAlpha = 0.030f,
        highlightAlpha = 0.34f,
        shadowAlpha = 0.12f,
        useSharedBackdrop = true
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.EditNote,
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
            contentPadding = PaddingValues(bottom = 120.dp)
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
            contentPadding = PaddingValues(bottom = 48.dp)
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
        FloatingGlassIconButton(
            icon = Icons.Default.ArrowBackIosNew,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
            width = 48.dp,
            height = 48.dp,
            cornerRadius = 18.dp,
            tint = MaterialTheme.colorScheme.onBackground,
            refractive = true,
            useSharedBackdrop = true
        )
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
    val shape = RoundedCornerShape(28.dp)
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
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
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
    val shape = RoundedCornerShape(30.dp)
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
            shape = RoundedCornerShape(22.dp),
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
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(19.dp))
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
    val shape = RoundedCornerShape(30.dp)
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
                    imageVector = if (editing) Icons.Default.Check else Icons.Default.Edit,
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
                        imageVector = Icons.Default.Close,
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
                shape = RoundedCornerShape(22.dp),
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
private fun DiaryLogEmptyState() {
    BackdropLiquidGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(170.dp),
        cornerRadius = 30.dp,
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
                imageVector = Icons.Default.History,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryAiAnalysisSheet(
    selectedMode: DiaryMode,
    summary: DiarySummary,
    isLoading: Boolean,
    result: String,
    error: String?,
    onDismiss: () -> Unit,
    onAnalyze: (DiaryMode, DiarySummary) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.34f),
        dragHandle = null
    ) {
        val isDark = isSystemInDarkTheme()
        val sheetShape = RoundedCornerShape(32.dp)
        val sheetColor = if (isDark) Color(0xFF101014) else Color(0xFFF7F7F9)
        val edgeBrush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (isDark) 0.16f else 0.72f),
                if (isDark) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.085f),
                if (isDark) Color.Black.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.12f)
            )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .shadow(
                    elevation = 22.dp,
                    shape = sheetShape,
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.48f else 0.22f),
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.22f else 0.08f)
                )
                .clip(sheetShape)
                .background(sheetColor, sheetShape)
                .border(1.dp, edgeBrush, sheetShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.diary_ai_title),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                        Text(
                            text = stringResource(R.string.diary_ai_subtitle),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                DiaryAiSelectedSummary(summary)
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (result.isNotBlank() || error != null) 390.dp else 118.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    if (result.isNotBlank() || error != null) {
                        item {
                            DiaryAiResultCard(result = result, error = error)
                        }
                    } else {
                        item { DiaryAiWaitingState() }
                    }
                }

                Button(
                    onClick = { onAnalyze(selectedMode, summary) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.diary_ai_analyzing), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.diary_ai_start_single),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryAiSelectableSummary(
    summary: DiarySummary,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        },
        animationSpec = tween(180),
        label = "diaryAiSelectionBorder"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.10f else 0.030f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.020f)
                    )
                )
            )
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DiaryAiSelectedSummary(summary: DiarySummary) {
    val shape = RoundedCornerShape(24.dp)
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
            imageVector = Icons.Default.CalendarMonth,
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
    val shape = RoundedCornerShape(26.dp)
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
            imageVector = Icons.Default.AutoAwesome,
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
            imageVector = Icons.Default.History,
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
    onModeChange: (DiaryMode) -> Unit
) {
    LiquidGlassSegmentedControl(
        items = listOf(
            DiaryMode.Day to stringResource(R.string.diary_day),
            DiaryMode.Week to stringResource(R.string.diary_week),
            DiaryMode.Month to stringResource(R.string.diary_month)
        ),
        selected = mode,
        onSelected = onModeChange
    )
}

@Composable
private fun DiarySummaryCard(
    summary: DiarySummary,
    onAnalyze: () -> Unit
) {
    var expanded by remember(summary.key) { mutableStateOf(false) }
    val displaySongs = remember(summary) {
        summary.records
            .groupBy { it.songId }
            .mapNotNull { (songId, records) ->
                val first = records.maxByOrNull { it.playedAt } ?: return@mapNotNull null
                DiaryDisplaySong(
                    record = first.copy(duration = records.sumOf { it.duration.coerceAtLeast(0L) }),
                    totalDuration = records.sumOf { it.duration.coerceAtLeast(0L) },
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
        cornerRadius = 28.dp,
        blurRadius = 10.dp,
        surfaceAlpha = 0.012f,
        highlightAlpha = 0.34f,
        shadowAlpha = 0.10f,
        useSharedBackdrop = true
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
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
                    onClick = onAnalyze,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DiaryMetric(
                    label = stringResource(R.string.diary_top_song),
                    value = summary.topSong?.title ?: "-",
                    modifier = Modifier.weight(1f)
                )
                DiaryMetric(
                    label = stringResource(R.string.diary_top_artist),
                    value = summary.topArtist.ifBlank { "-" },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(14.dp))
            visibleSongs.forEach { item ->
                DiarySongRow(
                    record = item.record,
                    artwork = item.artwork,
                    listenDuration = item.totalDuration,
                    playCount = item.playCount
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
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
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
    playCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
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
                    imageVector = Icons.Default.MusicNote,
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
private fun DiaryEmptyState() {
    BackdropLiquidGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(150.dp),
        cornerRadius = 28.dp,
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
                imageVector = Icons.Default.History,
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
