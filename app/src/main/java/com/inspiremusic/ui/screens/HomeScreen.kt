@file:OptIn(ExperimentalFoundationApi::class)

package com.inspiremusic.ui.screens

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.inspiremusic.R
import com.inspiremusic.ui.components.appChromeContentPadding
import com.inspiremusic.model.AudioItem
import com.inspiremusic.ui.components.AppCornerTokens
import com.inspiremusic.ui.components.ContinuousRoundedCornerShape
import com.inspiremusic.ui.components.FloatingGlassIconButton
import com.inspiremusic.ui.components.glassClickable
import com.inspiremusic.ui.theme.LocalAppIsDark
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
    val aiCommentary by viewModel.aiCommentary.collectAsState()
    val aiError by viewModel.aiError.collectAsState()
    val aiResponseText by viewModel.aiResponseText.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isDark = LocalAppIsDark.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val playlistSavedMessage = stringResource(R.string.home_ai_playlist_saved)

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..3 -> stringResource(R.string.home_greeting_midnight)
        in 4..6 -> stringResource(R.string.home_greeting_dawn)
        in 7..10 -> stringResource(R.string.home_greeting_morning)
        in 11..13 -> stringResource(R.string.home_greeting_noon)
        in 14..16 -> stringResource(R.string.home_greeting_afternoon)
        in 17..18 -> stringResource(R.string.home_greeting_dusk)
        in 19..22 -> stringResource(R.string.home_greeting_evening)
        else -> stringResource(R.string.home_greeting_midnight)
    }

    var prompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun submitPrompt(text: String = prompt) {
        val clean = text.trim()
        if (clean.isBlank()) return
        viewModel.generateAiPlaylist(clean)
        prompt = clean
        keyboardController?.hide()
        scope.launch { listState.animateScrollToItem(0) }
    }

    val suggestionPool = listOf(
        InspirePrompt(R.string.tag_relax, R.string.inspire_subtitle_relax, R.string.inspire_prompt_relax, Icons.Outlined.SelfImprovement, Color(0xFF30D158)),
        InspirePrompt(R.string.tag_workout, R.string.inspire_subtitle_workout, R.string.inspire_prompt_workout, Icons.Outlined.FitnessCenter, Color(0xFFFF9F0A)),
        InspirePrompt(R.string.tag_sad, R.string.inspire_subtitle_sad, R.string.inspire_prompt_sad, Icons.Outlined.Nightlight, Color(0xFF0A84FF)),
        InspirePrompt(R.string.tag_party, R.string.inspire_subtitle_party, R.string.inspire_prompt_party, Icons.Outlined.Celebration, Color(0xFFFF375F)),
        InspirePrompt(R.string.tag_sleep, R.string.inspire_subtitle_sleep, R.string.inspire_prompt_sleep, Icons.Outlined.Bedtime, Color(0xFF64D2FF)),
        InspirePrompt(R.string.tag_surprise, R.string.inspire_subtitle_surprise, R.string.inspire_prompt_surprise, Icons.Outlined.Casino, Color(0xFFBF5AF2)),
        InspirePrompt(R.string.tag_focus, R.string.inspire_subtitle_focus, R.string.inspire_prompt_focus, Icons.Outlined.CenterFocusStrong, Color(0xFF5E5CE6)),
        InspirePrompt(R.string.tag_commute, R.string.inspire_subtitle_commute, R.string.inspire_prompt_commute, Icons.Outlined.Commute, Color(0xFFFF9F0A)),
        InspirePrompt(R.string.tag_rain, R.string.inspire_subtitle_rain, R.string.inspire_prompt_rain, Icons.Outlined.WaterDrop, Color(0xFF5AC8FA)),
        InspirePrompt(R.string.tag_morning, R.string.inspire_subtitle_morning, R.string.inspire_prompt_morning, Icons.Outlined.WbSunny, Color(0xFFFFCC00)),
        InspirePrompt(R.string.tag_night_drive, R.string.inspire_subtitle_night_drive, R.string.inspire_prompt_night_drive, Icons.Outlined.DirectionsCar, Color(0xFF5856D6)),
        InspirePrompt(R.string.tag_cafe, R.string.inspire_subtitle_cafe, R.string.inspire_prompt_cafe, Icons.Outlined.LocalCafe, Color(0xFFA2845E)),
        InspirePrompt(R.string.tag_nostalgia, R.string.inspire_subtitle_nostalgia, R.string.inspire_prompt_nostalgia, Icons.Outlined.History, Color(0xFFFF2D55)),
        InspirePrompt(R.string.tag_weekend, R.string.inspire_subtitle_weekend, R.string.inspire_prompt_weekend, Icons.Outlined.Weekend, Color(0xFF34C759)),
        InspirePrompt(R.string.tag_date_night, R.string.inspire_subtitle_date_night, R.string.inspire_prompt_date_night, Icons.Outlined.FavoriteBorder, Color(0xFFFF375F)),
        InspirePrompt(R.string.tag_reading, R.string.inspire_subtitle_reading, R.string.inspire_prompt_reading, Icons.Outlined.MenuBook, Color(0xFFAF52DE)),
        InspirePrompt(R.string.tag_road_trip, R.string.inspire_subtitle_road_trip, R.string.inspire_prompt_road_trip, Icons.Outlined.Map, Color(0xFF32ADE6)),
        InspirePrompt(R.string.tag_hidden_gems, R.string.inspire_subtitle_hidden_gems, R.string.inspire_prompt_hidden_gems, Icons.Outlined.TravelExplore, Color(0xFF00C7BE))
    )
    var suggestions by remember { mutableStateOf(suggestionPool.shuffled().take(8)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                suggestions = suggestionPool.shuffled().take(8)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val promptHints = listOf(
        stringResource(R.string.home_prompt_hint_1),
        stringResource(R.string.home_prompt_hint_2),
        stringResource(R.string.home_prompt_hint_3),
        stringResource(R.string.home_prompt_hint_4),
        stringResource(R.string.home_prompt_hint_5),
        stringResource(R.string.home_prompt_hint_6),
        stringResource(R.string.home_prompt_hint_7),
        stringResource(R.string.home_prompt_hint_8)
    )

    LazyColumn(
        state = listState,
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
        contentPadding = appChromeContentPadding()
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
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
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 20.dp),
                resultVisible = aiIsLoading || aiError != null || aiResponseText.isNotBlank() || aiGeneratedSongs.isNotEmpty()
            ) {
                when {
                    aiIsLoading -> Text(stringResource(R.string.home_ai_loading), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onBackground)
                    aiError != null -> {
                        Text(aiError.orEmpty(), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                        androidx.compose.material3.TextButton(onClick = { viewModel.clearAiPlaylist() }) { Text(stringResource(R.string.home_ai_clear)) }
                    }
                    aiGeneratedSongs.isNotEmpty() -> {
                        InspireAiResultCard(
                            count = aiGeneratedSongs.size,
                            commentary = aiCommentary,
                            tags = aiTags,
                            emotions = aiEmotions,
                            onPlay = { viewModel.playAiPlaylist() },
                            onSave = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.saveAiPlaylist()
                                Toast.makeText(context, playlistSavedMessage, Toast.LENGTH_SHORT).show()
                            },
                            onClear = { viewModel.clearAiPlaylist() }
                        )
                        aiGeneratedSongs.forEachIndexed { index, song ->
                            InspireSongRow(song = song, isPlaying = currentSong?.id == song.id,
                                onClick = { viewModel.playList(aiGeneratedSongs, index) })
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    aiResponseText.isNotBlank() -> {
                        Text(aiResponseText, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onBackground)
                        androidx.compose.material3.TextButton(onClick = { viewModel.clearAiPlaylist() }) { Text(stringResource(R.string.home_ai_clear)) }
                    }
                }
            }
            InspirePromptHints(
                hints = promptHints,
                onHintClick = { prompt = it },
                modifier = Modifier.padding(bottom = 22.dp)
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.inspire_quick_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.inspire_quick_subtitle),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
                InspireWaterfallGrid(
                    columns = 2,
                    horizontalSpacing = 12.dp,
                    verticalSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    suggestions.forEachIndexed { index, suggestion ->
                        InspireWaterfallCard(
                            prompt = suggestion,
                            index = index,
                            onClick = { submitPrompt(it) }
                        )
                    }
                }
            }
        }


    }
}

