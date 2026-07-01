package com.applemusic.clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
    ignoreBackdropCompatibility: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)
    val sharedBackdropEnabled = LocalBackdropRenderingEnabled.current
    val backdrop = LocalBackdropLayer.current.takeIf {
        useSharedBackdrop && (sharedBackdropEnabled || ignoreBackdropCompatibility)
    }
    val backdropSurfaceColor = if (isDark) {
        Color.Black.copy(alpha = surfaceAlpha)
    } else {
        Color.White.copy(alpha = surfaceAlpha)
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val safeSurfaceAlpha = surfaceAlpha.coerceIn(0.01f, if (isDark) 0.16f else 0.22f)
    val safeSurfaceBrush = Brush.verticalGradient(
        listOf(
            if (isDark) Color.White.copy(alpha = safeSurfaceAlpha * 1.05f) else Color.White.copy(alpha = safeSurfaceAlpha * 1.85f),
            if (isDark) Color.White.copy(alpha = safeSurfaceAlpha * 0.46f) else Color.White.copy(alpha = safeSurfaceAlpha * 0.86f),
            if (isDark) Color.Black.copy(alpha = 0.040f) else Color.Black.copy(alpha = 0.014f)
        )
    )
    val safeGlowBrush = Brush.radialGradient(
        listOf(
            accentColor.copy(alpha = if (isDark) 0.055f else 0.070f),
            Color.White.copy(alpha = if (isDark) 0.030f else 0.080f),
            Color.Transparent
        ),
        radius = 340f
    )
    val safeCornerSheenBrush = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.075f else 0.16f),
            Color.Transparent
        ),
        radius = 180f
    )
    val safeBorderBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.30f else 0.46f),
            accentColor.copy(alpha = if (isDark) 0.095f else 0.080f),
            Color.Black.copy(alpha = if (isDark) 0.20f else 0.105f)
        )
    )
    val baseModifier = modifier
        .shadow(
            elevation = if (backdrop != null) 12.dp else 1.dp,
            shape = shape,
            spotColor = Color.Black.copy(
                alpha = if (backdrop != null) {
                    if (isDark) 0.24f else 0.12f
                } else {
                    if (isDark) 0.11f else 0.045f
                }
            ),
            ambientColor = Color.Black.copy(
                alpha = if (backdrop != null) {
                    if (isDark) 0.06f else 0.035f
                } else {
                    if (isDark) 0.028f else 0.012f
                }
            )
        )
        .graphicsLayer {
            this.shape = shape
            clip = true
            this.scaleX = scaleX
            this.scaleY = scaleY
        }
        .clip(shape)

    val glassModifier = if (backdrop != null) {
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
                    drawRect(backdropSurfaceColor)
                }
            )
        } else {
            baseModifier
                .background(safeSurfaceBrush, shape)
                .background(safeGlowBrush, shape)
                .background(safeCornerSheenBrush, shape)
        }

    Box(
        modifier = glassModifier
            .then(
                if (backdrop != null) {
                    Modifier.border(
                        0.85.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = highlightAlpha * 0.86f),
                                Color.White.copy(alpha = if (isDark) 0.055f else 0.050f),
                                Color.Black.copy(alpha = shadowAlpha * 0.78f)
                            )
                        ),
                        shape
                    )
                } else {
                    Modifier.border(1.dp, safeBorderBrush, shape)
                }
            )
    ) {
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        0.45.dp,
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.10f else 0.12f),
                                Color.Transparent,
                                Color.Black.copy(alpha = if (isDark) 0.10f else 0.055f)
                            )
                        ),
                        shape
                    )
            )
        }
        content()
    }
}
