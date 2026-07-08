package com.applemusic.clone.ui.lyrics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import kotlin.math.abs

fun DrawScope.drawContinuousLyrics(
    snapshot: LyricLayoutSnapshot,
    scrollOffset: Float,
    centerY: Float,
    left: Float,
    textWidth: Float,
    activeColor: Color = Color.White
) {
    val viewportTop = -size.height * 0.2f
    val viewportBottom = size.height * 1.2f
    val fadeRange = size.height * 0.52f

    val contentTop = scrollOffset + viewportTop - centerY
    val contentBottom = scrollOffset + viewportBottom - centerY
    val startIndex = snapshot.lines.firstPotentiallyVisibleIndex(contentTop)

    for (i in startIndex..snapshot.lines.lastIndex) {
        val line = snapshot.lines[i]
        if (line.top > contentBottom) break

        val lineCenterY = centerY + line.center - scrollOffset
        val lineTopY = lineCenterY - line.height / 2f
        val lineBottomY = lineCenterY + line.height / 2f
        if (lineBottomY < viewportTop || lineTopY > viewportBottom) continue

        val distance = abs(lineCenterY - centerY)
        val closeness = (1f - distance / fadeRange).coerceIn(0f, 1f)
        val alpha = (0.2f + 0.8f * closeness).coerceIn(0.16f, 1f)

        clipRect(left = left, top = lineTopY - 4f, right = left + textWidth, bottom = lineBottomY + 4f) {
            drawText(
                textLayoutResult = line.layout,
                color = activeColor.copy(alpha = alpha),
                topLeft = Offset(left, lineTopY)
            )
        }
    }
}

private fun List<LyricLineLayout>.firstPotentiallyVisibleIndex(contentTop: Float): Int {
    if (isEmpty()) return 0

    var low = 0
    var high = lastIndex
    var answer = lastIndex

    while (low <= high) {
        val mid = (low + high) ushr 1
        val bottom = this[mid].top + this[mid].height
        if (bottom >= contentTop) {
            answer = mid
            high = mid - 1
        } else {
            low = mid + 1
        }
    }

    return answer
}
