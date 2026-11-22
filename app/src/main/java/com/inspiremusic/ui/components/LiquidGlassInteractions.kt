@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package com.inspiremusic.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.inspiremusic.ui.theme.LocalAppIsDark
import com.inspiremusic.settings.GlassRenderingMode
import com.inspiremusic.settings.LocalAppSettingsController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens

/** Press feedback tuned for glass surfaces: a tiny lens compression plus a soft bounded wave. */
@Composable
fun Modifier.glassClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "glassPressScale"
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = if (enabled) 1f else 0.46f
    }.clip(ContinuousRoundedCornerShape(AppCornerTokens.Card)).clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

/** A tap-and-drag segmented rail whose lens follows the finger and springs to the nearest item. */
@Composable
fun <T> LiquidGlassSegmentedControl(
    items: List<Pair<T, String>>,
    selected: T?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    icons: Map<T, ImageVector> = emptyMap()
) {
    if (items.isEmpty()) return
    val isDark = LocalAppIsDark.current
    val settings by LocalAppSettingsController.current.settings.collectAsState()
    val selectedIndex = items.indexOfFirst { it.first == selected }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableFloatStateOf(selectedIndex.coerceAtLeast(0).toFloat()) }
    var pendingIndex by remember { mutableStateOf<Int?>(null) }
    val lensPosition = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    val pressInteraction = remember { MutableInteractionSource() }
    val pressed by pressInteraction.collectIsPressedAsState()
    val settleSpec = spring<Float>(
        dampingRatio = 0.60f,
        stiffness = Spring.StiffnessMediumLow
    )
    val lift = animateFloatAsState(
        targetValue = if (dragging || pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.59f, stiffness = Spring.StiffnessMedium),
        label = "segmentedGlassLift"
    )
    val lifted by remember(lift) { derivedStateOf { lift.value > 0.015f } }
    val railBackdrop = if (lifted && Build.VERSION.SDK_INT >= 33 &&
        settings.glassRenderingMode == GlassRenderingMode.AUTO
    ) rememberLayerBackdrop() else null

    LaunchedEffect(selectedIndex) {
        if (!dragging && pendingIndex == null && selectedIndex >= 0) {
            lensPosition.animateTo(selectedIndex.toFloat(), settleSpec)
        }
    }

    BackdropLiquidGlass(
        modifier = modifier.fillMaxWidth().height(height),
        cornerRadius = 22.dp,
        blurRadius = 10.dp,
        surfaceAlpha = if (isDark) 0.035f else 0.024f,
        highlightAlpha = if (isDark) 0.34f else 0.46f,
        shadowAlpha = if (isDark) 0.24f else 0.12f,
        useSharedBackdrop = true,
        borderColor = MaterialTheme.colorScheme.onSurface.copy(
            alpha = if (isDark) 0.24f else 0.16f
        )
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
            val density = LocalDensity.current
            val itemWidth = maxWidth / items.size
            val itemWidthPx = with(density) { itemWidth.toPx() }.coerceAtLeast(1f)
            val visualActiveIndex by remember(selectedIndex) {
                derivedStateOf {
                    when {
                        dragging -> dragIndex.roundToInt()
                        pendingIndex != null -> pendingIndex
                        else -> selectedIndex.takeIf { it >= 0 }
                    }
                }
            }
            val showLens = dragging || pendingIndex != null || selectedIndex >= 0
            val dragModifier = Modifier.pointerInput(selectedIndex, items.size, itemWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragIndex = if (selectedIndex >= 0) {
                            lensPosition.value
                        } else {
                            (offset.x / itemWidthPx - 0.5f)
                                .coerceIn(0f, (items.size - 1).toFloat())
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragIndex = (dragIndex + amount / itemWidthPx)
                            .coerceIn(0f, (items.size - 1).toFloat())
                    },
                    onDragEnd = {
                        val target = dragIndex.roundToInt().coerceIn(items.indices)
                        val releasePosition = dragIndex
                        pendingIndex = target
                        scope.launch {
                            lensPosition.snapTo(releasePosition)
                            dragging = false
                            lensPosition.animateTo(target.toFloat(), settleSpec)
                            pendingIndex = null
                        }
                        onSelected(items[target].first)
                    },
                    onDragCancel = {
                        val releasePosition = dragIndex
                        val target = selectedIndex.takeIf { it >= 0 }
                        scope.launch {
                            lensPosition.snapTo(releasePosition)
                            dragging = false
                            if (target != null) lensPosition.animateTo(target.toFloat(), settleSpec)
                        }
                    }
                )
            }
            // Capture only this sibling underlay, never the parent page or the lens itself.
            if (railBackdrop != null) {
                Box(
                    Modifier.fillMaxSize().layerBackdrop(railBackdrop).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                )
            }
            if (showLens) {
                val lensInsetPx = with(density) { 2.dp.toPx() }
                val lensMaterial = if (railBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = railBackdrop,
                        shape = { CircleShape },
                        effects = { lens(6.dp.toPx(), 12.dp.toPx(), chromaticAberration = true) },
                        layerBlock = { alpha = lift.value.coerceIn(0f, 1f) }
                    )
                } else Modifier
                Box(
                    modifier = Modifier
                        .width(itemWidth - 4.dp)
                        .height(height - 10.dp)
                        .graphicsLayer {
                            val visibleIndex = if (dragging) dragIndex else lensPosition.value
                            translationX = itemWidthPx * visibleIndex + lensInsetPx
                            val amount = lift.value.coerceAtLeast(0f)
                            scaleX = 1f + 0.16f * amount
                            scaleY = 1f + 0.13f * amount
                        }
                        .then(lensMaterial)
                        .glassSelectionLens(isDark) { lift.value }
                )
            }
            Row(Modifier.fillMaxSize().then(dragModifier)) {
                items.forEachIndexed { index, (value, label) ->
                    val itemSelected = index == visualActiveIndex
                    val color by animateColorAsState(
                        if (itemSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                        label = "glassSegmentText"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(17.dp))
                            .semantics { this.selected = itemSelected }
                            .clickable(
                                interactionSource = pressInteraction,
                                indication = null,
                                role = Role.Tab
                            ) { onSelected(value) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            icons[value]?.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                                androidx.compose.foundation.layout.Spacer(Modifier.width(7.dp))
                            }
                            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
