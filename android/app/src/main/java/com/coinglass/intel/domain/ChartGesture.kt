package com.coinglass.intel.domain

import kotlin.math.abs
import kotlin.math.hypot

/** Pure classification for chart pointer math. No Compose. */
object ChartGesture {
    const val FLING_DECAY = 0.90f
    const val FLING_MIN_BARS = 0.18f
    const val FLING_MIN_PX_PER_SEC = 350f
    const val ZOOM_COMMIT = 1.06f
    const val DOUBLE_TAP_MS = 280L

    enum class Drag { PAN, PRICE_ZOOM }

    fun pastSlop(dx: Float, dy: Float, slop: Float): Boolean =
        hypot(dx.toDouble(), dy.toDouble()) >= slop

    /** Horizontal (or vertical on the plot) = time pan. Vertical on the price gutter = y-scale. */
    fun dragKind(dx: Float, dy: Float, startX: Float, scaleLeft: Float): Drag {
        val vertical = abs(dy) > abs(dx)
        return if (vertical && startX >= scaleLeft) Drag.PRICE_ZOOM else Drag.PAN
    }

    fun isDoubleTap(now: Long, last: Long, window: Long = DOUBLE_TAP_MS): Boolean =
        last > 0L && now - last in 0..window

    fun flingBarsPerFrame(vxPxPerSec: Float, slotPx: Float, fps: Float = 60f): Float {
        if (!vxPxPerSec.isFinite() || !slotPx.isFinite() || slotPx <= 0f || fps <= 0f) return 0f
        return vxPxPerSec / slotPx / fps
    }

    fun shouldFling(vxPxPerSec: Float): Boolean =
        vxPxPerSec.isFinite() && abs(vxPxPerSec) >= FLING_MIN_PX_PER_SEC

    fun decay(v: Float): Float = v * FLING_DECAY

    fun flingDone(v: Float): Boolean = !v.isFinite() || abs(v) < FLING_MIN_BARS
}
