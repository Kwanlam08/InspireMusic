package com.applemusic.clone.ui.components

import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * 共享的 HazeState，用于跨组件共享"背后内容"快照。
 * 由 AppNavigation 提供（包裹整个 NavHost 的 Box 上挂 .hazeSource），
 * MiniPlayer / BlurBottomNavigation 通过 LiquidGlassSurface 消费。
 */
val LocalHazeState = compositionLocalOf<HazeState> {
    error(
        "HazeState not provided. Wrap your app's root content in " +
        "CompositionLocalProvider(LocalHazeState provides rememberHazeState()) { ... }"
    )
}
