package com.applemusic.clone.model

import android.net.Uri

data class AudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,         // 毫秒
    val uri: Uri,
    val albumArtUri: Uri?,
    val data: String = "",      // 文件物理路径，用于查找同名 .lrc
    val lyricsPath: String? = null, // 对应 .lrc 文件的完整路径
    val trackNumber: Int = 0,   // 专辑内的曲目序号
    val discNumber: Int = 1     // 光盘编号
)
