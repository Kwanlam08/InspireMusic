package com.inspiremusic.ui.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Synchronized continuous-curvature corner radius tokens across the entire application.
 * Squircle corners look visually tighter than plain arcs, so the values are a bit larger.
 */
object AppCornerTokens {
    val Small: Dp = 14.dp
    val Control: Dp = 20.dp
    val Card: Dp = 28.dp
    val Sheet: Dp = 34.dp
}

/** Default iOS-like corner smoothing (0 = plain arc, 1 = maximum smoothing). */
private const val DefaultSmoothing = 0.6f

/**
 * G2 continuous-curvature rounded rectangle (Figma / iOS style squircle).
 */
fun ContinuousRoundedCornerShape(
    radius: Dp,
    smoothing: Float = DefaultSmoothing
): CornerBasedShape = ContinuousCornerShapeImpl(
    CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius), smoothing
)

fun ContinuousRoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
    smoothing: Float = DefaultSmoothing
): CornerBasedShape = ContinuousCornerShapeImpl(
    CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart), smoothing
)

private class ContinuousCornerShapeImpl(
    topStartSize: CornerSize,
    topEndSize: CornerSize,
    bottomEndSize: CornerSize,
    bottomStartSize: CornerSize,
    val smoothing: Float
) : CornerBasedShape(topStartSize, topEndSize, bottomEndSize, bottomStartSize) {

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = ContinuousCornerShapeImpl(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect.Zero)
        val budget = min(w, h) / 2f
        val ltr = layoutDirection == LayoutDirection.Ltr
        val tl = (if (ltr) topStart else topEnd).coerceIn(0f, budget)
        val tr = (if (ltr) topEnd else topStart).coerceIn(0f, budget)
        val br = (if (ltr) bottomEnd else bottomStart).coerceIn(0f, budget)
        val bl = (if (ltr) bottomStart else bottomEnd).coerceIn(0f, budget)

        // Capsule / circle: smoothing has no room, a plain round rect is exact.
        if (tl >= budget * 0.98f && tr >= budget * 0.98f && br >= budget * 0.98f && bl >= budget * 0.98f) {
            return Outline.Rounded(RoundRect(0f, 0f, w, h, CornerRadius(budget, budget)))
        }
        return Outline.Generic(buildSquirclePath(w, h, tl, tr, br, bl, budget, smoothing.coerceIn(0f, 1f)))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContinuousCornerShapeImpl) return false
        return topStart == other.topStart && topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd && bottomStart == other.bottomStart &&
            smoothing == other.smoothing
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }
}

/** Figma smooth-corner parameters for a single corner. */
private class CornerParams(
    val p: Float,
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val arcLen: Float,
    val radius: Float,
    val arcMeasure: Float
)

private fun toRad(deg: Float): Float = (deg * Math.PI / 180.0).toFloat()

private fun cornerParams(radius: Float, smoothingIn: Float, budget: Float): CornerParams? {
    if (radius <= 0.5f) return null
    // Shrink smoothing when the corner does not have enough room (same as figma-squircle).
    val maxSmoothing = budget / radius - 1f
    val smoothing = min(smoothingIn, maxSmoothing).coerceAtLeast(0f)
    val p = min((1f + smoothing) * radius, budget)

    val arcMeasure = 90f * (1f - smoothing)
    val arcLen = sin(toRad(arcMeasure / 2f)) * radius * sqrt(2f)
    val angleAlpha = (90f - arcMeasure) / 2f
    val p3ToP4 = radius * tan(toRad(angleAlpha / 2f))
    val angleBeta = 45f * smoothing
    val c = p3ToP4 * cos(toRad(angleBeta))
    val d = c * tan(toRad(angleBeta))
    val b = ((p - arcLen - c - d) / 3f).coerceAtLeast(0f)
    val a = 2f * b
    return CornerParams(p, a, b, c, d, arcLen, radius, arcMeasure)
}

/**
 * Draws one corner. The corner is described in a local frame:
 * [sx],[sy] = point on the incoming edge where the corner starts,
 * ([ux],[uy]) = direction along the incoming edge, ([vx],[vy]) = direction along the outgoing edge.
 * All corners are traversed clockwise (screen coordinates, y down).
 */
private fun Path.smoothCorner(
    cp: CornerParams,
    sx: Float, sy: Float,
    ux: Float, uy: Float,
    vx: Float, vy: Float
) {
    fun px(x: Float, y: Float) = sx + x * ux + y * vx
    fun py(x: Float, y: Float) = sy + x * uy + y * vy

    val a = cp.a; val b = cp.b; val c = cp.c; val d = cp.d; val l = cp.arcLen; val r = cp.radius

    // 1) Entry cubic: straight edge → start of the arc
    val p1x = a + b + c
    val p1y = d
    cubicTo(px(a, 0f), py(a, 0f), px(a + b, 0f), py(a + b, 0f), px(p1x, p1y), py(p1x, p1y))

    // 2) True circular arc around the corner diagonal
    val cxL = cp.p - r
    val cyL = r
    val cx = px(cxL, cyL)
    val cy = py(cxL, cyL)
    val startX = px(p1x, p1y) - cx
    val startY = py(p1x, p1y) - cy
    val startAngle = Math.toDegrees(atan2(startY, startX).toDouble()).toFloat()
    arcTo(
        rect = Rect(cx - r, cy - r, cx + r, cy + r),
        startAngleDegrees = startAngle,
        sweepAngleDegrees = cp.arcMeasure,
        forceMoveTo = false
    )

    // 3) Exit cubic: end of the arc → next straight edge
    val p2x = p1x + l
    val p2y = p1y + l
    cubicTo(
        px(p2x + d, p2y + c), py(p2x + d, p2y + c),
        px(p2x + d, p2y + b + c), py(p2x + d, p2y + b + c),
        px(p2x + d, p2y + a + b + c), py(p2x + d, p2y + a + b + c)
    )
}

private fun buildSquirclePath(
    w: Float,
    h: Float,
    tl: Float,
    tr: Float,
    br: Float,
    bl: Float,
    budget: Float,
    smoothing: Float
): Path {
    val path = Path()
    val tlP = cornerParams(tl, smoothing, budget)
    val trP = cornerParams(tr, smoothing, budget)
    val brP = cornerParams(br, smoothing, budget)
    val blP = cornerParams(bl, smoothing, budget)

    path.moveTo(tlP?.p ?: 0f, 0f)

    // Top-right
    if (trP == null) path.lineTo(w, 0f) else {
        path.lineTo(w - trP.p, 0f)
        path.smoothCorner(trP, w - trP.p, 0f, 1f, 0f, 0f, 1f)
    }
    // Bottom-right
    if (brP == null) path.lineTo(w, h) else {
        path.lineTo(w, h - brP.p)
        path.smoothCorner(brP, w, h - brP.p, 0f, 1f, -1f, 0f)
    }
    // Bottom-left
    if (blP == null) path.lineTo(0f, h) else {
        path.lineTo(blP.p, h)
        path.smoothCorner(blP, blP.p, h, -1f, 0f, 0f, -1f)
    }
    // Top-left
    if (tlP == null) path.lineTo(0f, 0f) else {
        path.lineTo(0f, tlP.p)
        path.smoothCorner(tlP, 0f, tlP.p, 0f, -1f, 1f, 0f)
    }
    path.close()
    return path
}
