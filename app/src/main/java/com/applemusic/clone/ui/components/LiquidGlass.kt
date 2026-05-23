package com.applemusic.clone.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass — 液态玻璃效果容器
 *
 * 参考 Telegram Android 的液态玻璃实现思路：
 *  - 多层半透明渐变背景模拟磨砂玻璃质感
 *  - 顶部高光衬边（Rim Light）
 *  - 微妙的双色渐变边框
 *  - API 31+ 使用 RenderEffect 叠加模糊增强深度
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    isDark: Boolean = true,
    shadowElevation: Dp = 24.dp,
    shadowColor: Color = Color.Black,
    tintColor: Color = Color.Transparent,
    blurIntensity: Float = 24f,          // API 31+ RenderEffect 模糊半径
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    // 基础玻璃颜色（深色 / 浅色自适应）
    // 基础玻璃颜色：大幅提高透明度（根据请求，调整为约 70% 的透度，呈现浓郁毛玻璃感）
    val baseGlass = if (isDark)
        Color(0xFF1C1C1E).copy(alpha = 0.65f)
    else
        Color(0xFFF2F2F7).copy(alpha = 0.65f)

    val shimmerTop = if (isDark)
        Color.White.copy(alpha = 0.08f)
    else
        Color.White.copy(alpha = 0.35f)

    val rimTop = if (isDark)
        Color.White.copy(alpha = 0.20f)
    else
        Color.White.copy(alpha = 0.60f)

    val rimBot = if (isDark)
        Color.White.copy(alpha = 0.05f)
    else
        Color.White.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                spotColor = shadowColor.copy(alpha = 0.35f),
                ambientColor = shadowColor.copy(alpha = 0.12f)
            )
            .clip(shape)

    ) {
        // ① 基础磨砂背景
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            baseGlass.copy(alpha = baseGlass.alpha * 0.95f),
                            baseGlass.copy(alpha = minOf(baseGlass.alpha * 1.08f, 1f))
                        )
                    )
                )
        )

        // ② 彩色/主题色叠加层（可选）
        if (tintColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(tintColor.copy(alpha = 0.08f))
            )
        }

        // ③ 顶部玻璃高光（如光从上方照射）
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(shimmerTop, Color.Transparent),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // ④ 实际内容
        content()

        // ⑤ 玻璃边框（渐变：顶部亮 → 底部暗）
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(rimTop, rimBot)
                    ),
                    shape = shape
                )
        )
    }
}

/**
 * 用于 MiniPlayer / BottomNav 的液态玻璃（加强可见度版）
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    isDark: Boolean = true,
    shadowElevation: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    // 加强不透明度，确保可见
    // 底栏等关键控件表面玻璃质感
    val base = if (isDark)
        Color.Black.copy(alpha = 0.50f)
    else
        Color.White.copy(alpha = 0.60f)

    val blurRadius = 32f

    val shimmer = if (isDark) Color.White.copy(0.18f) else Color.White.copy(0.65f)

    // 外圈边框：深色模式用白边，浅色模式用暗色边（以确保可见）
    val rimTop = if (isDark) Color.White.copy(0.22f) else Color.Black.copy(0.14f)
    val rimBot = if (isDark) Color.White.copy(0.18f) else Color.Black.copy(0.10f)

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                spotColor = Color.Black.copy(0.15f),
                ambientColor = Color.Black.copy(0.05f)
            )
            .clip(shape)

    ) {
        // ① 底色（不透明）
        Box(Modifier.matchParentSize().background(base))

        // ② 顶部光泽 shimmer
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(shimmer, Color.Transparent))
            )
        )

        // 内容
        content()

        // ③ 可见边框：实色渐变边（明显可见的白色线条）
        Box(
            Modifier.matchParentSize()
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(listOf(rimTop, rimBot)),
                    shape = shape
                )
        )
    }
}