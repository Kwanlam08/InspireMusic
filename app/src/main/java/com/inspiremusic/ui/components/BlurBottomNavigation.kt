package com.inspiremusic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.inspiremusic.ui.navigation.BottomNavItems
import com.inspiremusic.ui.navigation.Screen
import com.inspiremusic.ui.navigation.SubRoutes
import com.inspiremusic.ui.theme.LocalAppIsDark
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun String?.toBottomRootRoute(): String = when {
    this == Screen.Library.route -> Screen.Library.route
    this == Screen.Diary.route -> Screen.Diary.route
    this == Screen.Settings.route -> Screen.Settings.route
    this == SubRoutes.DIARY -> Screen.Diary.route
    this == "library/settings" -> Screen.Settings.route
    this == SubRoutes.SONGS -> Screen.Library.route
    this == SubRoutes.ALBUMS -> Screen.Library.route
    this == SubRoutes.ARTISTS -> Screen.Library.route
    this == SubRoutes.PLAYLISTS -> Screen.Library.route
    this == "favorites" -> Screen.Library.route
    this == "library/search" -> Screen.Library.route
    this?.startsWith("library/search/") == true -> Screen.Library.route
    this?.startsWith("library/") == true -> Screen.Library.route
    this?.startsWith("playlist/") == true -> Screen.Library.route
    else -> Screen.Home.route
}

