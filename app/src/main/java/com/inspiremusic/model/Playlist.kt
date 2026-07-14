package com.inspiremusic.model

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val coverUri: String? = null,
    val subtitle: String = "",
    val isSmart: Boolean = false
)
