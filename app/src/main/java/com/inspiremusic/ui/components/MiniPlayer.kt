package com.inspiremusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import com.inspiremusic.R
import com.inspiremusic.viewmodel.MusicViewModel

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
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    if (currentSong == null) return

    val artworkRequest = remember(currentSong?.id, currentSong?.albumArtUri) {
        ImageRequest.Builder(context)
            .data(currentSong?.albumArtUri)
            .size(96)
            .crossfade(false)
            .build()
    }

    val duration = currentSong?.duration ?: 1L

    // 播放/暂停按钮弹跳
    BackdropLiquidGlass(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        cornerRadius = 22.dp,
        blurRadius = 7.dp,
        surfaceAlpha = if (isDark) 0.014f else 0.020f,
        highlightAlpha = if (isDark) 0.36f else 0.46f,
        shadowAlpha = if (isDark) 0.16f else 0.10f,
        ignoreBackdropCompatibility = true
    ) {
        Column(
            modifier = Modifier.padding(bottom = 5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clickable { onExpand() }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 专辑封面（动画 crossfade）
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(0.08f))
                ) {
                    coil.compose.SubcomposeAsyncImage(
                        model = artworkRequest,
                        contentDescription = stringResource(R.string.album_art),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.width(12.dp))

                // 动态根据主题设置主颜色
                val contentColor = if (isDark) Color.White else Color.Black
                val textShadow = Shadow(
                    color = if (isDark) Color.Black.copy(0.62f) else Color.White.copy(0.68f),
                    blurRadius = 5f
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
                val controlColor = if (isDark) Color.White else Color.Black
                IconButton(onClick = viewModel::skipPrev, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.np_previous),
                        tint = controlColor,
                        modifier = Modifier.size(23.dp)
                    )
                }
                IconButton(onClick = viewModel::playPause, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.np_pause) else stringResource(R.string.np_play),
                        tint = controlColor,
                        modifier = Modifier.size(25.dp)
                    )
                }

                // 下一首
                IconButton(onClick = viewModel::skipNext, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.np_next),
                        tint = controlColor,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            // 底部进度条（平滑动画）
            MiniPlayerProgress(viewModel = viewModel, duration = duration, isDark = isDark)
        }
    }
}

@Composable
private fun MiniPlayerProgress(
    viewModel: MusicViewModel,
    duration: Long,
    isDark: Boolean
) {
    val positionMs by viewModel.currentPositionMs.collectAsState()
    val rawProgress = if (duration > 0) positionMs.toFloat() / duration.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200),
        label = "miniPlayerProgress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(99.dp))
            .background((if (isDark) Color.White else Color.Black).copy(0.055f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