@Composable
private fun HomeGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AppCornerTokens.Card,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalAppIsDark.current
    val shape = ContinuousRoundedCornerShape(cornerRadius)
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
    modifier: Modifier = Modifier,
    resultVisible: Boolean = false,
    resultContent: @Composable () -> Unit = {}
) {
    val isDark = LocalAppIsDark.current
    HomeGlassPanel(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = AppCornerTokens.Card
    ) {
      Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )

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
                    .heightIn(min = 48.dp),
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
                        icon = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.home_ai_clear),
                        onClick = onClear,
                        width = 42.dp,
                        height = 42.dp,
                        cornerRadius = AppCornerTokens.Control,
                        tint = if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.70f),
                        containerColor = Color.White.copy(alpha = if (isDark) 0.050f else 0.22f)
                    )
                    Spacer(Modifier.width(6.dp))
                    FloatingGlassIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = stringResource(R.string.action_confirm),
                        onClick = onSubmit,
                        width = 46.dp,
                        height = 42.dp,
                        cornerRadius = AppCornerTokens.Control,
                        tint = if (isDark) Color.White else Color.Black,
                        containerColor = Color.White.copy(alpha = if (isDark) 0.075f else 0.28f)
                    )
                }
            }
        }
        AnimatedVisibility(visible = resultVisible) {
            Column {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                    thickness = 0.5.dp
                )
                resultContent()
            }
        }
      }
    }
}

