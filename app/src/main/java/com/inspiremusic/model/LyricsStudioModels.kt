package com.inspiremusic.model

enum class LyricTextAlignment { START, CENTER }

data class LyricsDisplaySettings(
    val portraitFontSizeSp: Float = 24f,
    val landscapeFontSizeSp: Float = 22f,
    val activeFontWeight: Int = 900,
    val translationFontSizeSp: Float = 15f,
    val lineSpacingDp: Float = 12f,
    val alignment: LyricTextAlignment = LyricTextAlignment.START,
    val activeEmphasis: Float = 1f,
    val showTranslation: Boolean = true,
    val bluetoothDelayMs: Long = 0L
)

data class LyricVersion(
    val id: String,
    val audioId: Long,
    val sourceName: String,
    val createdAt: Long,
    val rawContent: String
)

data class FavoriteLyricLine(
    val id: String,
    val audioId: Long,
    val title: String,
    val artist: String,
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class LyricIssueType { WRONG_SONG, TIMING, INCOMPLETE, TRANSLATION }

data class LyricIssue(
    val id: String,
    val audioId: Long,
    val type: LyricIssueType,
    val note: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class LyricSearchCandidate(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSeconds: Double,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val translationLineCount: Int
)
