package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle

/** REST seed + live WS merge. Chart paints a window; store keeps 600. */
object ChartSeries {
    const val VISIBLE_BARS = 90
    const val STORE_CAP = 600
    val TFS = listOf("1m", "3m", "5m", "15m")

    fun merge(rest: List<Candle>, live: Collection<Candle>): List<Candle> {
        if (rest.isEmpty() && live.isEmpty()) return emptyList()
        val m = linkedMapOf<Double, Candle>()
        for (c in rest) {
            if (c.openTime > 0) m[c.openTime] = c
        }
        for (c in live) {
            if (c.openTime > 0) m[c.openTime] = c
        }
        val sorted = m.values.sortedBy { it.openTime }
        return if (sorted.size <= STORE_CAP) sorted else sorted.takeLast(STORE_CAP)
    }

    fun visible(candles: List<Candle>, maxBars: Int = VISIBLE_BARS): List<Candle> {
        if (candles.isEmpty()) return emptyList()
        val sorted = if (candles.size < 2) candles else {
            var ordered = true
            for (i in 1 until candles.size) {
                if (candles[i].openTime < candles[i - 1].openTime) {
                    ordered = false
                    break
                }
            }
            if (ordered) candles else candles.sortedBy { it.openTime }
        }
        return if (sorted.size <= maxBars) sorted else sorted.takeLast(maxBars)
    }
}
