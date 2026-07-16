@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package com.inspiremusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiremusic.R
import com.inspiremusic.ui.theme.LocalAppIsDark
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LoadingStateView(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun FloatingGlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    height: Dp = 38.dp,
    tint: Color? = null,
    containerColor: Color? = null,
    cornerRadius: Dp = 16.dp,
    refractive: Boolean = true,
    useSharedBackdrop: Boolean = true,
    ignoreBackdropCompatibility: Boolean = false
) {
    val isDark = LocalAppIsDark.current
    val iconTint = tint ?: if (isDark) Color.White else Color.Black
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "glassButtonPress"
    )
    val requestedSurfaceAlpha = containerColor?.alpha ?: if (isDark) 0.20f else 0.30f

    BackdropLiquidGlass(
        modifier = modifier
            .size(width = width, height = height)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(
                    bounded = true,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                )
            ) { onClick() },
        cornerRadius = cornerRadius,
        blurRadius = if (refractive) 9.dp else 4.dp,
        surfaceAlpha = requestedSurfaceAlpha,
        highlightAlpha = if (isDark) if (refractive) 0.60f else 0.24f else if (refractive) 0.76f else 0.36f,
        shadowAlpha = if (isDark) if (refractive) 0.30f else 0.16f else if (refractive) 0.18f else 0.10f,
        useSharedBackdrop = refractive && useSharedBackdrop,
        ignoreBackdropCompatibility = ignoreBackdropCompatibility,
        surfaceRole = if (refractive && containerColor == null) {
            GlassSurfaceRole.NAVIGATION_CHROME
        } else {
            GlassSurfaceRole.STANDARD
        }
    ) {
        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

val LiquidGlassBottomSheetShape = RoundedCornerShape(30.dp)
val LiquidGlassBottomSheetModifier = Modifier
    .padding(horizontal = 12.dp, vertical = 10.dp)

val LiquidGlassDialogShape = RoundedCornerShape(28.dp)
val LiquidGlassDialogModifier = Modifier.border(
    1.dp,
    Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.48f),
            Color.White.copy(alpha = 0.12f),
            Color.Black.copy(alpha = 0.20f)
        )
    ),
    LiquidGlassDialogShape
)

@Composable
fun liquidGlassBottomSheetColor(): Color {
    val isDark = LocalAppIsDark.current
    return if (isDark) {
        Color(0xFF101014)
    } else {
        Color(0xFFF7F7F9)
    }
}

@Composable
fun liquidGlassDialogColor(): Color {
    val isDark = LocalAppIsDark.current
    return if (isDark) {
        Color(0xFF121216)
    } else {
        Color(0xFFF7F7F9)
    }
}

@Composable
fun LiquidGlassBottomSheetDragHandle() {
    val isDark = LocalAppIsDark.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(width = 44.dp, height = 5.dp),
            shape = RoundedCornerShape(100),
            color = if (isDark) {
                Color.White.copy(alpha = 0.42f)
            } else {
                Color.Black.copy(alpha = 0.24f)
            }
        ) {}
    }
}

@Composable
fun LiquidGlassBottomSheetFrame(
    modifier: Modifier = Modifier,
    useSharedBackdrop: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalAppIsDark.current
    val shape = LiquidGlassBottomSheetShape
    val backgroundColor = liquidGlassBottomSheetColor()
    // Full-screen overlays must not sample the NavHost behind them. In particular,
    // the now-playing menu would otherwise reveal the previous screen through it.
    val parentBackdrop = LocalBackdropLayer.current.takeIf { useSharedBackdrop }
    val sheetBackdrop = rememberLayerBackdrop()
    val edgeBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.16f else 0.72f),
            if (isDark) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.085f),
            if (isDark) Color.Black.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.12f)
        )
    )
    val frameModifier = modifier
        .fillMaxWidth()
        .shadow(
            elevation = 22.dp,
            shape = shape,
            spotColor = Color.Black.copy(alpha = if (isDark) 0.48f else 0.22f),
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.22f else 0.08f)
        )
        .then(
            if (parentBackdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = parentBackdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        lens(
                            refractionHeight = 14.dp.toPx(),
                            refractionAmount = 26.dp.toPx(),
                            chromaticAberration = true
                        )
                    },
                    exportedBackdrop = sheetBackdrop,
                    onDrawSurface = {
                        drawRect(backgroundColor.copy(alpha = if (isDark) 0.32f else 0.46f))
                    }
                )
            } else {
                Modifier.background(backgroundColor, shape)
            }
        )
        .clip(shape)
        .border(1.dp, edgeBrush, shape)

    CompositionLocalProvider(
        LocalBackdropLayer provides if (parentBackdrop != null) sheetBackdrop else null,
        LocalBackdropRenderingEnabled provides (parentBackdrop != null)
    ) {
        Column(modifier = frameModifier) {
            LiquidGlassBottomSheetDragHandle()
            content()
        }
    }
}

@Composable
fun LiquidGlassMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    labelColor: Color? = null
) {
    val isDark = LocalAppIsDark.current
    val resolvedLabelColor = labelColor ?: if (isDark) Color.White else Color.Black

    BackdropLiquidGlass(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        cornerRadius = 22.dp,
        blurRadius = 9.dp,
        surfaceAlpha = if (isDark) 0.026f else 0.040f,
        highlightAlpha = if (isDark) 0.56f else 0.72f,
        shadowAlpha = if (isDark) 0.28f else 0.17f,
        useSharedBackdrop = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackdropLiquidGlass(
                modifier = Modifier.size(34.dp),
                cornerRadius = 13.dp,
                blurRadius = 7.dp,
                surfaceAlpha = if (isDark) 0.022f else 0.030f,
                highlightAlpha = if (isDark) 0.48f else 0.64f,
                shadowAlpha = if (isDark) 0.22f else 0.12f,
                useSharedBackdrop = true
            ) {
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                color = resolvedLabelColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
