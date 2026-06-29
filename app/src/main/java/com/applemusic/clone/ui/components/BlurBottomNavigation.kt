package com.applemusic.clone.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.applemusic.clone.ui.navigation.BottomNavItems
import com.applemusic.clone.ui.navigation.Screen
import com.applemusic.clone.ui.navigation.SubRoutes
import kotlin.math.roundToInt

private fun String?.toBottomRootRoute(): String = when {
    this == Screen.Library.route -> Screen.Library.route
    this == SubRoutes.SONGS -> Screen.Library.route
    this == SubRoutes.ALBUMS -> Screen.Library.route
    this == SubRoutes.ARTISTS -> Screen.Library.route
    this == SubRoutes.PLAYLISTS -> Screen.Library.route
    this == "favorites" -> Screen.Library.route
    this?.startsWith("library/") == true -> Screen.Library.route
    this?.startsWith("playlist/") == true -> Screen.Library.route
    this == Screen.Search.route -> Screen.Search.route
    else -> Screen.Home.route
}

@Composable
fun BlurBottomNavigation(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDark = isSystemInDarkTheme()
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
            cornerRadius = 29.dp,
            blurRadius = 7.dp,
            surfaceAlpha = if (isDark) 0.014f else 0.020f,
            highlightAlpha = if (isDark) 0.36f else 0.46f,
            shadowAlpha = if (isDark) 0.16f else 0.10f
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
                var dragOffsetPx by remember { mutableFloatStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }
                var releasedLensIndex by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(selectedIndex, releasedLensIndex) {
                    if (releasedLensIndex == selectedIndex) {
                        releasedLensIndex = null
                    }
                }
                val dragLensIndex = (selectedIndex + dragOffsetPx / itemWidthPx)
                    .coerceIn(0f, (itemCount - 1).toFloat())
                val settledLensIndex by animateFloatAsState(
                    targetValue = (releasedLensIndex ?: selectedIndex).toFloat(),
                    animationSpec = spring(
                        dampingRatio = 0.58f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "bottomGlassLensIndex"
                )
                val lensIndex = if (isDragging) dragLensIndex else settledLensIndex
                val lensOffsetX = itemWidth * lensIndex
                val lensScaleX by animateFloatAsState(
                    targetValue = if (isDragging) 1.14f else 1f,
                    animationSpec = spring(
                        dampingRatio = 0.56f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "bottomGlassLensScale"
                )
                fun settleToIndex(index: Int) {
                    releasedLensIndex = index
                    navigateToIndex(index)
                }
                fun Modifier.bottomTabDrag(): Modifier = pointerInput(selectedIndex, itemCount, itemWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragOffsetPx = 0f
                            isDragging = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(
                                -selectedIndex * itemWidthPx,
                                (itemCount - 1 - selectedIndex) * itemWidthPx
                            )
                        },
                        onDragEnd = {
                            val targetIndex = (selectedIndex + (dragOffsetPx / itemWidthPx).roundToInt())
                                .coerceIn(0, itemCount - 1)
                            isDragging = false
                            dragOffsetPx = 0f
                            settleToIndex(targetIndex)
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffsetPx = 0f
                        }
                    )
                }

                BackdropLiquidGlass(
                    modifier = Modifier
                        .bottomTabDrag()
                        .offset(x = lensOffsetX + 4.dp, y = 7.dp)
                        .width(itemWidth - 8.dp)
                        .height(52.dp),
                    cornerRadius = 23.dp,
                    blurRadius = if (isDragging) 12.dp else 8.dp,
                    surfaceAlpha = if (isDark) if (isDragging) 0.050f else 0.030f else if (isDragging) 0.070f else 0.044f,
                    highlightAlpha = if (isDark) 0.54f else 0.68f,
                    shadowAlpha = if (isDark) 0.25f else 0.16f,
                    scaleX = lensScaleX,
                    scaleY = if (isDragging) 0.94f else 1f
                ) {}

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavItems.forEach { screen ->
                        val selected = selectedRootRoute == screen.route
                        val baseUnselected = if (isDark) Color.White else Color.Black
                        val textShadow = Shadow(
                            color = if (isDark) Color.Black.copy(0.62f) else Color.White.copy(0.68f),
                            blurRadius = 5f
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.18f else 0.96f,
                            animationSpec = spring(
                                dampingRatio = 0.82f,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "tabScale_${screen.route}"
                        )
                        val lift by animateDpAsState(
                            targetValue = if (selected) (-2).dp else 2.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "tabLift_${screen.route}"
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
                                .clip(RoundedCornerShape(22.dp))
                                .bottomTabDrag()
                                .clickable {
                                    settleToIndex(BottomNavItems.indexOf(screen))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.offset(y = lift),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    screen.icon,
                                    contentDescription = stringResource(screen.titleResId),
                                    tint = iconTint,
                                    modifier = Modifier
                                        .size(if (selected) 27.dp else 24.dp)
                                        .scale(scale)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(screen.titleResId),
                                    style = TextStyle(
                                        fontSize = if (selected) 11.sp else 10.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
