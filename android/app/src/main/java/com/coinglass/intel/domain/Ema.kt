package com.coinglass.intel.domain

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
}
