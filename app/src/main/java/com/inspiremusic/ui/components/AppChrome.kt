package com.inspiremusic.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

class AppChromeController internal constructor(
    private val updateVisibility: (Boolean) -> Unit
) {
    fun setVisible(visible: Boolean): Unit {
        updateVisibility(visible)
    }
}

val LocalAppChromeController = staticCompositionLocalOf<AppChromeController> {
    error("AppChromeController is not provided")
}

internal val LocalAppChromeHeight = staticCompositionLocalOf<State<Int>> { mutableIntStateOf(0) }

/** Space after scrolling content; the viewport still extends behind the glass. */
@Composable
internal fun appChromeContentPadding(): PaddingValues {
    val density = LocalDensity.current
    val obscuredBottom = maxOf(
        LocalAppChromeHeight.current.value,
        WindowInsets.navigationBars.getBottom(density),
        WindowInsets.ime.getBottom(density)
    )
    return PaddingValues(bottom = with(density) { obscuredBottom.toDp() } + 12.dp)
}
