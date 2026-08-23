package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import kotlin.math.abs
import kotlin.math.max

/** Smart-money map. Pure. Not a JS port. */
object Smc {
    const val BOOST = 8
    const val OB_LOOKBACK = 80
    const val OB_IMPULSE_MULT = 1.6
    const val OB_MAX = 6
    const val FVG_LOOKBACK = 80
    const val SWEEP_LOOKBACK = 20
    const val EQUAL_TOL = 0.0008

    data class Zone(
        val kind: String,
        val side: String,
        val low: Double,
        val high: Double,
        val startIdx: Int,
        val endIdx: Int,
        val touchIdx: Int? = null,
    ) {
        val touched: Boolean get() = touchIdx != null
    }

    data class Report(
        val obs: List<Zone> = emptyList(),
        val fvgs: List<Zone> = emptyList(),
        val sweeps: List<Zone> = emptyList(),
    ) {
        fun alignedUntouched(direction: String): Boolean {
            val side = when {
                "BULL" in direction -> "bull"
                "BEAR" in direction -> "bear"
                else -> return false
            }
            return obs.any { !it.touched && it.side == side } ||
                fvgs.any { !it.touched && it.side == side }
        }
    }

    fun analyze(candles: List<Candle>): Report {
        if (candles.size < 3) return Report()
        val bars = ensureSorted(candles)
        return Report(
            obs = orderBlocks(bars),
            fvgs = fairValueGaps(bars),
            sweeps = sweeps(bars),
        )
    }

    internal fun orderBlocks(bars: List<Candle>): List<Zone> {
        val n = bars.size
        val from = max(0, n - OB_LOOKBACK)
        val win = bars.subList(from, n)
        if (win.size < 3) return emptyList()
        val bodies = win.map { abs(it.close - it.open) }
        val med = median(bodies)
        if (med <= 0.0) return emptyList()
        val thresh = med * OB_IMPULSE_MULT
        val found = mutableListOf<Zone>()
        for (k in 1 until win.size) {
            val body = abs(win[k].close - win[k].open)
            if (body <= thresh) continue
            val bullImp = win[k].close > win[k].open
            var opp = -1
            for (j in k - 1 downTo 0) {
                val up = win[j].close > win[j].open
                val dn = win[j].close < win[j].open
                if (bullImp && dn) { opp = j; break }
                if (!bullImp && up) { opp = j; break }
            }
            if (opp < 0) continue
            val c = win[opp]
            val absIdx = from + opp
            val impIdx = from + k
            val touch = firstOverlap(bars, absIdx + 1, n, c.low, c.high, after = impIdx)
            found += Zone(
                kind = "ob",
                side = if (bullImp) "bull" else "bear",
                low = minOf(c.low, c.high),
                high = maxOf(c.low, c.high),
                startIdx = absIdx,
                endIdx = absIdx,
                touchIdx = touch,
            )
        }
        return found.takeLast(OB_MAX)
    }

    internal fun fairValueGaps(bars: List<Candle>): List<Zone> {
        val n = bars.size
        val from = max(0, n - FVG_LOOKBACK)
        val out = mutableListOf<Zone>()
        for (i in max(from + 2, 2) until n) {
            val left = bars[i - 2]
            val right = bars[i]
            when {
                right.low > left.high -> {
                    val lo = left.high
                    val hi = right.low
                    out += Zone(
                        kind = "fvg",
                        side = "bull",
                        low = lo,
                        high = hi,
                        startIdx = i - 2,
                        endIdx = i,
                        touchIdx = firstOverlap(bars, i + 1, n, lo, hi),
                    )
                }
                right.high < left.low -> {
                    val lo = right.high
                    val hi = left.low
                    out += Zone(
                        kind = "fvg",
                        side = "bear",
                        low = lo,
                        high = hi,
                        startIdx = i - 2,
                        endIdx = i,
                        touchIdx = firstOverlap(bars, i + 1, n, lo, hi),
                    )
                }
            }
        }
        return out
    }

    internal fun sweeps(bars: List<Candle>): List<Zone> {
        val n = bars.size
        val from = max(0, n - SWEEP_LOOKBACK)
        if (n - from < 3) return emptyList()
        val out = mutableListOf<Zone>()
        val seen = mutableSetOf<Int>()
        for (i in from until n) {
            val hi = bars[i].high
            if (hi <= 0) continue
            val peers = (from until n).filter { j ->
                val h = bars[j].high
                h > 0 && abs(h - hi) / hi <= EQUAL_TOL
            }
            if (peers.size < 2) continue
            val lastPeer = peers.maxOrNull() ?: continue
            val level = peers.maxOf { bars[it].high }
            for (k in lastPeer + 1 until n) {
                val c = bars[k]
                if (c.high > level && c.close <= level && k !in seen) {
                    seen += k
                    out += Zone(
                        kind = "sweep",
                        side = "bear",
                        low = level,
                        high = c.high,
                        startIdx = lastPeer,
                        endIdx = k,
                    )
                    break
                }
            }
        }
        for (i in from until n) {
            val lo = bars[i].low
            if (lo <= 0) continue
            val peers = (from until n).filter { j ->
                val l = bars[j].low
                l > 0 && abs(l - lo) / lo <= EQUAL_TOL
            }
            if (peers.size < 2) continue
            val lastPeer = peers.maxOrNull() ?: continue
            val level = peers.minOf { bars[it].low }
            for (k in lastPeer + 1 until n) {
                val c = bars[k]
                if (c.low < level && c.close >= level && k !in seen) {
                    seen += k
                    out += Zone(
                        kind = "sweep",
                        side = "bull",
                        low = c.low,
                        high = level,
                        startIdx = lastPeer,
                        endIdx = k,
                    )
                    break
                }
            }
        }
        return listOfNotNull(
            out.filter { it.side == "bear" }.maxByOrNull { it.endIdx },
            out.filter { it.side == "bull" }.maxByOrNull { it.endIdx },
        )
    }

    private fun firstOverlap(
        bars: List<Candle>,
        start: Int,
        end: Int,
        lo: Double,
        hi: Double,
        after: Int = start - 1,
    ): Int? {
        val from = max(start, after + 1)
        for (i in from until end) {
            val c = bars[i]
            if (c.low <= hi && c.high >= lo) return i
        }
        return null
    }

    private fun ensureSorted(candles: List<Candle>): List<Candle> {
        if (candles.size < 2) return candles
        for (i in 1 until candles.size) {
            if (candles[i].openTime < candles[i - 1].openTime) {
                return candles.sortedBy { it.openTime }
            }
        }
        return candles
    }

    private fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2.0 else s[m]
    }
}
