@file:OptIn(ExperimentalFoundationApi::class)

package com.inspiremusic.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inspiremusic.R
import com.inspiremusic.model.AudioItem
import com.inspiremusic.ui.components.FloatingGlassIconButton
import com.inspiremusic.ui.components.glassClickable
import com.inspiremusic.viewmodel.MusicViewModel
import java.util.Calendar

private data class InspirePrompt(
    val labelResId: Int,
    val subtitleResId: Int,
    val promptResId: Int,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onNavigateTo: (String) -> Unit
) {
    val aiIsLoading by viewModel.aiIsLoading.collectAsState()
    val aiGeneratedSongs by viewModel.aiGeneratedSongs.collectAsState()
    val aiTags by viewModel.aiTags.collectAsState()
    val aiEmotions by viewModel.aiEmotions.collectAsState()
    val aiError by viewModel.aiError.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isDark = isSystemInDarkTheme()
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> stringResource(R.string.home_greeting_morning)
        in 12..17 -> stringResource(R.string.home_greeting_afternoon)
        else -> stringResource(R.string.home_greeting_evening)
    }

    var prompt by remember { mutableStateOf("") }

    fun submitPrompt(text: String = prompt) {
        val clean = text.trim()
        if (clean.isBlank()) return
        viewModel.generateAiPlaylist(clean)
        prompt = ""
        keyboardController?.hide()
    }

    val suggestionPool = listOf(
        InspirePrompt(R.string.tag_relax, R.string.inspire_subtitle_relax, R.string.inspire_prompt_relax, Icons.Default.SelfImprovement, Color(0xFF30D158)),
        InspirePrompt(R.string.tag_workout, R.string.inspire_subtitle_workout, R.string.inspire_prompt_workout, Icons.Default.FitnessCenter, Color(0xFFFF9F0A)),
        InspirePrompt(R.string.tag_sad, R.string.inspire_subtitle_sad, R.string.inspire_prompt_sad, Icons.Default.Nightlight, Color(0xFF0A84FF)),
        InspirePrompt(R.string.tag_party, R.string.inspire_subtitle_party, R.string.inspire_prompt_party, Icons.Default.Celebration, Color(0xFFFF375F)),
        InspirePrompt(R.string.tag_sleep, R.string.inspire_subtitle_sleep, R.string.inspire_prompt_sleep, Icons.Default.Bedtime, Color(0xFF64D2FF)),
        InspirePrompt(R.string.tag_surprise, R.string.inspire_subtitle_surprise, R.string.inspire_prompt_surprise, Icons.Default.Casino, Color(0xFFBF5AF2)),
        InspirePrompt(R.string.tag_focus, R.string.inspire_subtitle_focus, R.string.inspire_prompt_focus, Icons.Default.CenterFocusStrong, Color(0xFF5E5CE6)),
        InspirePrompt(R.string.tag_commute, R.string.inspire_subtitle_commute, R.string.inspire_prompt_commute, Icons.Default.Commute, Color(0xFFFF9F0A)),
        InspirePrompt(R.string.tag_rain, R.string.inspire_subtitle_rain, R.string.inspire_prompt_rain, Icons.Default.WaterDrop, Color(0xFF5AC8FA)),
        InspirePrompt(R.string.tag_morning, R.string.inspire_subtitle_morning, R.string.inspire_prompt_morning, Icons.Default.WbSunny, Color(0xFFFFCC00)),
        InspirePrompt(R.string.tag_night_drive, R.string.inspire_subtitle_night_drive, R.string.inspire_prompt_night_drive, Icons.Default.DirectionsCar, Color(0xFF5856D6)),
        InspirePrompt(R.string.tag_cafe, R.string.inspire_subtitle_cafe, R.string.inspire_prompt_cafe, Icons.Default.LocalCafe, Color(0xFFA2845E)),
        InspirePrompt(R.string.tag_nostalgia, R.string.inspire_subtitle_nostalgia, R.string.inspire_prompt_nostalgia, Icons.Default.History, Color(0xFFFF2D55)),
        InspirePrompt(R.string.tag_weekend, R.string.inspire_subtitle_weekend, R.string.inspire_prompt_weekend, Icons.Default.Weekend, Color(0xFF34C759)),
        InspirePrompt(R.string.tag_date_night, R.string.inspire_subtitle_date_night, R.string.inspire_prompt_date_night, Icons.Default.Favorite, Color(0xFFFF375F)),
        InspirePrompt(R.string.tag_reading, R.string.inspire_subtitle_reading, R.string.inspire_prompt_reading, Icons.Default.MenuBook, Color(0xFFAF52DE)),
        InspirePrompt(R.string.tag_road_trip, R.string.inspire_subtitle_road_trip, R.string.inspire_prompt_road_trip, Icons.Default.Map, Color(0xFF32ADE6)),
        InspirePrompt(R.string.tag_hidden_gems, R.string.inspire_subtitle_hidden_gems, R.string.inspire_prompt_hidden_gems, Icons.Default.TravelExplore, Color(0xFF00C7BE))
    )
    val suggestions = remember { suggestionPool.shuffled().take(6) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        if (isDark) Color(0xFF111116) else Color(0xFFF7F7FA),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentPadding = PaddingValues(bottom = 168.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) .18f else .11f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = greeting,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.home_ai_empty),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
        }

        item {
            InspirePromptBox(
                value = prompt,
                onValueChange = { prompt = it.take(180) },
                onSubmit = { submitPrompt() },
                onClear = { prompt = "" },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
            )
        }

        item {
            Text(
                text = stringResource(R.string.inspire_quick_title),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
            Text(
                text = stringResource(R.string.inspire_quick_subtitle),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp)
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                suggestions.chunked(2).forEach { rowSuggestions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowSuggestions.forEach { suggestion ->
                            InspireSuggestionCard(
                                prompt = suggestion,
                                onClick = { submitPrompt(it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowSuggestions.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            when {
                aiIsLoading -> InspireHeroPanel(
                    title = stringResource(R.string.home_ai_loading),
                    subtitle = "正在分析你的曲库、情绪和节奏偏好",
                    accent = Color(0xFFBF5AF2),
                    showProgress = true
                )

                aiError != null -> InspireHeroPanel(
                    title = "AI 请求失败",
                    subtitle = aiError.orEmpty(),
                    accent = MaterialTheme.colorScheme.primary
                )

                aiGeneratedSongs.isEmpty() -> InspireHeroPanel(
                    title = "告诉我你现在想听什么",
                    subtitle = "比如夜晚散步、下雨天、运动前热身，或直接点上面的灵感卡片",
                    accent = MaterialTheme.colorScheme.primary
                )

                else -> InspireResultHeader(
                    count = aiGeneratedSongs.size,
                    tags = aiTags,
                    emotions = aiEmotions,
                    onPlay = { viewModel.playAiPlaylist() },
                    onSave = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.saveAiPlaylist()
                    }
                )
            }
        }

        if (aiGeneratedSongs.isNotEmpty()) {
            items(aiGeneratedSongs, key = { it.id }) { song ->
                InspireSongRow(
                    song = song,
                    isPlaying = currentSong?.id == song.id,
                    onClick = {
                        viewModel.playList(aiGeneratedSongs, aiGeneratedSongs.indexOf(song))
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.090f else 0.34f),
                        Color.White.copy(alpha = if (isDark) 0.030f else 0.13f),
                        Color.Black.copy(alpha = if (isDark) 0.055f else 0.018f)
                    )
                ),
                shape
            )
            .background(
                Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.070f else 0.16f),
                        Color.Transparent
                    ),
                    radius = 260f
                ),
                shape
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.30f else 0.54f),
                        Color.Black.copy(alpha = if (isDark) 0.22f else 0.12f)
                    )
                ),
                shape
            )
    ) {
        content()
    }
}

