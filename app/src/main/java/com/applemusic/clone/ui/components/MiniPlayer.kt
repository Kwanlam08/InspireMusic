package com.applemusic.clone.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.viewmodel.MusicViewModel

/**
 * Liquid Glass MiniPlayer
 *
 * - 液态玻璃卡片背景
 * - 平滑动画进度条（animateFloatAsState）
 * - 歌曲切换时封面 crossfade（SubcomposeAsyncImage 自带）
 * - 播放/暂停弹跳动画
 */
@Composable
fun MiniPlayer(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.currentPositionMs.collectAsState()
    val isDark = isSystemInDarkTheme()

    if (currentSong == null) return

    val duration = currentSong?.duration ?: 1L
    val rawProgress = if (duration > 0) positionMs.toFloat() / duration.toFloat() else 0f

    // 平滑进度动画（避免跳变）
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200),
        label = "miniPlayerProgress"
    )

    // 播放/暂停按钮弹跳
    val playBtnScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playBtnScale"
    )

    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 14.dp,
        isDark = isDark,
        shadowElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { onExpand() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 专辑封面（动画 crossfade）
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(0.08f))
                ) {
                    coil.compose.SubcomposeAsyncImage(
                        model = currentSong?.albumArtUri,
                        contentDescription = "封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.width(12.dp))

                // 动态根据主题设置主颜色
                val contentColor = if (isDark) Color.White else Color.Black
                val textShadow = Shadow(
                    color = if (isDark) Color.Black.copy(0.6f) else Color.White.copy(0.6f),
                    blurRadius = 6f
                )

                // 歌曲信息（AnimatedContent 切换）
                AnimatedContent(
                    targetState = currentSong,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    modifier = Modifier.weight(1f),
                    label = "miniPlayerSongInfo"
                ) { song ->
                    Column {
                        Text(
                            text = song?.title ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                shadow = textShadow
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = contentColor
                        )
                        Text(
                            text = song?.artist ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                shadow = textShadow
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = contentColor.copy(0.6f)
                        )
                    }
                }

                // 播放/暂停（弹跳）
                IconButton(
                    onClick = { viewModel.playPause() },
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer { scaleX = playBtnScale; scaleY = playBtnScale }
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = contentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 下一首
                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = contentColor.copy(0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 底部进度条（平滑动画）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(0.07f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(Color(0xFFFF375F))
                )
            }
        }
    }
}