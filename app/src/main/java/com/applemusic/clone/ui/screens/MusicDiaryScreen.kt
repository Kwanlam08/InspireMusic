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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.applemusic.clone.R
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.model.ListeningRecord
import com.applemusic.clone.ui.components.BackdropLiquidGlass
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
    var mode by remember { mutableStateOf(DiaryMode.Day) }
    var showAiAnalysis by remember { mutableStateOf(false) }
    val daySummaries = remember(records, songs) { buildDiarySummaries(records, songs, DiaryMode.Day) }
    val weekSummaries = remember(records, songs) { buildDiarySummaries(records, songs, DiaryMode.Week) }
    val monthSummaries = remember(records, songs) { buildDiarySummaries(records, songs, DiaryMode.Month) }

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
                    DiaryAiButton(onClick = { showAiAnalysis = true })
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
                                DiarySummaryCard(summary)
                            }
                        }
                    }
                }
            }
        }

        if (showAiAnalysis) {
            DiaryAiAnalysisSheet(
                daySummaries = daySummaries,
                weekSummaries = weekSummaries,
                monthSummaries = monthSummaries,
                isLoading = diaryAiIsLoading,
                result = diaryAiResult,
                error = diaryAiError,
                onDismiss = { showAiAnalysis = false },
                onAnalyze = { selectedMode, selectedSummaries ->
                    viewModel.analyzeDiaryWithAi(
                        buildDiaryAnalysisPrompt(selectedMode, selectedSummaries)
                    )
                },
                onClearResult = viewModel::clearDiaryAiAnalysis
            )
        }
    }
}

@Composable
private fun DiaryAiButton(onClick: () -> Unit) {
    BackdropLiquidGlass(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        cornerRadius = 20.dp,
        blurRadius = 12.dp,
        surfaceAlpha = 0.020f,
        highlightAlpha = 0.38f,
        shadowAlpha = 0.12f,
        useSharedBackdrop = false
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = stringResource(R.string.diary_ai_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryAiAnalysisSheet(
    daySummaries: List<DiarySummary>,
    weekSummaries: List<DiarySummary>,
    monthSummaries: List<DiarySummary>,
    isLoading: Boolean,
    result: String,
    error: String?,
    onDismiss: () -> Unit,
    onAnalyze: (DiaryMode, List<DiarySummary>) -> Unit,
    onClearResult: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(DiaryMode.Day) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val summaries = when (selectedMode) {
        DiaryMode.Day -> daySummaries
        DiaryMode.Week -> weekSummaries
        DiaryMode.Month -> monthSummaries
    }
    val selectedSummaries = summaries.filter { it.key in selectedKeys }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.34f),
        dragHandle = null
    ) {
        BackdropLiquidGlass(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 12.dp)
                .navigationBarsPadding(),
            cornerRadius = 32.dp,
            blurRadius = 18.dp,
            surfaceAlpha = 0.035f,
            highlightAlpha = 0.42f,
            shadowAlpha = 0.18f,
            useSharedBackdrop = false
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                DiarySegmentedControl(
                    mode = selectedMode,
                    onModeChange = {
                        selectedMode = it
                        selectedKeys = emptySet()
                        onClearResult()
                    }
                )
                Spacer(Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    if (summaries.isEmpty()) {
                        item { DiaryAiEmptySelection() }
                    } else {
                        items(summaries, key = { it.key }) { summary ->
                            DiaryAiSelectableSummary(
                                summary = summary,
                                selected = summary.key in selectedKeys,
                                onClick = {
                                    selectedKeys = if (summary.key in selectedKeys) {
                                        selectedKeys - summary.key
                                    } else {
                                        selectedKeys + summary.key
                                    }
                                    onClearResult()
                                }
                            )
                        }
                    }
                    if (result.isNotBlank() || error != null) {
                        item {
                            DiaryAiResultCard(result = result, error = error)
                        }
                    }
                }

                Button(
                    onClick = { onAnalyze(selectedMode, selectedSummaries) },
                    enabled = selectedSummaries.isNotEmpty() && !isLoading,
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
                            text = stringResource(R.string.diary_ai_start, selectedSummaries.size),
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = error ?: result,
            color = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f)
            },
            fontSize = 14.sp,
            lineHeight = 21.sp
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
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.20f),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.050f),
                        Color.Black.copy(alpha = 0.080f)
                    )
                ),
                shape
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            val itemWidth = maxWidth / 3
            val targetOffset by animateDpAsState(
                targetValue = itemWidth * mode.ordinal,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "diarySegmentSlider"
            )
            Box(
                modifier = Modifier
                    .offset(x = targetOffset)
                    .width(itemWidth)
                    .height(42.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.060f))
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.080f)
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                DiarySegment(
                    label = stringResource(R.string.diary_day),
                    selected = mode == DiaryMode.Day,
                    onClick = { onModeChange(DiaryMode.Day) },
                    modifier = Modifier.weight(1f)
                )
                DiarySegment(
                    label = stringResource(R.string.diary_week),
                    selected = mode == DiaryMode.Week,
                    onClick = { onModeChange(DiaryMode.Week) },
                    modifier = Modifier.weight(1f)
                )
                DiarySegment(
                    label = stringResource(R.string.diary_month),
                    selected = mode == DiaryMode.Month,
                    onClick = { onModeChange(DiaryMode.Month) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DiarySegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(15.dp)
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f)
        },
        animationSpec = tween(180),
        label = "diarySegmentText"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun DiarySummaryCard(summary: DiarySummary) {
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
        useSharedBackdrop = false
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
