@file:OptIn(ExperimentalFoundationApi::class)

package com.applemusic.clone.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.R
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.viewmodel.MusicViewModel
import java.util.Calendar

// 品牌渐变色（Apple Intelligence 配色）
private val aiGradientColors = listOf(
    Color(0xFF7C4DFF),
    Color(0xFFBF5AF2),
    Color(0xFFFF6B9D),
    Color(0xFFFF9500),
    Color(0xFF5E5CE6),
    Color(0xFF007AFF),
    Color(0xFF7C4DFF)
)

// 动画旋转的彩虹边框
@Composable
private fun AnimatedRainbowBorder(content: @Composable () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "rainbow")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "border_angle"
    )
    val isDark = isSystemInDarkTheme()
    val borderAlpha = if (isDark) 1f else 0.75f
    val gradientColors = aiGradientColors.map { it.copy(alpha = borderAlpha) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 外层发光层
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.sweepGradient(gradientColors))
                .graphicsLayer { rotationZ = angle }
        )
        // 内容卡片
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF8F8FF),
            shadowElevation = 0.dp
        ) {
            content()
        }
    }
}

// 高级 AI 图标：多层渐变圆形 + 旋转星星
@Composable
private fun AiOrbIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle_rot"
    )

    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        // 外圈发光
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7C4DFF).copy(alpha = 0.35f),
                            Color(0xFFBF5AF2).copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
        // 内圈渐变圆
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF7C4DFF),
                            Color(0xFFBF5AF2),
                            Color(0xFF5E5CE6)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(13.dp)
                    .rotate(sparkleRotation)
            )
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onNavigateTo: (String) -> Unit
) {
    val aiPrompt by viewModel.aiPrompt.collectAsState()
    val aiIsLoading by viewModel.aiIsLoading.collectAsState()
    val aiGeneratedSongs by viewModel.aiGeneratedSongs.collectAsState()
    val aiResponseText by viewModel.aiResponseText.collectAsState()
    val aiError by viewModel.aiError.collectAsState()

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> stringResource(R.string.home_greeting_morning)
        hour < 18 -> stringResource(R.string.home_greeting_afternoon)
        else -> stringResource(R.string.home_greeting_evening)
    }

    var inputText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val showResult = aiGeneratedSongs.isNotEmpty() || aiError != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onBackground
        )

        // ── 动态彩虹边框 AI 输入框 ──
        AnimatedRainbowBorder {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AiOrbIcon()
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.home_ai_placeholder),
                            color = MaterialTheme.colorScheme.onBackground.copy(0.35f),
                            fontSize = 15.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.generateAiPlaylist(inputText.trim())
                                inputText = ""
                                keyboardController?.hide()
                            }
                        }
                    )
                )
                if (inputText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            viewModel.generateAiPlaylist(inputText.trim())
                            inputText = ""
                            keyboardController?.hide()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF7C4DFF), Color(0xFF5E5CE6))
                                )
                            )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            stringResource(R.string.action_confirm),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.home_ai_try),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground.copy(0.7f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(10.dp))

        // ── 高级快速标签（两行3列）──
        data class QuickTag(val icon: ImageVector, val accent: Color, val bgLight: Color, val labelResId: Int)
        val quickTags = listOf(
            QuickTag(Icons.Default.SelfImprovement, Color(0xFF30D158), Color(0xFFE8FFF0), R.string.tag_relax),
            QuickTag(Icons.Default.FitnessCenter,   Color(0xFFFF9500), Color(0xFFFFF4E0), R.string.tag_workout),
            QuickTag(Icons.Default.WaterDrop,       Color(0xFF007AFF), Color(0xFFE0F0FF), R.string.tag_sad),
            QuickTag(Icons.Default.Celebration,     Color(0xFFFF6B9D), Color(0xFFFFE8F2), R.string.tag_party),
            QuickTag(Icons.Default.Bedtime,         Color(0xFF5E5CE6), Color(0xFFEEEEFF), R.string.tag_sleep),
            QuickTag(Icons.Default.AutoAwesome,     Color(0xFF7C4DFF), Color(0xFFF2EBFF), R.string.tag_surprise)
        )

        val isDark = isSystemInDarkTheme()

        @Composable
        fun QuickTagChip(tag: QuickTag) {
            val label = stringResource(tag.labelResId)
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val chipScale by animateFloatAsState(
                targetValue = if (isPressed) 0.93f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "chip_scale"
            )
            val bgColor = if (isDark) tag.accent.copy(alpha = 0.18f) else tag.bgLight

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .scale(chipScale)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        inputText = label
                    },
                shape = RoundedCornerShape(14.dp),
                color = bgColor,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    tag.accent.copy(alpha = if (isDark) 0.35f else 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        tag.accent.copy(alpha = if (isDark) 0.4f else 0.2f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            tag.icon,
                            contentDescription = null,
                            tint = tag.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tag.accent.copy(alpha = if (isDark) 0.9f else 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        @Composable
        fun QuickTagRow(tags: List<QuickTag>) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag -> QuickTagChip(tag) }
            }
        }

        QuickTagRow(quickTags.take(3))
        Spacer(Modifier.height(8.dp))
        QuickTagRow(quickTags.drop(3))

        Spacer(Modifier.height(24.dp))

        if (aiIsLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val infiniteTransition = rememberInfiniteTransition(label = "loading")
                    val loadScale by infiniteTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "load_scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .scale(loadScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF7C4DFF),
                                        Color(0xFFBF5AF2),
                                        Color(0xFF5E5CE6)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.home_ai_loading),
                        color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        } else if (aiError != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF375F), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(aiError ?: "", color = Color(0xFFFF375F), fontSize = 14.sp)
                }
            }
        } else if (showResult) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.home_ai_result_title, aiGeneratedSongs.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row {
                        IconButton(onClick = { viewModel.playAiPlaylist() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.home_ai_play), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { viewModel.saveAiPlaylist() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.BookmarkAdd, stringResource(R.string.home_ai_save), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
                    items(aiGeneratedSongs) { song ->
                        val isPlaying = viewModel.currentSong.collectAsState().value?.id == song.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playList(aiGeneratedSongs, aiGeneratedSongs.indexOf(song)) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                coil.compose.AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp
                                )
                                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (isPlaying) Icon(Icons.Default.VolumeUp, stringResource(R.string.now_playing_indicator), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 高级空态：渐变圆 + 星星图标
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF7C4DFF).copy(alpha = 0.15f),
                                        Color(0xFFBF5AF2).copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = Color(0xFF7C4DFF).copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.home_ai_empty),
                        color = MaterialTheme.colorScheme.onBackground.copy(0.35f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
