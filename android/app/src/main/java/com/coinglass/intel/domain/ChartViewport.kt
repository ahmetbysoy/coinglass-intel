package com.coinglass.intel.domain

import kotlin.math.roundToInt

/** Visible slice of a 600-bar store. No Compose. */
object ChartViewport {
    const val MIN_BARS = 30
    const val MAX_BARS = 400
    const val RIGHT_PAD = 5

    fun slotCount(shown: Int, following: Boolean): Int =
        shown + if (following) RIGHT_PAD else 0

    fun maxOffset(total: Int, visible: Int): Int = (total - visible).coerceAtLeast(0)

    data class Window(val start: Int, val endExclusive: Int) {
        val size: Int get() = (endExclusive - start).coerceAtLeast(0)
    }

    data class PanAcc(val bars: Int, val remain: Float)

    data class ZoomAcc(val visible: Int, val offset: Int, val remain: Float)

    fun window(total: Int, visible: Int, offsetFromEnd: Int): Window {
        if (total <= 0) return Window(0, 0)
        val vis = visible.coerceIn(1, total)
        val end = (total - offsetFromEnd.coerceAtLeast(0)).coerceIn(0, total)
        val start = (end - vis).coerceAtLeast(0)
        return Window(start, end)
    }

    /** Finger right (dx>0) → older bars (offsetFromEnd++). Remainder keeps sub-bar drag. */
    fun pixelsToBars(dxPx: Float, slotPx: Float, remain: Float): PanAcc {
        if (!dxPx.isFinite() || !slotPx.isFinite() || slotPx <= 0f || !remain.isFinite()) {
            return PanAcc(0, 0f)
        }
        val acc = remain + dxPx / slotPx
        if (!acc.isFinite()) return PanAcc(0, 0f)
        val bars = acc.toInt()
        return PanAcc(bars, acc - bars)
    }

    fun zoom(
        visible: Int,
        factor: Float,
        focus01: Float,
        offsetFromEnd: Int,
        total: Int,
    ): Pair<Int, Int> {
        if (total <= 0) return visible to 0
        if (!factor.isFinite() || factor <= 0f) {
            return visible to offsetFromEnd.coerceIn(0, maxOffset(total, visible))
        }
        val cap = MAX_BARS.coerceAtMost(total).coerceAtLeast(MIN_BARS)
        val old = visible.coerceIn(MIN_BARS.coerceAtMost(cap), cap)
        val next = (old / factor).roundToInt().coerceIn(MIN_BARS.coerceAtMost(cap), cap)
        if (next == old) return old to offsetFromEnd.coerceIn(0, maxOffset(total, next))
        val f = focus01.coerceIn(0f, 1f)
        val focusFromEnd = offsetFromEnd + ((1f - f) * old).roundToInt()
        val newOff = (focusFromEnd - ((1f - f) * next).roundToInt())
            .coerceIn(0, maxOffset(total, next))
        return next to newOff
    }

    /**
     * Tiny pinch steps (1.01×) must not round back to the same bar count.
     * Accumulate until |ln(acc)| crosses [commit].
     */
    fun zoomAccum(
        visible: Int,
        remain: Float,
        factor: Float,
        focus01: Float,
        offsetFromEnd: Int,
        total: Int,
        commit: Float = ChartGesture.ZOOM_COMMIT,
    ): ZoomAcc {
        if (!factor.isFinite() || factor <= 0f) return ZoomAcc(visible, offsetFromEnd, remain)
        val base = if (remain.isFinite() && remain > 0f) remain else 1f
        val next = base * factor
        if (!next.isFinite() || next <= 0f) return ZoomAcc(visible, offsetFromEnd, 1f)
        if (next < commit && next > 1f / commit) return ZoomAcc(visible, offsetFromEnd, next)
        val (v, o) = zoom(visible, next, focus01, offsetFromEnd, total)
        return ZoomAcc(v, o, 1f)
    }

    fun pan(offsetFromEnd: Int, deltaBars: Int, visible: Int, total: Int): Int =
        (offsetFromEnd + deltaBars).coerceIn(0, maxOffset(total, visible))

    fun holdOnAppend(offsetFromEnd: Int, following: Boolean, added: Int): Int =
        if (!following && added > 0) offsetFromEnd + added else offsetFromEnd
}