@Composable
private fun InspirePromptBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    HomeGlassPanel(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 28.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.24f else 0.16f),
                                Color(0xFFBF5AF2).copy(alpha = if (isDark) 0.16f else 0.10f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AI",
                    color = if (isDark) Color.White else Color(0xFF1C1C1E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.width(12.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = false,
                maxLines = 3,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = stringResource(R.string.home_ai_placeholder),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.36f),
                                fontSize = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )

            AnimatedVisibility(visible = value.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    FloatingGlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.home_ai_clear),
                        onClick = onClear,
                        width = 44.dp,
                        height = 44.dp,
                        cornerRadius = 17.dp,
                        tint = if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.70f),
                        containerColor = Color.White.copy(alpha = if (isDark) 0.050f else 0.22f)
                    )
                    Spacer(Modifier.width(6.dp))
                    FloatingGlassIconButton(
                        icon = Icons.Default.Send,
                        contentDescription = stringResource(R.string.action_confirm),
                        onClick = onSubmit,
                        width = 48.dp,
                        height = 44.dp,
                        cornerRadius = 17.dp,
                        tint = if (isDark) Color.White else Color.Black,
                        containerColor = Color.White.copy(alpha = if (isDark) 0.075f else 0.28f)
                    )
                }
            }
        }
    }
}

