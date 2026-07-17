package com.inspiremusic.model

data class LrcLine(
    val timeMs: Long,
    val text: String,
    val isSynced: Boolean = true,
    val translation: String? = null,
    val romanization: String? = null,
    val words: List<LrcWord> = emptyList()
)

data class LrcWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
