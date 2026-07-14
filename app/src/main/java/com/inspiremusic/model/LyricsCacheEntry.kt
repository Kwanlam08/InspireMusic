package com.inspiremusic.model

data class LyricsCacheEntry(
    val audioId: Long,
    val title: String,
    val artist: String,
    val path: String,
    val sizeBytes: Long,
    val updatedAt: Long,
    val lineCount: Int,
    val isSynced: Boolean,
    val preview: String
)
