package com.applemusic.clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

val LocalBackdropLayer = compositionLocalOf<LayerBackdrop?> { null }
val LocalBackdropRenderingEnabled = compositionLocalOf { true }

@Composable
fun BackdropLiquidGlass(
    modifier: Modifier = Modifier,
    cornerRadius: Dp,
    blurRadius: Dp = 8.dp,
    surfaceAlpha: Float = 0.028f,
    highlightAlpha: Float = 0.54f,
    shadowAlpha: Float = 0.18f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    useSharedBackdrop: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)
    val sharedBackdropEnabled = LocalBackdropRenderingEnabled.current
    val backdrop = LocalBackdropLayer.current.takeIf { useSharedBackdrop && sharedBackdropEnabled }
    val surfaceColor = if (isDark) {
        Color.Black.copy(alpha = surfaceAlpha)
    } else {
        Color.White.copy(alpha = surfaceAlpha)
    }
    val baseModifier = modifier
        .shadow(
            elevation = 12.dp,
            shape = shape,
            spotColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f),
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.06f else 0.035f)
        )
        .graphicsLayer {
            this.shape = shape
            clip = true
            this.scaleX = scaleX
            this.scaleY = scaleY
        }
        .clip(shape)

    Box(
        modifier = if (backdrop != null) {
            baseModifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(blurRadius.toPx())
                    lens(
                        refractionHeight = 12.dp.toPx(),
                        refractionAmount = 22.dp.toPx(),
                        chromaticAberration = true
                    )
                },
                highlight = {
                    Highlight.Default.copy(alpha = highlightAlpha.coerceIn(0f, 1f))
                },
                shadow = {
                    Shadow(
                        radius = 18.dp,
                        color = Color.Black.copy(alpha = if (isDark) 0.18f else 0.10f),
                        alpha = 0.85f
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 8.dp,
                        color = Color.Black.copy(alpha = shadowAlpha),
                        alpha = 0.72f
                    )
                },
                onDrawSurface = {
                    drawRect(surfaceColor)
                }
            )
        } else {
            baseModifier
                .background(surfaceColor, shape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.14f else 0.22f),
                            Color.Transparent
                        )
                    ),
                    shape
                )
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.030f else 0.075f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (isDark) 0.040f else 0.018f)
                        )
                    ),
                    shape
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = highlightAlpha),
                            Color.White.copy(alpha = if (isDark) 0.090f else 0.16f),
                            Color.Black.copy(alpha = shadowAlpha)
                        )
                    ),
                    shape
                )
        }
            .background(surfaceColor, shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.030f else 0.070f),
                        Color.Transparent,
                        Color.Black.copy(alpha = if (isDark) 0.028f else 0.010f)
                    )
                ),
                shape
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = highlightAlpha),
                        Color.White.copy(alpha = if (isDark) 0.090f else 0.16f),
                        Color.Black.copy(alpha = shadowAlpha)
                    )
                ),
                shape
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    0.6.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.22f else 0.34f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (isDark) 0.15f else 0.10f)
                        )
                    ),
                    shape
                )
        )
        content()
    }
}
