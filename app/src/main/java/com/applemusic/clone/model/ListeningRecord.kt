package com.applemusic.clone.model

data class ListeningRecord(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playedAt: Long,
    val duration: Long
)
