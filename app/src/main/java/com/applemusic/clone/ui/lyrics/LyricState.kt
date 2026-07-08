package com.applemusic.clone.ui.lyrics

import androidx.compose.ui.text.TextLayoutResult
import com.applemusic.clone.model.LrcLine

data class LyricLineLayout(
    val index: Int,
    val line: LrcLine,
    val layout: TextLayoutResult,
    val top: Float,
    val center: Float,
    val height: Float
)

data class LyricLayoutSnapshot(
    val lines: List<LyricLineLayout>,
    val contentHeight: Float
)
