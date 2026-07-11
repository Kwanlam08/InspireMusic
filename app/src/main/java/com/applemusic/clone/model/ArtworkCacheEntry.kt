package com.applemusic.clone.model

data class ArtworkCacheEntry(
    val audioId: Long,
    val title: String,
    val album: String,
    val artist: String,
    val path: String,
    val sizeBytes: Long,
    val updatedAt: Long,
    val albumId: Long = 0L
)
