package com.applemusic.clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * 真实的高斯模糊毛玻璃面板。
 *
 * 实现机制:
 *  - 父级 (NavHost) 通过 .hazeSource(state) 捕获"背后内容"快照。
 *  - 这里通过 LocalHazeState 拿到同一个 HazeState，调用 hazeChild
 *    把底栏 / MiniPlayer 区域渲染成"对背后内容做高斯模糊"的效果。
 *  - 底层走 RenderEffect (API 31+)，低版本走 Haze 内置 software fallback。
 *
 * 调参目标 (让它看起来像真的玻璃、不是一层遮罩):
 *  - blurRadius 要够大 (>= 40dp)，否则远处色块糊不开。
 *  - tint 必须留出透光空间 (alpha < 0.20)，让背后的真实颜色透出来，
 *    否则就只是加了一层黑/白蒙版。
 *  - 顶部 1px 高光描边 + 内部顶部斜向高光是 iOS 液态玻璃的灵魂。
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    isDark: Boolean = true,
    shadowElevation: Dp = 16.dp,
    blurRadius: Dp = 70.dp,
    tintAlpha: Float = 0.035f,    // 极低 tint，让背后内容透出来（液态玻璃感）
    borderAlpha: Float = 0.18f,   // 低调边框 alpha（用户要求"一定要低调"）
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val hazeState = LocalHazeState.current

    // 1) 底色：透光为主，玻璃感为辅
    val tintColor = if (isDark) Color.Black.copy(alpha = tintAlpha)
                    else Color.White.copy(alpha = tintAlpha)

    // 2) 玻璃高光（顶部微亮，模拟顶光反射，alpha 调低更低调）
    val highlightTop = if (isDark) Color.White.copy(alpha = 0.14f)
                       else Color.White.copy(alpha = 0.55f)
    val highlightBottom = Color.Transparent

    // 3) 边缘描边（亮顶、暗中间、亮底 → 真实玻璃厚度感，但整体压到 ~18% 以下）
    val rimTop = if (isDark) Color.White.copy(alpha = borderAlpha)
                 else Color.Black.copy(alpha = borderAlpha * 0.45f)
    val rimMid = if (isDark) Color.White.copy(alpha = borderAlpha * 0.25f)
                 else Color.Black.copy(alpha = borderAlpha * 0.18f)
    val rimBot = if (isDark) Color.White.copy(alpha = borderAlpha * 0.7f)
                 else Color.Black.copy(alpha = borderAlpha * 0.35f)

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.25f),
                ambientColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(shape)
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = tintColor,
                    tint = HazeTint(tintColor),
                    blurRadius = blurRadius,
                    noiseFactor = 0.10f
                )
            )
    ) {
        // 玻璃面板"顶部高光"叠层 — 模拟太阳光照在玻璃边缘
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(highlightTop, highlightBottom),
                        startY = 0f,
                        endY = 80f
                    )
                )
        )

        // 边缘描边 — 1px 渐变亮边
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(rimTop, rimMid, rimBot)
                    ),
                    shape = shape
                )
        )

        content()
    }
}
