package com.applemusic.clone.ui.lyrics

import androidx.compose.ui.util.lerp
import com.applemusic.clone.model.LrcLine

fun findCurrentLyricIndex(
    currentPosition: Long,
    lyrics: List<LrcLine>
): Int {
    if (lyrics.isEmpty()) return -1

    var low = 0
    var high = lyrics.lastIndex
    var answer = -1

    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lyrics[mid].timeMs <= currentPosition) {
            answer = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return answer.coerceAtLeast(0)
}

fun calculateScrollOffset(
    currentPosition: Long,
    lyrics: List<LrcLine>,
    layout: LyricLayoutSnapshot,
    focusOffset: Float = 0f
): Float {
    if (lyrics.isEmpty() || layout.lines.isEmpty()) return 0f

    val currentIndex = findCurrentLyricIndex(currentPosition, lyrics)
        .coerceIn(0, layout.lines.lastIndex)
    val currentLine = layout.lines[currentIndex]
    val nextLine = layout.lines.getOrNull(currentIndex + 1) ?: return (currentLine.center - focusOffset).coerceAtLeast(0f)

    val start = lyrics[currentIndex].timeMs
    val end = lyrics.getOrNull(currentIndex + 1)?.timeMs ?: start
    val progress = if (end > start) {
        ((currentPosition - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    return (lerp(currentLine.center, nextLine.center, progress) - focusOffset).coerceAtLeast(0f)
}