@Composable
private fun InspireWaterfallGrid(
    columns: Int = 2,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val colWidth = ((constraints.maxWidth - hGap * (columns - 1)) / columns).coerceAtLeast(0)
        val childConstraints = constraints.copy(
            minWidth = colWidth,
            maxWidth = colWidth,
            minHeight = 0
        )
        val colHeights = IntArray(columns)
        val placements = measurables.map { measurable ->
            val placeable = measurable.measure(childConstraints)
            val col = colHeights.indices.minByOrNull { colHeights[it] } ?: 0
            val x = col * (colWidth + hGap)
            val y = colHeights[col]
            colHeights[col] += placeable.height + vGap
            Triple(placeable, x, y)
        }
        val totalHeight = ((colHeights.maxOrNull() ?: 0) - if (measurables.isNotEmpty()) vGap else 0)
            .coerceAtLeast(0)
        layout(constraints.maxWidth, totalHeight) {
            placements.forEach { (placeable, x, y) ->
                placeable.placeRelative(x, y)
            }
        }
    }
}

@Composable
private fun InspireWaterfallCard(
    prompt: InspirePrompt,
    index: Int,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(prompt.labelResId)
    val subtitle = stringResource(prompt.subtitleResId)
    val fullPrompt = stringResource(prompt.promptResId)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "waterfallCardScale"
    )
    val showHeroWave = index == 0 || index == 3 || index == 6
    val showActionFooter = index == 0 || index == 2 || index == 5 || index == 7

    HomeGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .scale(scale)
            .glassClickable(interactionSource = interactionSource) { onClick(fullPrompt) },
        cornerRadius = AppCornerTokens.Card
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(prompt.accent.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(360f, 40f),
                        radius = 460f
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (index == 0) {
                Text(
                    text = stringResource(R.string.inspire_for_you),
                    color = prompt.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = prompt.icon,
                    contentDescription = null,
                    tint = prompt.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = if (index == 0) 20.sp else 17.sp,
                    lineHeight = if (index == 0) 24.sp else 21.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .clip(ContinuousRoundedCornerShape(AppCornerTokens.Small))
                    .background(prompt.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = subtitle,
                    color = prompt.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (showHeroWave) {
                Spacer(Modifier.height(10.dp))
                AmbientSignalArtwork(
                    accent = prompt.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (index == 0) 68.dp else 48.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = fullPrompt,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            if (showActionFooter) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.inspire_generate_now),
                        color = prompt.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = prompt.accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AmbientSignalArtwork(
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val center = Offset(size.width * 0.62f, size.height * 0.48f)
        val unit = size.minDimension
        drawCircle(accent.copy(alpha = 0.08f), radius = unit * 0.42f, center = center)
        drawCircle(accent.copy(alpha = 0.13f), radius = unit * 0.29f, center = center)
        drawCircle(accent.copy(alpha = 0.80f), radius = unit * 0.045f, center = center)
        repeat(3) { index ->
            drawArc(
                color = accent.copy(alpha = 0.46f - index * 0.09f),
                startAngle = -48f,
                sweepAngle = 248f,
                useCenter = false,
                topLeft = Offset(center.x - unit * (0.17f + index * 0.08f), center.y - unit * (0.17f + index * 0.08f)),
                size = androidx.compose.ui.geometry.Size(unit * (0.34f + index * 0.16f), unit * (0.34f + index * 0.16f)),
                style = Stroke(width = (2.3f - index * 0.35f).dp.toPx())
            )
        }
        drawLine(
            color = accent.copy(0.34f),
            start = Offset(size.width * 0.18f, size.height * 0.76f),
            end = Offset(size.width * 0.86f, size.height * 0.19f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/** Suggested prompts shown under the input box. Tapping one fills the box so it can be edited before sending. */
@Composable
private fun InspirePromptHints(
    hints: List<String>,
    onHintClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppIsDark.current
    val chipShape = ContinuousRoundedCornerShape(AppCornerTokens.Small)
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(hints) { hint ->
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(chipShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(if (isDark) 0.07f else 0.05f))
                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(0.08f), chipShape)
                    .clickable { onHintClick(hint) }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(0.85f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = hint,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.75f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
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
        cornerRadius = AppCornerTokens.Card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = accent,
                    strokeWidth = 2.2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
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
private fun InspireAiResultCard(
    count: Int,
    commentary: String?,
    tags: List<String>,
    emotions: List<String>,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    val isDark = LocalAppIsDark.current
    val controlShape = ContinuousRoundedCornerShape(AppCornerTokens.Control)
    val smallShape = ContinuousRoundedCornerShape(AppCornerTokens.Small)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 顶部标题与操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "灵感精选歌单",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "匹配到 $count 首本地歌曲",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                        fontSize = 12.sp
                    )
                }
                // 清空按钮
                Box(
                    modifier = Modifier
                        .clip(smallShape)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.08f else 0.05f))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "清空",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // AI 温暖导语文字（如有返回）
            if (!commentary.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(controlShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.10f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.06f else 0.04f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.25f else 0.18f),
                            controlShape
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "“ $commentary ”",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.90f),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 情绪与场景标签
            val allChips = (tags.take(4) + emotions.take(3)).distinct()
            if (allChips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(allChips) { chip ->
                        Box(
                            modifier = Modifier
                                .clip(smallShape)
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.08f else 0.05f))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "#$chip",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 底部操作栏：播放全部与保存歌单
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 播放全部
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(controlShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "播放全部",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // 保存歌单
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(controlShape)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.10f else 0.06f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.16f else 0.10f),
                            controlShape
                        )
                        .clickable(onClick = onSave),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.BookmarkAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "保存歌单",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
}

@Composable
private fun InspireSongRow(
    song: AudioItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalAppIsDark.current
    val shape = ContinuousRoundedCornerShape(AppCornerTokens.Control)
    val thumbShape = ContinuousRoundedCornerShape(AppCornerTokens.Small)
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
                .clip(thumbShape)
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
                Icons.Outlined.VolumeUp,
                stringResource(R.string.now_playing_indicator),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
