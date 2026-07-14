package com.inspiremusic.model

data class LrcLine(
    val timeMs: Long,
    val text: String,
    val isSynced: Boolean = true
)
