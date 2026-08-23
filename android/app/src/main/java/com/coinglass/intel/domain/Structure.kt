package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.BookSnap
import com.coinglass.intel.domain.model.Candle
import com.coinglass.intel.domain.model.OrderBook
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class StructureLevels(
    val support: Double = 0.0,
    val resistance: Double = 0.0,
    val bidWall: Double = 0.0,
    val askWall: Double = 0.0,
    val poc: Double = 0.0,
)

object Structure {
    /** Volume-weighted value area (POC band). VAL ≈ support, VAH ≈ resistance. */
    fun volumeArea(rows: List<Candle>, bins: Int = 24): Triple<Double, Double, Double> {
        if (rows.size < 8) return Triple(0.0, 0.0, 0.0)
        val s = rows.sortedBy { it.openTime }.takeLast(120)
        val lo = s.minOf { it.low }
        val hi = s.maxOf { it.high }
        if (hi <= lo) return Triple(0.0, 0.0, 0.0)
        val step = (hi - lo) / bins
        val vol = DoubleArray(bins)
        for (c in s) {
            val mid = (c.high + c.low + c.close) / 3.0
            val i = (((mid - lo) / step).toInt()).coerceIn(0, bins - 1)
            vol[i] += c.volume
        }
        val tot = vol.sum()
        if (tot <= 0) return Triple(0.0, 0.0, 0.0)
        val poc = vol.indices.maxBy { vol[it] }
        var acc = vol[poc]
        var a = poc
        var b = poc
        val target = tot * 0.70
        while (acc < target && (a > 0 || b < bins - 1)) {
            val left = if (a > 0) vol[a - 1] else -1.0
            val right = if (b < bins - 1) vol[b + 1] else -1.0
            if (right >= left) {
                b += 1
                acc += vol[b]
            } else {
                a -= 1
                acc += vol[a]
            }
        }
        val vah = lo + (b + 1) * step
        val val_ = lo + a * step
        val pocPx = lo + (poc + 0.5) * step
        return Triple(val_, pocPx, vah)
    }

    fun walls(book: OrderBook?): Pair<Double, Double> {
        if (book == null || book.bids.isEmpty() || book.asks.isEmpty()) return 0.0 to 0.0
        val bid = book.bids.maxByOrNull { it.second }?.first ?: 0.0
        val ask = book.asks.maxByOrNull { it.second }?.first ?: 0.0
        return bid to ask
    }

    fun from(candles: List<Candle>, book: OrderBook?): StructureLevels {
        val (s, poc, r) = volumeArea(candles)
        val (bw, aw) = walls(book)
        return StructureLevels(support = s, resistance = r, bidWall = bw, askWall = aw, poc = poc)
    }

    /**
     * Spoof = a 10x-median wall that vanishes inside [minLife, maxLife] ms.
     * Standing walls that persist do not count.
     */
    fun spoofFromHistory(hist: List<BookSnap>, now: Long = System.currentTimeMillis()): Int {
        if (hist.size < 3) return 0
        var score = 0
        val recent = hist.takeLast(16)
        val first = recent.first()
        val last = recent.last()
        fun vanished(old: List<Pair<Double, Double>>, neu: List<Pair<Double, Double>>, mid: Double): Boolean {
            if (old.isEmpty()) return false
            val med = old.map { it.second }.sorted().let { it[it.size / 2] }.let { if (it == 0.0) 1.0 else it }
            for ((p, q) in old) {
                if (q < med * 10) continue
                if (mid > 0 && abs(p - mid) / mid * 100 < 0.15) continue
                val still = neu.any { abs(it.first - p) / max(p, 1e-9) < 0.0008 && it.second > q * 0.4 }
                val age = last.ts - first.ts
                if (!still && age in 1_500..12_000) return true
            }
            return false
        }
        if (vanished(first.bidWalls, last.bidWalls, last.mid)) score += 40
        if (vanished(first.askWalls, last.askWalls, last.mid)) score += 40
        return min(score, 100)
    }
}
