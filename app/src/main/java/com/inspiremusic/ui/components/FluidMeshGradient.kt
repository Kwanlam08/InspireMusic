package com.inspiremusic.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * 流体网格渐变色彩配置。
 */
data class MeshGradientColors(
    val baseBackground: Color = Color(0xFF14131B),
    val primary: Color = Color(0xFF6B3BA7),
    val secondary: Color = Color(0xFFD83A6F),
    val tertiary: Color = Color(0xFF2A5BB5),
    val accent: Color = Color(0xFFE87A36)
)

/**
 * 异步从专辑位图提取 4~5 阶多重调色板。
 */
suspend fun extractMeshColorsFromBitmap(bitmap: Bitmap?): MeshGradientColors {
    if (bitmap == null || bitmap.isRecycled) {
        return MeshGradientColors()
    }
    return withContext(Dispatchers.Default) {
        try {
            // 对缩略图进行取色以保证高速与低内存
            val palette = Palette.from(bitmap)
                .maximumColorCount(16)
                .generate()

            val dominant = palette.dominantSwatch?.rgb?.let { Color(it) }
            val vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) }
            val lightVibrant = palette.lightVibrantSwatch?.rgb?.let { Color(it) }
            val darkVibrant = palette.darkVibrantSwatch?.rgb?.let { Color(it) }
            val muted = palette.mutedSwatch?.rgb?.let { Color(it) }
            val darkMuted = palette.darkMutedSwatch?.rgb?.let { Color(it) }

            // 基础暗色底板：优先暗沉色或主色暗化
            val base = darkMuted
                ?: darkVibrant
                ?: dominant?.copy(alpha = 1f)?.let {
                    Color(
                        red = it.red * 0.25f,
                        green = it.green * 0.25f,
                        blue = it.blue * 0.25f,
                        alpha = 1f
                    )
                }
                ?: Color(0xFF14131B)

            val p = vibrant ?: dominant ?: Color(0xFF6B3BA7)
            val s = lightVibrant ?: muted ?: Color(0xFFD83A6F)
            val t = darkVibrant ?: dominant ?: Color(0xFF2A5BB5)
            val a = palette.lightMutedSwatch?.rgb?.let { Color(it) }
                ?: muted
                ?: Color(0xFFE87A36)

            MeshGradientColors(
                baseBackground = base,
                primary = p,
                secondary = s,
                tertiary = t,
                accent = a
            )
        } catch (_: Exception) {
            MeshGradientColors()
        }
    }
}

/**
 * 动态流体光斑背景组件 (Fluid Mesh Gradient)。
 * 驱动 4 组多重光斑在画布上以正弦调和曲线慢速流动、交融与呼吸。
 */
@Composable
fun FluidMeshGradientBackground(
    bitmap: Bitmap?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    dimMaskAlpha: Float = 0.38f
) {
    var rawColors by remember { mutableStateOf(MeshGradientColors()) }

    LaunchedEffect(bitmap) {
        rawColors = extractMeshColorsFromBitmap(bitmap)
    }

    // 颜色过渡动画（毫秒级丝滑插值，切歌时柔和变色）
    val animDuration = 1200
    val animEasing = FastOutSlowInEasing
    val baseBg by animateColorAsState(
        targetValue = rawColors.baseBackground,
        animationSpec = tween(animDuration, easing = animEasing),
        label = "meshBaseBg"
    )
    val primaryColor by animateColorAsState(
        targetValue = rawColors.primary,
        animationSpec = tween(animDuration, easing = animEasing),
        label = "meshPrimary"
    )
    val secondaryColor by animateColorAsState(
        targetValue = rawColors.secondary,
        animationSpec = tween(animDuration, easing = animEasing),
        label = "meshSecondary"
    )
    val tertiaryColor by animateColorAsState(
        targetValue = rawColors.tertiary,
        animationSpec = tween(animDuration, easing = animEasing),
        label = "meshTertiary"
    )
    val accentColor by animateColorAsState(
        targetValue = rawColors.accent,
        animationSpec = tween(animDuration, easing = animEasing),
        label = "meshAccent"
    )

    // 动态时间驱动（播放时舒缓游弋，暂停时定格/极微动）
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameNanos != 0L) {
                    val dtSeconds = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
                    // 播放时流动速率 0.35f（悠扬舒缓），暂停时衰减至 0.05f 微呼吸
                    val speed = if (isPlaying) 0.35f else 0.05f
                    time += dtSeconds * speed
                }
                lastFrameNanos = frameTimeNanos
            }
        }
    }

    // Rasterize the soft background at half size; the player controls stay at native resolution.
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                // 1. 底层暗色背景
                drawRect(color = baseBg)

                val t = time.toDouble()

                // 2. 光斑 1: 主导色 (Primary) - 上半区左侧游弋
                val c1x = (0.28f + sin(t * 0.75).toFloat() * 0.18f) * w
                val c1y = (0.28f + cos(t * 0.55).toFloat() * 0.14f) * h
                val r1 = (w * 0.85f).coerceAtLeast(50f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.60f),
                            primaryColor.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = Offset(c1x, c1y),
                        radius = r1
                    ),
                    radius = r1,
                    center = Offset(c1x, c1y)
                )

                // 3. 光斑 2: 次主色 (Secondary) - 上半区右侧交织
                val c2x = (0.72f + cos(t * 0.65).toFloat() * 0.20f) * w
                val c2y = (0.35f + sin(t * 0.85).toFloat() * 0.16f) * h
                val r2 = (w * 0.90f).coerceAtLeast(50f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = 0.55f),
                            secondaryColor.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(c2x, c2y),
                        radius = r2
                    ),
                    radius = r2,
                    center = Offset(c2x, c2y)
                )

                // 4. 光斑 3: 第三色 (Tertiary) - 下半区左侧扩展
                val c3x = (0.32f + sin(t * 0.50 + 2.1).toFloat() * 0.22f) * w
                val c3y = (0.72f + cos(t * 0.70 + 1.2).toFloat() * 0.16f) * h
                val r3 = (w * 0.95f).coerceAtLeast(50f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tertiaryColor.copy(alpha = 0.56f),
                            tertiaryColor.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(c3x, c3y),
                        radius = r3
                    ),
                    radius = r3,
                    center = Offset(c3x, c3y)
                )

                // 5. 光斑 4: 点缀色 (Accent) - 下半区右侧微动
                val c4x = (0.68f + cos(t * 0.45 + 3.4).toFloat() * 0.18f) * w
                val c4y = (0.68f + sin(t * 0.60 + 2.5).toFloat() * 0.18f) * h
                val r4 = (w * 0.82f).coerceAtLeast(50f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.48f),
                            accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(c4x, c4y),
                        radius = r4
                    ),
                    radius = r4,
                    center = Offset(c4x, c4y)
                )

                // 6. 顶部到底部的极弱暗调渐变蒙层，强化层次感与文字易读性
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = dimMaskAlpha * 0.6f),
                            Color.Black.copy(alpha = dimMaskAlpha * 0.9f),
                            Color.Black.copy(alpha = dimMaskAlpha * 1.15f)
                        )
                    )
                )
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val rasterWidth = (width + 1) / 2
        val rasterHeight = (height + 1) / 2
        val background = measurables.single().measure(Constraints.fixed(rasterWidth, rasterHeight))
        layout(width, height) {
            background.placeWithLayer(0, 0) {
                compositingStrategy = CompositingStrategy.Offscreen
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = if (rasterWidth > 0) width.toFloat() / rasterWidth else 1f
                scaleY = if (rasterHeight > 0) height.toFloat() / rasterHeight else 1f
            }
        }
    }
}