@Composable
fun BlurBottomNavigation(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDark = LocalAppIsDark.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pageBackdrop = LocalBackdropLayer.current.takeIf { Build.VERSION.SDK_INT >= 33 }
    val selectedRootRoute = currentRoute.toBottomRootRoute()
    val selectedIndex = BottomNavItems
        .indexOfFirst { it.route == selectedRootRoute }
        .takeIf { it >= 0 } ?: 0
    fun navigateToIndex(index: Int) {
        val target = BottomNavItems.getOrNull(index) ?: return
        if (target.route == currentRoute) return
        navController.navigate(target.route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = target.route != Screen.Library.route
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        BackdropLiquidGlass(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp),
            cornerRadius = AppCornerTokens.Sheet,
            blurRadius = 7.dp,
            highlightAlpha = if (isDark) 0.36f else 0.46f,
            shadowAlpha = if (isDark) 0.16f else 0.10f,
            surfaceRole = GlassSurfaceRole.NAVIGATION_CHROME,
            // This bar floats above the captured NavHost, so retain the real
            // refractive material instead of falling back to an opaque surface.
            ignoreBackdropCompatibility = true
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                val density = LocalDensity.current
                val itemCount = BottomNavItems.size.coerceAtLeast(1)
                val itemWidth = maxWidth / itemCount
                val itemWidthPx = with(density) { itemWidth.toPx() }
                val scope = rememberCoroutineScope()
                // 优化：合并"手指偏移"和"动画 lens 位置"到一个 Animatable。
                // 拖动期间直接 snapTo，0 重组；释放时再 animateTo 目标 index。
                // 之前用 Animatable + 独立 dragOffsetPx 两个 state，拖动每帧双倍 invalidate。
                val lensPosition = remember { Animatable(selectedIndex.toFloat()) }
                var isDragging by remember { mutableStateOf(false) }
                var pendingIndex by remember { mutableStateOf<Int?>(null) }
                val interactions = remember { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val lift = animateFloatAsState(
                    targetValue = if (pressed || isDragging) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.59f, stiffness = Spring.StiffnessMedium),
                    label = "tabGlassLift"
                )
                val settleSpec = spring<Float>(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow
                )
                LaunchedEffect(selectedIndex) {
                    if (!isDragging && pendingIndex == null) {
                        lensPosition.animateTo(selectedIndex.toFloat(), settleSpec)
                    }
                }
                val visualSelectedIndex by remember(selectedIndex, itemCount, lensPosition) {
                    derivedStateOf {
                        when {
                            isDragging -> lensPosition.value.roundToInt()
                            pendingIndex != null -> pendingIndex ?: selectedIndex
                            else -> selectedIndex
                        }
                    }
                }
                fun settleToIndex(index: Int) {
                    pendingIndex = index
                    scope.launch {
                        isDragging = false
                        lensPosition.animateTo(index.toFloat(), settleSpec)
                        pendingIndex = null
                    }
                    navigateToIndex(index)
                }
                fun Modifier.bottomTabDrag(): Modifier = pointerInput(selectedIndex, itemCount, itemWidthPx, rtl) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // 手指落下时，把 lens 位置（可能正在 settle 动画中）"接住"到当前值
                            scope.launch { lensPosition.stop() }
                            isDragging = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                // 直接 snapTo 累计拖动量（无插值），0 重组
                                val maxLow = -selectedIndex * itemWidthPx
                                val maxHigh = (itemCount - 1 - selectedIndex) * itemWidthPx
                                val delta = if (rtl) -dragAmount else dragAmount
                                val newOffset = (lensPosition.value * itemWidthPx - selectedIndex * itemWidthPx + delta)
                                    .coerceIn(maxLow, maxHigh)
                                // 把"偏移 + 选中项索引"换算成"lens 在 [0, itemCount-1] 区间的位置"
                                val target = selectedIndex + newOffset / itemWidthPx
                                lensPosition.snapTo(target.coerceIn(0f, (itemCount - 1).toFloat()))
                            }
                        },
                        onDragEnd = {
                            val targetIndex = lensPosition.value.roundToInt()
                                .coerceIn(0, itemCount - 1)
                            settleToIndex(targetIndex)
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                lensPosition.animateTo(selectedIndex.toFloat(), settleSpec)
                            }
                        }
                    )
                }

                val lensInsetPx = with(density) { 4.dp.toPx() }
                val lensTranslationYPx = with(density) { 7.dp.toPx() }
                val selectionFill = if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.08f)
                val showLens by remember(lift) { derivedStateOf { lift.value > 0.015f } }
                // Sample the existing NavHost source only while lifted; never capture the bar itself.
                val lensMaterial = if (showLens && pageBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = pageBackdrop,
                        shape = { CircleShape },
                        effects = { lens(8.dp.toPx(), 14.dp.toPx(), chromaticAberration = true) },
                        layerBlock = { alpha = lift.value.coerceIn(0f, 1f) }
                    )
                } else Modifier
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(itemWidth - 8.dp)
                        .height(52.dp)
                        .graphicsLayer {
                            val amount = lift.value.coerceAtLeast(0f)
                            val x = itemWidthPx * lensPosition.value + lensInsetPx
                            translationX = if (rtl) -x else x
                            translationY = lensTranslationYPx
                            scaleX = 1f + 0.20f * amount
                            scaleY = 1f + 0.13f * amount
                        }
                        .then(lensMaterial)
                        .drawBehind {
                            val amount = lift.value.coerceIn(0f, 1f)
                            val corners = CornerRadius(size.height / 2f)
                            drawRoundRect(selectionFill, alpha = 1f - 0.74f * amount, cornerRadius = corners)
                            if (amount > 0f) {
                                val edge = 1.dp.toPx()
                                drawRoundRect(
                                    Color.White.copy(alpha = (if (isDark) 0.44f else 0.66f) * amount),
                                    topLeft = Offset(edge / 2, edge / 2),
                                    size = Size(size.width - edge, size.height - edge),
                                    cornerRadius = CornerRadius(corners.x - edge / 2),
                                    style = Stroke(edge)
                                )
                            }
                        }
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .bottomTabDrag(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavItems.forEachIndexed { index, screen ->
                        val selected = visualSelectedIndex == index
                        val baseUnselected = if (isDark) Color.White else Color.Black
                        val textShadow = Shadow(
                            color = if (isDark) Color.Black.copy(0.62f) else Color.White.copy(0.68f),
                            blurRadius = 5f
                        )
                        val iconTint by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.primary else baseUnselected.copy(0.48f),
                            animationSpec = tween(220),
                            label = "tabTint_${screen.route}"
                        )
                        val labelAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.42f,
                            animationSpec = tween(220),
                            label = "tabAlpha_${screen.route}"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("bottom_nav_${screen.route}")
                                .clickable(
                                    interactionSource = interactions,
                                    indication = null
                                ) {
                                    settleToIndex(index)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    screen.icon,
                                    contentDescription = stringResource(screen.titleResId),
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(screen.titleResId),
                                    style = TextStyle(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        shadow = textShadow
                                    ),
                                    color = iconTint.copy(alpha = labelAlpha)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
