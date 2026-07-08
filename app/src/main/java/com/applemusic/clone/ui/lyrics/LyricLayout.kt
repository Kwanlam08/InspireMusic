package com.applemusic.clone.ui.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameMillis
import com.applemusic.clone.R
import com.applemusic.clone.model.LrcLine

@OptIn(ExperimentalTextApi::class)
@Composable
fun ContinuousLyricsView(
    lyrics: List<LrcLine>,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.lyrics_empty),
                color = Color.White.copy(0.3f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val horizontalPaddingPx = with(density) { 18.dp.toPx() }
    val lineGap = with(density) { 18.dp.toPx() }
    val maxTextWidthPx = with(density) { (screenWidthDp.dp - 36.dp).toPx().toInt().coerceAtLeast(1) }
    val textStyle = TextStyle(
        color = Color.White,
        fontSize = 25.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        lineBreak = LineBreak.Paragraph,
        hyphens = Hyphens.Auto
    )

    val layoutSnapshot = remember(lyrics, maxTextWidthPx, textStyle) {
        var top = 0f
        val measured = lyrics.mapIndexed { index, line ->
            val layout = textMeasurer.measure(
                text = AnnotatedString(line.text),
                style = textStyle,
                constraints = Constraints(maxWidth = maxTextWidthPx)
            )
            val height = layout.size.height.toFloat()
            val item = LyricLineLayout(
                index = index,
                line = line,
                layout = layout,
                top = top,
                center = top + height / 2f,
                height = height
            )
            top += height + lineGap
            item
        }
        LyricLayoutSnapshot(measured, top)
    }

    var framePositionMs by remember { mutableLongStateOf(currentPositionMs) }
    var anchorPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    var anchorFrameMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(currentPositionMs) {
        val drift = kotlin.math.abs(currentPositionMs - framePositionMs)
        if (!isPlaying || drift > 280L) {
            anchorPositionMs = currentPositionMs
            anchorFrameMs = 0L
            framePositionMs = currentPositionMs
        }
    }

    LaunchedEffect(isPlaying) {
        anchorPositionMs = currentPositionMs
        anchorFrameMs = 0L
        framePositionMs = currentPositionMs
        while (true) {
            withFrameMillis { frameMs ->
                if (anchorFrameMs == 0L) anchorFrameMs = frameMs
                framePositionMs = if (isPlaying) {
                    anchorPositionMs + (frameMs - anchorFrameMs)
                } else {
                    anchorPositionMs
                }
            }
        }
    }

    val latestFramePositionMs by rememberUpdatedState(framePositionMs)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 18.dp, bottom = 64.dp)
            .pointerInput(layoutSnapshot) {
                detectTapGestures { offset: Offset ->
                    val focusY = size.height * 0.32f
                    val scrollOffset = calculateScrollOffset(
                        currentPosition = latestFramePositionMs,
                        lyrics = lyrics,
                        layout = layoutSnapshot
                    )
                    val tapped = layoutSnapshot.lines.minByOrNull { line ->
                        kotlin.math.abs((focusY + line.center - scrollOffset) - offset.y)
                    }
                    tapped?.let { onSeek(it.line.timeMs) }
                }
            }
    ) {
        val textWidthPx = maxTextWidthPx.toFloat()
        val focusY = size.height * 0.32f
        val scrollOffset = calculateScrollOffset(
            currentPosition = framePositionMs,
            lyrics = lyrics,
            layout = layoutSnapshot
        )
        drawContinuousLyrics(
            snapshot = layoutSnapshot,
            scrollOffset = scrollOffset,
            centerY = focusY,
            left = horizontalPaddingPx,
            textWidth = textWidthPx
        )
    }
}
