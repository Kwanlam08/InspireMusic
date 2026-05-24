package com.applemusic.clone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.viewmodel.MusicViewModel
import java.util.Calendar

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
        hour < 12 -> "早上好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }

    var inputText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val showResult = aiGeneratedSongs.isNotEmpty() || aiError != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 顶部问候 ─────────────────────────────────────
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onBackground
        )

        // ── AI 输入区 ────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF7C4DFF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "描述你想听的音乐...",
                            color = MaterialTheme.colorScheme.onBackground.copy(0.35f)
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
                        }
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "发送",
                            tint = Color(0xFF7C4DFF)
                        )
                    }
                }
            }
        }

        // ── 快速标签 ─────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Text(
            text = "试着描述",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground.copy(0.7f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        val quickTags = listOf(
            "🎵" to "放松的轻音乐",
            "💪" to "运动健身时听的歌",
            "😢" to "适合伤感的慢歌",
            "🎉" to "派对嗨曲",
            "🌙" to "睡前安眠纯音乐",
            "🎲" to "随机惊喜"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickTags.take(3).forEach { (emoji, label) ->
                QuickTag(emoji = emoji, label = label, onClick = { inputText = label }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickTags.drop(3).forEach { (emoji, label) ->
                QuickTag(emoji = emoji, label = label, onClick = { inputText = label }, modifier = Modifier.weight(1f))
            }
        }

        // ── 结果区 ──────────────────────────────────────
        Spacer(Modifier.height(24.dp))

        if (aiIsLoading) {
            // 加载动画
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF7C4DFF),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "AI 正在为你挑选歌曲...",
                        color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        } else if (aiError != null) {
            // 错误
            Box(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        null,
                        tint = Color(0xFFFF375F),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        aiError ?: "",
                        color = Color(0xFFFF375F),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (showResult) {
            // 成功结果
            AnimatedVisibility(visible = true, enter = fadeIn()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "AI 推荐结果 (${aiGeneratedSongs.size} 首)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row {
                            // 播放
                            IconButton(
                                onClick = { viewModel.playAiPlaylist() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    "播放",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // 收藏
                            IconButton(
                                onClick = { viewModel.saveAiPlaylist() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.BookmarkAdd,
                                    "保存为播放列表",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 歌曲列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 160.dp)
                    ) {
                        items(aiGeneratedSongs) { song ->
                            val isPlaying = viewModel.currentSong.collectAsState().value?.id == song.id
                            AiSongResultRow(
                                song = song,
                                isPlaying = isPlaying,
                                onClick = { viewModel.playList(aiGeneratedSongs, aiGeneratedSongs.indexOf(song)) }
                            )
                        }
                    }
                }
            }
        } else {
            // 空状态
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.15f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "让 AI 为你生成专属播放列表",
                        color = MaterialTheme.colorScheme.onBackground.copy(0.3f),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTag(emoji: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AiSongResultRow(
    song: AudioItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            coil.compose.AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isPlaying) {
            Icon(
                Icons.Default.VolumeUp,
                "播放中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
