@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package com.applemusic.clone.ui.components

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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/** Press feedback tuned for glass surfaces: a tiny lens compression plus a soft bounded wave. */
@Composable
fun Modifier.glassClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "glassPressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = if (enabled) 1f else 0.46f
    }.clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = rememberRipple(
            bounded = true,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        ),
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
    val isDark = isSystemInDarkTheme()
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
    val lensScaleX by animateFloatAsState(
        targetValue = if (dragging || pressed) 1.10f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = Spring.StiffnessMediumLow),
        label = "segmentedLensPressX"
    )
    val lensScaleY by animateFloatAsState(
        targetValue = if (dragging || pressed) 1.055f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "segmentedLensPressY"
    )

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
            val visibleIndex = if (dragging) dragIndex else lensPosition.value
            val visualActiveIndex = when {
                dragging -> dragIndex.roundToInt()
                pendingIndex != null -> pendingIndex
                else -> selectedIndex.takeIf { it >= 0 }
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
            if (showLens) {
                val lensTranslationPx = itemWidthPx * visibleIndex + with(density) { 2.dp.toPx() }
                BackdropLiquidGlass(
                    modifier = Modifier
                        .width(itemWidth - 4.dp)
                        .height(height - 10.dp)
                        .graphicsLayer { translationX = lensTranslationPx },
                    cornerRadius = 18.dp,
                    blurRadius = if (dragging) 13.dp else 9.dp,
                    surfaceAlpha = if (isDark) 0.050f else 0.032f,
                    highlightAlpha = if (isDark) 0.48f else 0.60f,
                    shadowAlpha = if (isDark) 0.24f else 0.14f,
                    scaleX = lensScaleX,
                    scaleY = lensScaleY,
                    useSharedBackdrop = true,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.34f else 0.24f)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.10f else 0.065f))
                    )
                }
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
                                indication = rememberRipple(
                                    bounded = true,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                ),
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
