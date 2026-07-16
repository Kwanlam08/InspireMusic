package com.inspiremusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
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
import com.inspiremusic.ui.theme.LocalAppIsDark

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
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalAppIsDark.current
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
    // Some GPU drivers crash inside RenderThread when many backdrop textures are
    // created during navigation. The compatibility material keeps the translucent
    // glass hierarchy and edge light without allocating another sampled texture.
    val safeSurfaceBrush = Brush.verticalGradient(
        if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color(0xFF202024).copy(alpha = 0.68f),
                Color.Black.copy(alpha = 0.52f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.84f),
                Color.White.copy(alpha = 0.66f),
                Color(0xFFE8E8ED).copy(alpha = 0.54f)
            )
        }
    )
    val safeBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.Black.copy(alpha = 0.095f)
    }
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
                // A directional highlight plus an explicit border produced a bright
                // horizontal seam on several GPU/driver combinations. Keep one edge layer.
                highlight = { Highlight.Default.copy(alpha = 0f) },
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
        }

    Box(
        modifier = glassModifier
            .then(
                if (backdrop != null) {
                    Modifier.border(
                        0.6.dp,
                        borderColor ?: safeBorderColor.copy(alpha = safeBorderColor.alpha * 0.72f),
                        shape
                    )
                } else {
                    Modifier.border(1.dp, borderColor ?: safeBorderColor, shape)
                }
            )
    ) {
        content()
    }
}
