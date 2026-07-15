package com.inspiremusic.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.inspiremusic.settings.AccentColorStyle
import com.inspiremusic.settings.ThemeMode

/** Resolved app theme. Components must use this instead of the system theme directly. */
val LocalAppIsDark = staticCompositionLocalOf { false }

private fun darkAccentColorScheme(style: AccentColorStyle) = darkColorScheme(
    primary = when (style) {
        AccentColorStyle.APPLE_RED -> AppleRedDark
        AccentColorStyle.ANDROID_BLUE -> AndroidBlueDark
    },
    secondary = when (style) {
        AccentColorStyle.APPLE_RED -> AppleRedSecondaryDark
        AccentColorStyle.ANDROID_BLUE -> AndroidBlueSecondaryDark
    },
    tertiary = when (style) {
        AccentColorStyle.APPLE_RED -> AppleRedDark
        AccentColorStyle.ANDROID_BLUE -> AndroidBlueDark
    },
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = DarkText,
    onSurface = DarkText
)

private fun lightAccentColorScheme(style: AccentColorStyle) = lightColorScheme(
    primary = when (style) {
        AccentColorStyle.APPLE_RED -> AppleRed
        AccentColorStyle.ANDROID_BLUE -> AndroidBlue
    },
    secondary = when (style) {
        AccentColorStyle.APPLE_RED -> AppleRedSecondary
        AccentColorStyle.ANDROID_BLUE -> AndroidBlueSecondary
    },
    tertiary = when (style) {
        AccentColorStyle.APPLE_RED -> AppleRed
        AccentColorStyle.ANDROID_BLUE -> AndroidBlue
    },
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightText,
    onSurface = LightText
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private object NoRippleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node = object : Modifier.Node() {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0
}

@Composable
fun InspireMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
    accentColorStyle: AccentColorStyle = AccentColorStyle.APPLE_RED,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val baseColorScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkAccentColorScheme(accentColorStyle) else lightAccentColorScheme(accentColorStyle)
    }
    val colorScheme = baseColorScheme.copy(
        background = if (darkTheme) DarkBackground else LightBackground,
        surface = if (darkTheme) DarkSurface else LightSurface,
        onBackground = if (darkTheme) DarkText else LightText,
        onSurface = if (darkTheme) DarkText else LightText
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes
    ) {
        CompositionLocalProvider(
            LocalIndication provides NoRippleIndication,
            LocalAppIsDark provides darkTheme
        ) {
            content()
        }
    }
}
