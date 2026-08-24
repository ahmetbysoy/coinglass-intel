package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Map chart x → candle index. Pure. */
object ChartHit {
    data class Tip(
        val idx: Int,
        val candle: Candle,
        val liqBin: LiqHeat.Bin?,
        val line: String,
    )

    fun index(x: Float, width: Float, n: Int): Int? {
        if (n <= 0 || width <= 0f) return null
        if (x < 0f || x > width) return null
        val slot = width / n
        if (slot <= 0f) return null
        return (x / slot).toInt().coerceIn(0, n - 1)
    }

    /** Crosshair key is openTime, not window index. Missing → not in view. */
    fun indexOfTime(shown: List<Candle>, openTime: Double?): Int {
        if (openTime == null || shown.isEmpty()) return -1
        val i = shown.binarySearchBy(openTime) { it.openTime }
        return if (i >= 0) i else -1
    }

    fun timeOf(shown: List<Candle>, idx: Int): Double? = shown.getOrNull(idx)?.openTime

    /** Pixel y in the candle pane → price. y=0 is hi. */
    fun priceAtY(y: Float, candleH: Float, lo: Double, hi: Double): Double {
        if (!y.isFinite() || candleH <= 0f || !lo.isFinite() || !hi.isFinite()) return hi
        val t = (1.0 - (y / candleH).toDouble()).coerceIn(0.0, 1.0)
        return lo + (hi - lo) * t
    }

    /** Snap to nearest OHLC (LW magnet). */
    fun magnet(c: Candle, target: Double): Double {
        if (!target.isFinite()) return c.close
        var best = c.close
        var bestD = kotlin.math.abs(c.close - target)
        for (p in doubleArrayOf(c.open, c.high, c.low)) {
            val d = kotlin.math.abs(p - target)
            if (d < bestD) {
                bestD = d
                best = p
            }
        }
        return best
    }

    fun timeMs(openTime: Double): Long {
        val t = openTime.toLong()
        return if (t in 1 until 10_000_000_000L) t * 1000L else t
    }

    fun formatTime(openTime: Double): String {
        if (openTime <= 0.0) return "—"
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(timeMs(openTime)))
    }

    fun binAt(price: Double, heat: LiqHeat.Grid): LiqHeat.Bin? {
        if (price <= 0.0 || heat.bins.isEmpty()) return null
        return heat.bins.firstOrNull { price >= it.lo && price < it.hi }
            ?: heat.bins.lastOrNull { price >= it.lo && price <= it.hi }
    }

    fun tip(shown: List<Candle>, idx: Int, heat: LiqHeat.Grid = LiqHeat.Grid()): Tip? {
        val c = shown.getOrNull(idx) ?: return null
        val bin = binAt(c.close, heat)
        val liq = if (bin == null || bin.total <= 0) "liq —"
        else "liq L" + fmtUsd(bin.longUsd) + "/S" + fmtUsd(bin.shortUsd)
        val line = "O " + fmtPrice(c.open) +
            "  H " + fmtPrice(c.high) +
            "  L " + fmtPrice(c.low) +
            "  C " + fmtPrice(c.close) +
            "  " + formatTime(c.openTime) +
            "  " + liq
        return Tip(idx, c, bin, line)
    }
}
