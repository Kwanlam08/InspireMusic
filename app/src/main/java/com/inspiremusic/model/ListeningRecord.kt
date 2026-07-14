package com.inspiremusic.model

data class ListeningRecord(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playedAt: Long,
    val duration: Long,
    val genre: String = ""
)
