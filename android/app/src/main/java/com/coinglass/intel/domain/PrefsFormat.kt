package com.coinglass.intel.domain

import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

object PrefsFormat {
    fun compactUsd(v: Double): String = when {
        v >= 1_000_000 -> "$" + "%.1fM".format(Locale.ROOT, v / 1_000_000)
        v >= 1_000 -> "$" + "%.0fK".format(Locale.ROOT, v / 1_000)
        else -> "$" + "%.0f".format(Locale.ROOT, v)
    }

    fun fmt(pattern: String, v: Double): String = pattern.format(Locale.ROOT, v)

    fun logToLinear(v: Double, min: Double, max: Double): Float {
        if (min <= 0.0 || max <= min) return 0f
        val x = v.coerceIn(min, max)
        return ((ln(x) - ln(min)) / (ln(max) - ln(min))).toFloat().coerceIn(0f, 1f)
    }

    fun linearToLog(t: Float, min: Double, max: Double): Double {
        if (min <= 0.0 || max <= min) return min
        val u = t.toDouble().coerceIn(0.0, 1.0)
        return exp(ln(min) + (ln(max) - ln(min)) * u)
    }
}
