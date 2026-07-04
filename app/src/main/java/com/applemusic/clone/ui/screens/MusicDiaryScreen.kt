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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    val artworkBySongId: Map<Long, Any?> = emptyMap(),
    val topGenre: String = "-"
) {
    val playCount: Int = records.size
    val uniqueSongCount: Int = records.map { it.songId }.distinct().size
    val totalDuration: Long = records.sumOf { it.duration.coerceAtLeast(0L) }
    val topSong: ListeningRecord? = records
        .groupBy { it.songId }
        .maxByOrNull { it.value.size }
        ?.value
        ?.firstOrNull()
    val topArtist: String = records
        .groupBy { it.artist.ifBlank { "-" } }
        .maxByOrNull { it.value.size }
        ?.key
        .orEmpty()
}

@Composable
fun MusicDiaryScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val records by viewModel.listeningRecords.collectAsState()
    val songs by viewModel.songs.collectAsState()
    var mode by remember { mutableStateOf(DiaryMode.Day) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 170.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.diary_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp
                    )
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
                val targetSummaries = remember(records, songs, targetMode) {
                    buildDiarySummaries(records, songs, targetMode)
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
            Spacer(Modifier.height(10.dp))
            DiaryMetric(
                label = stringResource(R.string.diary_top_genre),
                value = summary.topGenre.ifBlank { "-" },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            summary.records
                .distinctBy { it.songId }
                .take(3)
                .forEach { record ->
                    DiarySongRow(
                        record = record,
                        artwork = summary.artworkBySongId[record.songId]
                    )
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
    artwork: Any?
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
                text = record.artist,
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
                topGenre = grouped
                    .mapNotNull { record ->
                        record.genre
                            .ifBlank { songLookup[record.songId]?.genre.orEmpty() }
                            .takeIf { it.isNotBlank() }
                    }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?: "-"
            )
        }
        .sortedByDescending { it.key }
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
