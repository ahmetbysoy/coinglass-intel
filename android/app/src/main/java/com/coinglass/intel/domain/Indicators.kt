package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object Indicators {
    private fun ema(data: List<Double>, span: Int): List<Double> {
        if (data.isEmpty()) return emptyList()
        val alpha = 2.0 / (span + 1)
        val out = mutableListOf(data.first())
        for (i in 1 until data.size) {
            out += data[i] * alpha + out.last() * (1 - alpha)
        }
        return out
    }

    private fun stdev(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val m = xs.average()
        val v = xs.sumOf { (it - m) * (it - m) } / (xs.size - 1)
        return sqrt(v)
    }

    fun candleMetrics(rows: List<Candle>, label: String = ""): Map<String, Double>? {
        if (rows.size < 5) return null
        val sorted = rows.sortedBy { it.openTime }
        val closes = sorted.map { it.close }
        val highs = sorted.map { it.high }
        val lows = sorted.map { it.low }
        val vols = sorted.map { it.volume }
        val last = closes.last()
        val first = closes.first()
        if (first == 0.0) return null
        val ret = (last - first) / first * 100.0
        val ret3 = if (closes.size > 4 && closes[closes.size - 4] != 0.0) {
            (closes.last() - closes[closes.size - 4]) / closes[closes.size - 4] * 100.0
        } else 0.0
        val rsi = Scalper.wilderRsi(closes, 14)
        val volLast = vols.lastOrNull() ?: 0.0
        val volMed = if (vols.size > 1) {
            val body = vols.dropLast(1).sorted()
            body[body.size / 2]
        } else 1.0

        val trs = sorted.mapIndexed { i, r ->
            if (i == 0) r.high - r.low
            else {
                val pc = sorted[i - 1].close
                max(r.high - r.low, max(abs(r.high - pc), abs(r.low - pc)))
            }
        }
        val atr = if (trs.size >= 14) trs.takeLast(14).average() else trs.average()
        val atrPct = if (last == 0.0) 0.0 else atr / last * 100.0

        val bbPct = if (closes.size >= 20) {
            val win = closes.takeLast(20)
            val sma = win.average()
            val sd = stdev(win)
            if (sd == 0.0) 0.0 else (last - sma) / (2 * sd) * 100.0
        } else 0.0

        var macdLine = 0.0
        var signalLine = 0.0
        var histogram = 0.0
        if (closes.size >= 35) {
            val ema12 = ema(closes, 12)
            val ema26 = ema(closes, 26)
            val macdSeries = ema12.zip(ema26) { a, b -> a - b }
            val signalSeries = ema(if (macdSeries.size >= 26) macdSeries.takeLast(26) else macdSeries, 9)
            macdLine = macdSeries.last()
            signalLine = signalSeries.last()
            histogram = macdLine - signalLine
        }

        val stochRsi = if (closes.size >= 14) {
            val hh = highs.takeLast(14).max()
            val ll = lows.takeLast(14).min()
            if (hh == ll) 50.0 else (last - ll) / (hh - ll) * 100.0
        } else 50.0

        val typical = sorted.map { (it.high + it.low + it.close) / 3.0 }
        val volSum = vols.sum()
        val vwap = if (volSum == 0.0) last else typical.zip(vols) { t, v -> t * v }.sum() / volSum

        var supportTests = 0.0
        var resistanceTests = 0.0
        if (closes.size > 5) {
            for (i in 1..4) {
                val li = lows[lows.size - i]
                val li1 = lows[lows.size - i - 1]
                val hi = highs[highs.size - i]
                val hi1 = highs[highs.size - i - 1]
                if (abs(li - li1) / (li + 1e-6) < 0.01) supportTests += 1
                if (abs(hi - hi1) / (hi + 1e-6) < 0.01) resistanceTests += 1
            }
        }

        return mapOf(
            "ret" to ret,
            "ret_3" to ret3,
            "rsi" to rsi,
            "atr" to atr,
            "atr_pct" to atrPct,
            "bb_pct" to bbPct,
            "last" to last,
            "high" to (highs.maxOrNull() ?: 0.0),
            "low" to (lows.minOrNull() ?: 0.0),
            "vol_total" to vols.sum(),
            "vol_last" to volLast,
            "vol_med" to volMed,
            "n" to closes.size.toDouble(),
            "macd_line" to macdLine,
            "signal_line" to signalLine,
            "histogram" to histogram,
            "stoch_rsi" to stochRsi,
            "vwap" to vwap,
            "support_tests" to supportTests,
            "resistance_tests" to resistanceTests,
        )
    }
}
