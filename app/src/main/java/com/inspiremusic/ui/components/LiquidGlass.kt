package com.inspiremusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    isDark: Boolean = true,
    shadowElevation: Dp = 14.dp,
    blurRadius: Dp = 72.dp,
    tintAlpha: Float = 0.006f,
    borderAlpha: Float = 0.10f,
    washStrength: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val hazeState = LocalHazeState.current
    val tintColor = if (isDark) {
        Color.Black.copy(alpha = tintAlpha)
    } else {
        Color.White.copy(alpha = tintAlpha)
    }

    val innerWash = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.028f * washStrength),
                Color.White.copy(alpha = 0.008f * washStrength),
                Color.Black.copy(alpha = 0.020f * washStrength)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.13f * washStrength),
                Color.White.copy(alpha = 0.052f * washStrength),
                Color.White.copy(alpha = 0.018f * washStrength)
            )
        )
    }

    val edgeBrush = Brush.verticalGradient(
        listOf(
            if (isDark) Color.White.copy(alpha = borderAlpha * 1.15f) else Color.White.copy(alpha = borderAlpha * 1.35f),
            if (isDark) Color.Black.copy(alpha = borderAlpha * 1.05f) else Color.Black.copy(alpha = borderAlpha * 0.70f),
            if (isDark) Color.Black.copy(alpha = borderAlpha * 0.45f) else Color.Black.copy(alpha = borderAlpha * 0.22f)
        )
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                spotColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.16f),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.075f else 0.045f)
            )
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .clip(shape)
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = tintColor,
                    tint = HazeTint(tintColor),
                    blurRadius = blurRadius,
                    noiseFactor = 0.045f
                )
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(innerWash, shape)
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (if (isDark) 0.052f else 0.12f) * washStrength),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 68f
                    ),
                    shape
                )
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (if (isDark) 0.030f else 0.052f) * washStrength),
                            Color.Transparent
                        ),
                        radius = 260f
                    ),
                    shape
                )
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, edgeBrush, shape)
        )

        content()
    }
}
