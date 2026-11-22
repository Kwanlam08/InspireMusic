package com.inspiremusic.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Shared resting tint and lifted rim for navigation, segmented controls and sliders. */
internal fun Modifier.glassSelectionLens(isDark: Boolean, lift: () -> Float) = drawBehind {
    val amount = lift().coerceIn(0f, 1f)
    val corner = CornerRadius(size.height / 2f)
    val fill = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    drawRoundRect(fill, alpha = 1f - 0.74f * amount, cornerRadius = corner)
    val edge = 1.dp.toPx()
    if (amount > 0f && size.minDimension > edge) {
        drawRoundRect(
            Color.White.copy(alpha = (if (isDark) 0.44f else 0.66f) * amount),
            topLeft = Offset(edge / 2, edge / 2),
            size = Size(size.width - edge, size.height - edge),
            cornerRadius = CornerRadius(corner.x - edge / 2),
            style = Stroke(edge)
        )
    }
}
