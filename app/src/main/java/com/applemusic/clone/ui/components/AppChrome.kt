package com.applemusic.clone.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

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
