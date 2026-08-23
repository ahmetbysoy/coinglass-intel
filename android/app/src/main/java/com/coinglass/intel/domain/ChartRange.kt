package com.coinglass.intel.domain

import kotlin.math.abs

/** Visible y-scale. Far S/R or spoof walls must not squash candles. */
object ChartRange {
    const val IN_VIEW = 1.5

    fun bounds(lows: Double, highs: Double, extras: List<Double>): Pair<Double, Double> {
        var lo = lows
        var hi = highs
        if (hi < lo) {
            val t = lo
            lo = hi
            hi = t
        }
        if (hi <= lo) return lo to lo + 1.0
        val mid = (lo + hi) / 2.0
        val lim = (hi - lo) * IN_VIEW
        for (p in extras) {
            if (p <= 0.0) continue
            if (abs(p - mid) > lim) continue
            if (p < lo) lo = p
            if (p > hi) hi = p
        }
        val pad = (hi - lo) * 0.04
        return (lo - pad) to (hi + pad)
    }

    fun inView(px: Double, lo: Double, hi: Double): Boolean =
        px > 0.0 && px >= lo && px <= hi
}
