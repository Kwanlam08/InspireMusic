package com.inspiremusic.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.inspiremusic.settings.GlassRenderingMode
import com.inspiremusic.settings.LocalAppSettingsController
import com.inspiremusic.ui.theme.LocalAppIsDark
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight

/** Capsule switch inspired by Kyant0/AndroidLiquidGlass's LiquidToggle sample. */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by LocalAppSettingsController.current.settings.collectAsState()
    val dark = LocalAppIsDark.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val offColor = if (dark) Color(0xFF787880).copy(0.36f) else Color(0xFF787878).copy(0.20f)
    val onColor = MaterialTheme.colorScheme.primary
    val travelPx = with(LocalDensity.current) { 20.dp.toPx() }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val position = animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "switchPosition")
    val press = animateFloatAsState(if (pressed || dragFraction != null) 1f else 0f, tween(140), label = "switchPress")
    val showLens by remember(press) { derivedStateOf { press.value > 0f } }
    val dragState = rememberDraggableState { delta ->
        dragFraction = ((dragFraction ?: position.value) + delta / travelPx).coerceIn(0f, 1f)
    }

    // Only record the track while the lens is visible. It is a sibling of the
    // thumb, so it can never capture the thumb or sample the enclosing NavHost.
    val trackBackdrop = if (showLens && Build.VERSION.SDK_INT >= 33 &&
        settings.glassRenderingMode == GlassRenderingMode.AUTO
    ) rememberLayerBackdrop() else null

    Box(
        modifier = modifier
            .size(64.dp, 48.dp)
            .toggleable(checked, interactionSource, indication = null, role = Role.Switch, onValueChange = onCheckedChange)
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                reverseDirection = rtl,
                onDragStarted = { dragFraction = position.value },
                onDragStopped = {
                    val target = (dragFraction ?: position.value) >= 0.5f
                    dragFraction = null
                    if (target != checked) onCheckedChange(target)
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.size(64.dp, 28.dp)
                .then(if (trackBackdrop != null) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                .clip(CircleShape)
                .drawBehind { drawRect(lerp(offColor, onColor, dragFraction ?: position.value)) }
        )

        val thumbMaterial = if (trackBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = trackBackdrop,
                shape = { CircleShape },
                effects = { lens(5.dp.toPx(), 10.dp.toPx(), chromaticAberration = true) },
                highlight = { Highlight.Default.copy(alpha = press.value) },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - 0.78f * press.value)) }
            )
        } else {
            Modifier.background(Color.White, CircleShape)
        }
        Box(
            Modifier.size(40.dp, 24.dp)
                .graphicsLayer {
                    val x = 2.dp.toPx() + travelPx * (dragFraction ?: position.value)
                    translationX = if (rtl) -x else x
                    scaleX = 1f + 0.14f * press.value
                    scaleY = 1f + 0.10f * press.value
                }
                .shadow(2.dp, CircleShape, clip = false)
                .then(thumbMaterial)
        )
    }
}
