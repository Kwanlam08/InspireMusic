package com.applemusic.clone.model

import android.net.Uri

data class AudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val data: String = "",
    val lyricsPath: String? = null,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val sizeBytes: Long = 0L,
    val dateModifiedMs: Long = 0L,
    val genre: String = ""
)
