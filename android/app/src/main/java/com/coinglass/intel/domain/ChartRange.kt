package com.coinglass.intel.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

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

    /** Fewer ticks when the visible band is a few bps. */
    fun tickCount(lo: Double, hi: Double): Int {
        val mid = (lo + hi) / 2.0
        if (mid <= 0.0 || hi <= lo) return 3
        val pct = (hi - lo) / mid * 100.0
        return when {
            pct < 0.5 -> 3
            pct < 2.0 -> 4
            else -> 5
        }
    }

    fun niceStep(span: Double, target: Int): Double {
        val t = target.coerceIn(2, 10)
        if (span <= 0.0 || !span.isFinite()) return 1.0
        val raw = span / t
        if (raw <= 0.0 || !raw.isFinite()) return 1.0
        val exp = floor(log10(raw))
        val mag = 10.0.pow(exp)
        val r = raw / mag
        val n = when {
            r <= 1.0 -> 1.0
            r <= 2.0 -> 2.0
            r <= 2.5 -> 2.5
            r <= 5.0 -> 5.0
            else -> 10.0
        }
        return n * mag
    }

    /** Round prices (1 / 2 / 2.5 / 5 × 10ⁿ), not raw hi-lo / N. */
    fun niceTicks(lo: Double, hi: Double, target: Int = 5): List<Double> {
        if (!lo.isFinite() || !hi.isFinite() || hi <= lo) return emptyList()
        val step = niceStep(hi - lo, target)
        if (step <= 0.0 || !step.isFinite()) return emptyList()
        val first = ceil(lo / step - 1e-9) * step
        val out = ArrayList<Double>(target + 3)
        var v = first
        var i = 0
        while (v <= hi + step * 1e-7 && i < 16) {
            if (v >= lo - step * 1e-7) out.add(v)
            val next = v + step
            if (next <= v) break
            v = next
            i++
        }
        return out
    }

    fun fmtAxis(p: Double, step: Double): String {
        if (!p.isFinite()) return "—"
        val s = abs(if (step.isFinite() && step > 0.0) step else p)
        val d = when {
            s >= 10.0 -> 0
            s >= 1.0 -> 1
            s >= 0.1 -> 2
            s >= 0.01 -> 3
            s >= 0.0001 -> 5
            else -> 8
        }
        return if (d == 0) {
            String.format(Locale.US, "%.0f", p)
        } else {
            String.format(Locale.US, "%.${d}f", p).trimEnd('0').trimEnd('.')
        }
    }

    fun clampLabelY(lineY: Float, labelH: Float, top: Float, bottom: Float): Float {
        val h = labelH.coerceAtLeast(1f)
        val lo = top
        val hi = (bottom - h).coerceAtLeast(top)
        return (lineY - h / 2f).coerceIn(lo, hi)
    }

    fun tooClose(a: Float, b: Float, minGap: Float): Boolean = abs(a - b) < minGap

    fun placeAxisLabels(
        ticks: List<Double>,
        lineY: List<Float>,
        labelH: Float,
        top: Float,
        bottom: Float,
        avoidY: Float? = null,
        avoidGap: Float = 0f,
    ): List<Pair<Double, Float>> {
        if (ticks.size != lineY.size) return emptyList()
        val out = ArrayList<Pair<Double, Float>>(ticks.size)
        for (i in ticks.indices) {
            val y = clampLabelY(lineY[i], labelH, top, bottom)
            val mid = y + labelH / 2f
            if (avoidY != null && tooClose(mid, avoidY, avoidGap)) continue
            if (out.any { tooClose(it.second, y, labelH + 2f) }) continue
            out.add(ticks[i] to y)
        }
        return out
    }
}
