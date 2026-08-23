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
)

object Structure {
    /** Last swing low / high from 1h (or 5m) candles. */
    fun swings(rows: List<Candle>): Pair<Double, Double> {
        if (rows.size < 6) return 0.0 to 0.0
        val s = rows.sortedBy { it.openTime }
        var sup = 0.0
        var res = 0.0
        for (i in 2 until s.size - 2) {
            val lo = s[i].low
            val hi = s[i].high
            if (lo <= s[i - 1].low && lo <= s[i - 2].low && lo <= s[i + 1].low && lo <= s[i + 2].low) {
                sup = lo
            }
            if (hi >= s[i - 1].high && hi >= s[i - 2].high && hi >= s[i + 1].high && hi >= s[i + 2].high) {
                res = hi
            }
        }
        return sup to res
    }

    fun walls(book: OrderBook?): Pair<Double, Double> {
        if (book == null || book.bids.isEmpty() || book.asks.isEmpty()) return 0.0 to 0.0
        val bid = book.bids.maxByOrNull { it.second }?.first ?: 0.0
        val ask = book.asks.maxByOrNull { it.second }?.first ?: 0.0
        return bid to ask
    }

    fun from(candles: List<Candle>, book: OrderBook?): StructureLevels {
        val (s, r) = swings(candles)
        val (bw, aw) = walls(book)
        return StructureLevels(s, r, bw, aw)
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
