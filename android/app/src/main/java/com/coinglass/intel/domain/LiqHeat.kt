package com.coinglass.intel.domain

import kotlin.math.abs
import kotlin.math.max

data class LiqPrint(
    val price: Double,
    val usd: Double,
    val longSide: Boolean,
)

/** Live liquidations bucketed on the price axis. Not a coin list. */
object LiqHeat {
    data class Bin(
        val lo: Double,
        val hi: Double,
        val mid: Double,
        val longUsd: Double,
        val shortUsd: Double,
    ) {
        val total: Double get() = longUsd + shortUsd
    }

    data class Grid(
        val bins: List<Bin> = emptyList(),
        val maxUsd: Double = 0.0,
        val longTot: Double = 0.0,
        val shortTot: Double = 0.0,
        val lo: Double = 0.0,
        val hi: Double = 0.0,
    ) {
        val empty: Boolean get() = bins.all { it.total <= 0 }
    }

    fun build(
        prints: List<LiqPrint>,
        anchor: Double,
        bins: Int = 24,
        minRangePct: Double = 1.2,
    ): Grid {
        if (anchor <= 0 || bins < 4) return Grid()
        val usable = prints.filter { it.price > 0 && it.usd > 0 }
        val farthest = usable.maxOfOrNull { abs(it.price - anchor) } ?: 0.0
        val half = max(anchor * (minRangePct / 100.0), farthest * 1.05).let { if (it <= 0) anchor * 0.012 else it }
        val lo = anchor - half
        val hi = anchor + half
        val step = (hi - lo) / bins
        if (step <= 0) return Grid()
        val longs = DoubleArray(bins)
        val shorts = DoubleArray(bins)
        for (p in usable) {
            if (p.price < lo || p.price > hi) continue
            val i = (((p.price - lo) / step).toInt()).coerceIn(0, bins - 1)
            if (p.longSide) longs[i] += p.usd else shorts[i] += p.usd
        }
        val out = (0 until bins).map { i ->
            Bin(lo + i * step, lo + (i + 1) * step, lo + (i + 0.5) * step, longs[i], shorts[i])
        }
        return Grid(
            bins = out,
            maxUsd = out.maxOf { it.total }.let { if (it <= 0) 1.0 else it },
            longTot = longs.sum(),
            shortTot = shorts.sum(),
            lo = lo,
            hi = hi,
        )
    }
}
