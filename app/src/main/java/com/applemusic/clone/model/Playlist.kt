package com.applemusic.clone.model

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val coverUri: String? = null,
    val subtitle: String = ""
)
