package com.coinglass.intel.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

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

    data class Cluster(
        val index: Int,
        val price: Double,
        val usd: Double,
        val longDominant: Boolean,
    )

    data class Stats(
        val pullUp: Double = 0.0,
        val pullDown: Double = 0.0,
        val clusters: List<Cluster> = emptyList(),
    ) {
        /** 0..1 — 0.5 nötr, >0.5 yukarı (short-liq / above-mark) çekimi. */
        val upBias: Float
            get() {
                val s = pullUp + pullDown
                return if (s <= 0.0) 0.5f else (pullUp / s).toFloat()
            }
    }

    /** USD / sqrt(distance) — closer fat bins pull harder. */
    fun stats(grid: Grid, mark: Double, top: Int = 3): Stats {
        val n = grid.bins.size
        if (n == 0 || grid.hi <= grid.lo) return Stats()
        val step = (grid.hi - grid.lo) / n
        var up = 0.0
        var down = 0.0
        val all = ArrayList<Cluster>(n)
        grid.bins.forEachIndexed { i, b ->
            val tot = b.total
            if (tot <= 0.0) return@forEachIndexed
            val price = b.mid
            val pull = tot / sqrt(abs(price - mark).coerceAtLeast(step))
            if (price > mark) up += pull else down += pull
            all += Cluster(i, price, tot, b.longUsd >= b.shortUsd)
        }
        return Stats(up, down, all.sortedByDescending { it.usd }.take(top.coerceAtLeast(0)))
    }

    /** Canvas y=0 is hi (last bin). */
    fun binIndexAt(y: Float, height: Float, n: Int): Int {
        if (n <= 0 || height <= 0f || !y.isFinite()) return -1
        val row = (y / (height / n)).toInt().coerceIn(0, n - 1)
        return n - 1 - row
    }

    /** 0..1 in range. <0 below lo, >1 above hi. NaN if collapsed. */
    fun markT(mark: Double, lo: Double, hi: Double): Double {
        if (hi <= lo || !mark.isFinite()) return Double.NaN
        return (mark - lo) / (hi - lo)
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
