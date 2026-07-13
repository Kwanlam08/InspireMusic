package com.applemusic.clone.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp
) {
    if (items.isEmpty()) return
    val isDark = isSystemInDarkTheme()
    val selectedIndex = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    var dragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    val settledIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "glassSegmentIndex"
    )

    BackdropLiquidGlass(
        modifier = modifier.fillMaxWidth().height(height),
        cornerRadius = 22.dp,
        blurRadius = 10.dp,
        surfaceAlpha = if (isDark) 0.035f else 0.024f,
        highlightAlpha = if (isDark) 0.48f else 0.68f,
        shadowAlpha = if (isDark) 0.24f else 0.12f,
        useSharedBackdrop = true
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
            val density = LocalDensity.current
            val itemWidth = maxWidth / items.size
            val itemWidthPx = with(density) { itemWidth.toPx() }.coerceAtLeast(1f)
            val visibleIndex = if (dragging) dragIndex else settledIndex
            val dragModifier = Modifier.pointerInput(selectedIndex, items.size, itemWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        dragIndex = selectedIndex.toFloat()
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragIndex = (dragIndex + amount / itemWidthPx)
                            .coerceIn(0f, (items.size - 1).toFloat())
                    },
                    onDragEnd = {
                        val target = dragIndex.roundToInt().coerceIn(items.indices)
                        dragging = false
                        onSelected(items[target].first)
                    },
                    onDragCancel = { dragging = false }
                )
            }
            Box(
                modifier = Modifier
                    .offset(x = itemWidth * visibleIndex)
                    .width(itemWidth)
                    .height(height - 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.15f),
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.10f else 0.07f)
                            )
                        )
                    )
                    .then(dragModifier)
            )
            Row(Modifier.fillMaxSize().then(dragModifier)) {
                items.forEachIndexed { index, (value, label) ->
                    val itemSelected = index == selectedIndex
                    val color by animateColorAsState(
                        if (itemSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                        label = "glassSegmentText"
                    )
                    val interaction = remember(value) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(17.dp))
                            .semantics { this.selected = itemSelected }
                            .clickable(
                                interactionSource = interaction,
                                indication = rememberRipple(
                                    bounded = true,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                ),
                                role = Role.Tab
                            ) { onSelected(value) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
