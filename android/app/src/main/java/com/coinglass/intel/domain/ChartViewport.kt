package com.coinglass.intel.domain

import kotlin.math.roundToInt

/** Visible slice of a 600-bar store. No Compose. */
object ChartViewport {
    const val MIN_BARS = 30
    const val MAX_BARS = 400

    data class Window(val start: Int, val endExclusive: Int) {
        val size: Int get() = (endExclusive - start).coerceAtLeast(0)
    }

    fun window(total: Int, visible: Int, offsetFromEnd: Int): Window {
        if (total <= 0) return Window(0, 0)
        val vis = visible.coerceIn(1, total)
        val end = (total - offsetFromEnd.coerceAtLeast(0)).coerceIn(0, total)
        val start = (end - vis).coerceAtLeast(0)
        return Window(start, end)
    }

    fun zoom(
        visible: Int,
        factor: Float,
        focus01: Float,
        offsetFromEnd: Int,
        total: Int,
    ): Pair<Int, Int> {
        if (total <= 0) return visible to 0
        val old = visible.coerceIn(MIN_BARS, MAX_BARS.coerceAtMost(total).coerceAtLeast(MIN_BARS))
        val cap = MAX_BARS.coerceAtMost(total).coerceAtLeast(MIN_BARS)
        val next = (old / factor).roundToInt().coerceIn(MIN_BARS, cap)
        if (next == old) return old to offsetFromEnd.coerceIn(0, (total - next).coerceAtLeast(0))
        val f = focus01.coerceIn(0f, 1f)
        val focusFromEnd = offsetFromEnd + ((1f - f) * old).roundToInt()
        val newOff = (focusFromEnd - ((1f - f) * next).roundToInt())
            .coerceIn(0, (total - next).coerceAtLeast(0))
        return next to newOff
    }

    fun pan(offsetFromEnd: Int, deltaBars: Int, visible: Int, total: Int): Int =
        (offsetFromEnd + deltaBars).coerceIn(0, (total - visible).coerceAtLeast(0))

    fun holdOnAppend(offsetFromEnd: Int, following: Boolean, added: Int): Int =
        if (!following && added > 0) offsetFromEnd + added else offsetFromEnd
}
