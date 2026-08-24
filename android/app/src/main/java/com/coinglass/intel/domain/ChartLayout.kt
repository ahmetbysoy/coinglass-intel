package com.coinglass.intel.domain

import kotlin.math.roundToInt

/**
 * Shared geometry for draw + gestures. Price/heat live in gutters so
 * candles never sit under the axis and hit-tests match pixels.
 */
object ChartLayout {
    data class Geo(
        val width: Float,
        val height: Float,
        val plotLeft: Float,
        val plotWidth: Float,
        val candleTop: Float,
        val candleH: Float,
        val volTop: Float,
        val volH: Float,
        val timeTop: Float,
        val timeH: Float,
        val heatLeft: Float,
        val heatW: Float,
        val priceLeft: Float,
        val priceW: Float,
        val slot: Float,
        val slots: Int,
        val shown: Int,
        val following: Boolean,
        val shift: Float,
    ) {
        fun xCenter(i: Int): Float = plotLeft + shift + slot * i + slot / 2f

        fun inPriceScale(x: Float): Boolean = x >= priceLeft

        fun inPlotX(x: Float): Boolean = x >= plotLeft && x < plotLeft + plotWidth

        fun bodyW(): Float = (slot * 0.62f).coerceIn(1.6f, 12f)

        fun hitIndex(x: Float): Int? {
            if (slots <= 0 || slot <= 0f || !x.isFinite()) return null
            val local = x - plotLeft - shift
            if (local < 0f || local > plotWidth) return null
            return (local / slot).toInt().coerceIn(0, (slots - 1).coerceAtLeast(0))
        }

        fun candleIndex(x: Float, lastIdx: Int): Int? {
            val raw = hitIndex(x) ?: return null
            if (lastIdx < 0) return null
            return raw.coerceAtMost(lastIdx)
        }
    }

    fun geo(
        width: Float,
        height: Float,
        shown: Int,
        following: Boolean,
        showVol: Boolean,
        showHeat: Boolean,
        priceGutter: Float,
        heatGutter: Float,
        timeH: Float,
        shiftBars: Float = 0f,
        pad: Float = 0f,
    ): Geo {
        val w = width.coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)
        val priceW = priceGutter.coerceIn(0f, w * 0.42f)
        val heatW = if (showHeat) heatGutter.coerceIn(0f, w * 0.22f) else 0f
        val plotLeft = pad
        val plotWidth = (w - priceW - heatW - pad).coerceAtLeast(1f)
        val time = timeH.coerceIn(0f, h * 0.25f)
        val volH = if (showVol) ((h - time) * 0.14f).coerceAtLeast(0f) else 0f
        val candleH = (h - volH - time).coerceAtLeast(1f)
        val n = shown.coerceAtLeast(0)
        val slots = ChartViewport.slotCount(n, following).coerceAtLeast(1)
        val slot = plotWidth / slots
        val shift = if (slot.isFinite()) -shiftBars * slot else 0f
        val heatLeft = plotLeft + plotWidth
        val priceLeft = heatLeft + heatW
        return Geo(
            width = w,
            height = h,
            plotLeft = plotLeft,
            plotWidth = plotWidth,
            candleTop = 0f,
            candleH = candleH,
            volTop = candleH,
            volH = volH,
            timeTop = candleH + volH,
            timeH = time,
            heatLeft = heatLeft,
            heatW = heatW,
            priceLeft = priceLeft,
            priceW = priceW,
            slot = slot,
            slots = slots,
            shown = n,
            following = following,
            shift = shift,
        )
    }

    fun timeLabelIndices(n: Int, maxLabels: Int = 4): List<Int> {
        if (n <= 0) return emptyList()
        if (n == 1) return listOf(0)
        val count = maxLabels.coerceIn(2, n)
        val step = (n - 1).toFloat() / (count - 1)
        val out = ArrayList<Int>(count)
        for (i in 0 until count) {
            val idx = (i * step).roundToInt().coerceIn(0, n - 1)
            if (out.lastOrNull() != idx) out.add(idx)
        }
        return out
    }
}
