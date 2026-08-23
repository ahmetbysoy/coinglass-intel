package com.coinglass.intel.domain

import com.coinglass.intel.data.db.OutcomeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyRiskTest {
    private fun row(ts: Long, win: Boolean?, settled: Boolean = true) = OutcomeEntity(
        id = ts,
        symbol = "XUSDT",
        ts = ts,
        price = 10.0,
        score = 20.0,
        direction = "BULLISH",
        ob = 0.0, tf = 0.0, oi = 0.0, funding = 0.0, liq = 0.0, vol = 0.0, mom = 0.0,
        settled15 = settled,
        win15 = win,
    )

    @Test
    fun emptyDay() {
        val s = DailyRisk.of(emptyList(), now = 1_800_000_000_000L)
        assertEquals(0, s.trades)
        assertFalse(s.hot)
    }

    @Test
    fun streakAndHot() {
        val day = DailyRisk.startOfDay(1_800_000_000_000L) + 3_600_000L
        val rows = listOf(
            row(day, true),
            row(day + 1, false),
            row(day + 2, false),
            row(day + 3, false),
        )
        val s = DailyRisk.of(rows, now = day + 10_000)
        assertEquals(4, s.trades)
        assertEquals(3, s.streakLoss)
        assertTrue(s.hot)
        assertTrue(s.line.contains("kayıp") || s.line.contains("DUR"))
    }

    @Test
    fun ignoresYesterday() {
        val now = 1_800_000_000_000L
        val yest = DailyRisk.startOfDay(now) - 3_600_000L
        val s = DailyRisk.of(listOf(row(yest, false)), now = now)
        assertEquals(0, s.trades)
    }
}
