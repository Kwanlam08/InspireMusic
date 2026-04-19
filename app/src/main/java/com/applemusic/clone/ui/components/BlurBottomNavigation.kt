package com.applemusic.clone.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.applemusic.clone.ui.navigation.BottomNavItems

/**
 * Liquid Glass 悬浮药丸型底部导航栏
 * 参考 Telegram Android 的液态玻璃风格，每个 Tab 有弹跳选中动画。
 */
@Composable
fun BlurBottomNavigation(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDark = isSystemInDarkTheme()

    // 底部安全区
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            cornerRadius = 14.dp,
            isDark = isDark,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route

                    // 选中弹跳动画
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.08f else 1.00f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "tabScale_${screen.route}"
                    )

                    // 动态根据主题设置未选中主颜色
                    val baseUnselected = if (isDark) Color.White else Color.Black

                    // 给文本加上描边/阴影效果（根据用户需求加的边框）
                    val textShadow = Shadow(
                        color = if (isDark) Color.Black.copy(0.6f) else Color.White.copy(0.6f),
                        blurRadius = 6f
                    )

                    val iconTint by animateColorAsState(
                        targetValue = if (selected) Color(0xFFFF375F) else baseUnselected.copy(0.45f),
                        animationSpec = tween(220),
                        label = "tabTint_${screen.route}"
                    )

                    val labelAlpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.5f,
                        animationSpec = tween(220),
                        label = "tabAlpha_${screen.route}"
                    )

                    NavigationBarItem(
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.title,
                                tint = iconTint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(scale)
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = TextStyle(
                                    fontSize = 11.sp, // 稍微加大一点
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    shadow = textShadow
                                ),
                                color = iconTint.copy(alpha = labelAlpha)
                            )
                        },
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFF375F),
                            selectedTextColor = Color(0xFFFF375F),
                            unselectedIconColor = baseUnselected.copy(0.45f),
                            unselectedTextColor = baseUnselected.copy(0.45f),
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}
