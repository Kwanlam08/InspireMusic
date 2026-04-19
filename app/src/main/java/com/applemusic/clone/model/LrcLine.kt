package com.applemusic.clone.model

data class LrcLine(
    val timeMs: Long,   // 歌词出现时间（毫秒）
    val text: String    // 歌词文本
)
