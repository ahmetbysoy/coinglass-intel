package com.coinglass.intel.domain

import com.coinglass.intel.data.db.OutcomeEntity
import java.util.Calendar

/** Overtrading / daily loss streak — UI only, no orders. */
object DailyRisk {
    data class Stats(
        val trades: Int,
        val losses15: Int,
        val streakLoss: Int,
        val hot: Boolean,
        val line: String,
    )

    fun of(rows: List<OutcomeEntity>, now: Long = System.currentTimeMillis(), warnAt: Int = 8): Stats {
        val start = startOfDay(now)
        val today = rows.filter { it.ts >= start }.sortedBy { it.ts }
        val losses = today.count { it.settled15 && it.win15 == false }
        var streak = 0
        for (o in today.asReversed()) {
            if (!o.settled15) continue
            if (o.win15 == false) streak += 1 else break
        }
        val hot = today.size >= warnAt || streak >= 3
        val line = when {
            today.isEmpty() -> "bugün işlem yok"
            hot && streak >= 3 -> "DUR — $streak kayıp üst üste"
            hot -> "çok işlem: ${today.size} kayıt bugün"
            else -> "bugün ${today.size} kayıt · $losses kayıp (15m)"
        }
        return Stats(today.size, losses, streak, hot, line)
    }

    fun startOfDay(now: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
