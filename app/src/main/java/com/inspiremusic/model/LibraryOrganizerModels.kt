package com.inspiremusic.model

data class MetadataDraft(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null
)

enum class LibraryIssueType { DUPLICATE_ALBUM, MISSING_ARTWORK, MISSING_TRACK, MISSING_YEAR, MISSING_GENRE, MISSING_ALBUM_ARTIST }

data class LibraryIssue(
    val type: LibraryIssueType,
    val title: String,
    val detail: String,
    val songIds: List<Long>,
    val suggestedAlbum: String? = null,
    val suggestedAlbumArtist: String? = null
)

data class OrganizerHistoryBatch(
    val batchId: String,
    val label: String,
    val createdAt: Long,
    val affectedSongs: Int
)

data class LibraryHealthReport(
    val totalSongs: Int = 0,
    val issues: List<LibraryIssue> = emptyList(),
    val scannedAt: Long = 0L
) {
    val healthScore: Int
        get() {
            val affected = issues.flatMap { it.songIds }.distinct().size
            return if (totalSongs == 0) 100 else (100 - affected.coerceAtMost(totalSongs) * 100 / totalSongs).coerceIn(0, 100)
        }
}