@Composable
private fun InspireSuggestionCard(
    prompt: InspirePrompt,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(prompt.labelResId)
    val subtitle = stringResource(prompt.subtitleResId)
    val fullPrompt = stringResource(prompt.promptResId)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "inspireSuggestionScale"
    )

    HomeGlassPanel(
        modifier = modifier
            .height(108.dp)
            .scale(scale)
            .glassClickable(interactionSource = interactionSource) { onClick(fullPrompt) },
        cornerRadius = 22.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(prompt.accent.copy(alpha = 0.17f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = prompt.icon,
                        contentDescription = null,
                        tint = prompt.accent,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InspireHeroPanel(
    title: String,
    subtitle: String,
    accent: Color,
    showProgress: Boolean = false
) {
    HomeGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 26.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (showProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(21.dp),
                        color = accent,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun InspireResultHeader(
    count: Int,
    tags: List<String>,
    emotions: List<String>,
    onPlay: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_ai_result_title, count),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = buildString {
                if (tags.isNotEmpty()) append(tags.joinToString(" / "))
                if (tags.isNotEmpty() && emotions.isNotEmpty()) append("  ")
                if (emotions.isNotEmpty()) append(emotions.joinToString(" / "))
            }
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = detail,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatingGlassIconButton(
                icon = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.home_ai_play),
                onClick = onPlay,
                width = 48.dp,
                height = 44.dp,
                cornerRadius = 17.dp,
                tint = MaterialTheme.colorScheme.primary,
                containerColor = Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.060f else 0.26f)
            )
            FloatingGlassIconButton(
                icon = Icons.Default.BookmarkAdd,
                contentDescription = stringResource(R.string.home_ai_save),
                onClick = onSave,
                width = 48.dp,
                height = 44.dp,
                cornerRadius = 17.dp,
                tint = MaterialTheme.colorScheme.primary,
                containerColor = Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.060f else 0.26f)
            )
        }
    }
}

@Composable
private fun InspireSongRow(
    song: AudioItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) if (isPlaying) 0.12f else 0.070f else if (isPlaying) 0.34f else 0.22f),
                        Color.White.copy(alpha = if (isDark) 0.025f else 0.09f),
                        Color.Black.copy(alpha = if (isDark) 0.050f else 0.012f)
                    )
                ),
                shape
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.26f else 0.48f),
                        Color.Black.copy(alpha = if (isDark) 0.18f else 0.10f)
                    )
                ),
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
        }
        if (isPlaying) {
            Icon(
                Icons.Default.VolumeUp,
                stringResource(R.string.now_playing_indicator),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
