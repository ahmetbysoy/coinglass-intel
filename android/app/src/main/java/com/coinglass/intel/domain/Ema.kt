package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle

object Ema {
    fun of(closes: List<Double>, period: Int): List<Double> {
        if (period < 2 || closes.size < period) return emptyList()
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double>(closes.size)
        var prev = closes.take(period).average()
        repeat(period - 1) { out.add(Double.NaN) }
        out.add(prev)
        for (i in period until closes.size) {
            prev = closes[i] * k + prev * (1.0 - k)
            out.add(prev)
        }
        return out
    }

    fun next(prev: Double, close: Double, period: Int): Double {
        val k = 2.0 / (period + 1)
        return close * k + prev * (1.0 - k)
    }
}

/** Incremental EMA over a live candle list. Full rebuild only on reset. */
class EmaCache(private val period: Int) {
    private val values = ArrayList<Double>()
    private var lastSize = 0
    private var lastFirstTime = 0.0

    fun update(candles: List<Candle>): List<Double> {
        if (candles.isEmpty() || period < 2) {
            values.clear()
            lastSize = 0
            lastFirstTime = 0.0
            return emptyList()
        }
        val first = candles.first().openTime
        val needFull = values.isEmpty() ||
            first != lastFirstTime ||
            candles.size < lastSize ||
            candles.size <= period
        if (needFull) {
            values.clear()
            values.addAll(Ema.of(candles.map { it.close }, period))
        } else {
            val start = (lastSize - 1).coerceAtLeast(period)
            for (i in start until candles.size) {
                val prev = values.getOrNull(i - 1)
                if (prev == null || prev.isNaN()) {
                    values.clear()
                    values.addAll(Ema.of(candles.map { it.close }, period))
                    break
                }
                val v = Ema.next(prev, candles[i].close, period)
                if (i < values.size) values[i] = v else values.add(v)
            }
        }
        lastSize = candles.size
        lastFirstTime = first
        return values
    }
}
