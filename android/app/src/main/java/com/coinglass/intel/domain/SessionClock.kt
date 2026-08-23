package com.coinglass.intel.domain

import java.util.Calendar
import java.util.TimeZone

/** UTC session windows — no exchange calendar holidays. */
object SessionClock {
    fun name(nowMs: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.timeInMillis = nowMs
        val h = c.get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 0..7 -> "ASIA"
            in 8..12 -> "LONDON"
            in 13..15 -> "LONDON+NY"
            in 16..20 -> "NY"
            else -> "ASIA"
        }
    }
}
