package com.inspiremusic.ui.components

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.inspiremusic.settings.GlassRenderingMode
import com.inspiremusic.settings.LocalAppSettingsController
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight

/** Keeps Material's slider gestures, discrete steps, keyboard and accessibility behavior. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val active = pressed || dragged
    val settings by LocalAppSettingsController.current.settings.collectAsState()
    val trackBackdrop = if (active && Build.VERSION.SDK_INT >= 33 &&
        settings.glassRenderingMode == GlassRenderingMode.AUTO
    ) rememberLayerBackdrop() else null
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = { LiquidSliderThumb(active = active, backdrop = trackBackdrop) },
        track = { state ->
            val span = valueRange.endInclusive - valueRange.start
            val fraction = if (span > 0f) ((state.value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Box(
                Modifier.fillMaxWidth().height(6.dp)
                    // The track and thumb are siblings; the track cannot capture the thumb.
                    .then(if (trackBackdrop != null) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                    .clip(CircleShape)
                    .drawBehind {
                        drawRect(inactiveColor)
                        val filled = size.width * fraction
                        val start = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - filled
                        drawRect(activeColor, topLeft = Offset(start, 0f), size = Size(filled, size.height))
                    }
            )
        }
    )
}

@Composable
internal fun LiquidSliderThumb(
    active: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    height: Dp = 20.dp,
    backdrop: LayerBackdrop? = null
) {
    val motion = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "sliderGlassLift"
    )
    val material = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = { lens(5.dp.toPx(), 10.dp.toPx(), chromaticAberration = true) },
            highlight = { Highlight.Default.copy(alpha = motion.value.coerceIn(0f, 1f)) },
            onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - 0.68f * motion.value.coerceIn(0f, 1f))) }
        )
    } else {
        Modifier.drawBehind {
            val corner = CornerRadius(size.height / 2f)
            val lift = motion.value.coerceIn(0f, 1f)
            drawRoundRect(Color.White.copy(alpha = 0.88f - 0.38f * lift), cornerRadius = corner)
            val edge = 1.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.White, Color.White.copy(0.22f))),
                topLeft = Offset(edge / 2, edge / 2),
                size = Size(size.width - edge, size.height - edge),
                cornerRadius = CornerRadius(corner.x - edge / 2),
                style = Stroke(edge)
            )
        }
    }
    Box(
        modifier.size(width, height)
            .graphicsLayer {
                scaleX = 1f + 0.22f * motion.value
                scaleY = 1f + 0.16f * motion.value
            }
            .shadow(2.dp, CircleShape, clip = false)
            .then(material)
    )
}
