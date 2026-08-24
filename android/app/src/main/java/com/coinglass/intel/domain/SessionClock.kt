package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import java.util.Calendar
import java.util.TimeZone

/** UTC session windows — no exchange calendar holidays. */
object SessionClock {
    data class Info(
        val name: String,
        val asia: Boolean,
        val london: Boolean,
        val ny: Boolean,
    )

    fun name(nowMs: Long = System.currentTimeMillis()): String = info(nowMs).name

    /** Asia 00–08, London 08–16, NY 13–21 UTC. Overlap = LONDON+NY. */
    fun info(nowMs: Long = System.currentTimeMillis()): Info {
        val h = utcHour(nowMs)
        val asia = h < 8 || h >= 21
        val london = h in 8..15
        val ny = h in 13..20
        val name = when {
            london && ny -> "LONDON+NY"
            london -> "LONDON"
            ny -> "NY"
            else -> "ASIA"
        }
        return Info(name, asia, london, ny)
    }

    /** Current week open = last 1w candle open (Monday 00:00 UTC). */
    fun weeklyOpen(weeklies: List<Candle>): Double {
        if (weeklies.isEmpty()) return 0.0
        val last = weeklies.maxBy { it.openTime }
        return when {
            last.open > 0.0 -> last.open
            last.close > 0.0 -> last.close
            else -> 0.0
        }
    }

    /** Current month open = last 1M open, else first 1d of this UTC month. */
    fun monthlyOpen(
        monthlies: List<Candle>,
        dailies: List<Candle> = emptyList(),
        nowMs: Long = System.currentTimeMillis(),
    ): Double {
        if (monthlies.isNotEmpty()) {
            val last = monthlies.maxBy { it.openTime }
            if (last.open > 0.0) return last.open
            if (last.close > 0.0) return last.close
        }
        if (dailies.isEmpty()) return 0.0
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = nowMs
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        var best: Candle? = null
        for (c in dailies) {
            cal.timeInMillis = timeMs(c.openTime)
            if (cal.get(Calendar.YEAR) != y || cal.get(Calendar.MONTH) != m) continue
            if (best == null || c.openTime < best.openTime) best = c
        }
        return best?.open?.takeIf { it > 0.0 } ?: best?.close ?: 0.0
    }

    fun distPct(price: Double, open: Double): Double {
        if (price <= 0.0 || open <= 0.0) return 0.0
        return (price - open) / open * 100.0
    }

    fun timeMs(openTime: Double): Long {
        val t = openTime.toLong()
        return if (t in 1 until 10_000_000_000L) t * 1000L else t
    }

    private fun utcHour(nowMs: Long): Int {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.timeInMillis = nowMs
        return c.get(Calendar.HOUR_OF_DAY)
    }
}
