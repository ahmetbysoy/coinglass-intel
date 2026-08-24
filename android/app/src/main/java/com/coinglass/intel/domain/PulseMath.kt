package com.coinglass.intel.domain

import java.util.Locale

object PulseMath {
    const val FUNDING_INTERVAL_MS = 8L * 60L * 60_000L
    const val FUNDING_HOT_MS = 30L * 60_000L

    fun remainingMs(nextFundingMs: Long, nowMs: Long): Long {
        if (nextFundingMs <= 0L) return -1L
        return nextFundingMs - nowMs
    }

    fun isHot(remainingMs: Long, windowMs: Long = FUNDING_HOT_MS): Boolean =
        remainingMs in 1..windowMs

    fun etaHours(remainingMs: Long): Long = remainingMs.coerceAtLeast(0L) / 3_600_000L

    fun etaMinutes(remainingMs: Long): Long = remainingMs.coerceAtLeast(0L) / 60_000L % 60L

    /** 0 = just funded / far, 1 = due now. */
    fun fundingProgress(remainingMs: Long, intervalMs: Long = FUNDING_INTERVAL_MS): Float {
        if (remainingMs <= 0L || intervalMs <= 0L) return 1f
        return (1f - remainingMs.toFloat() / intervalMs.toFloat()).coerceIn(0f, 1f)
    }

    fun fmtSigned(v: Double, digits: Int): String =
        String.format(Locale.ROOT, "%+.${digits}f", v)

    fun spreadPct(rates: List<Double>): Double? {
        if (rates.size < 2) return null
        return (rates.max() - rates.min()) * 100.0
    }
}
