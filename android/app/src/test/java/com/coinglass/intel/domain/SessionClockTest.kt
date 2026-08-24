package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SessionClockTest {
    private fun utc(h: Int, day: Int = 24, month: Int = Calendar.AUGUST, year: Int = 2026): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.set(year, month, day, h, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun bar(t: Double, open: Double, close: Double = open) = Candle(
        openTime = t, open = open, high = open + 1, low = open - 1, close = close, volume = 1.0,
    )

    @Test
    fun windows() {
        assertEquals("ASIA", SessionClock.name(utc(3)))
        assertEquals("LONDON", SessionClock.name(utc(9)))
        assertEquals("LONDON+NY", SessionClock.name(utc(14)))
        assertEquals("NY", SessionClock.name(utc(18)))
        assertEquals("ASIA", SessionClock.name(utc(22)))
    }

    @Test
    fun flagsMatchSpec() {
        val asia = SessionClock.info(utc(2))
        assertTrue(asia.asia)
        assertFalse(asia.london)
        assertFalse(asia.ny)
        val both = SessionClock.info(utc(14))
        assertTrue(both.london)
        assertTrue(both.ny)
        val ny = SessionClock.info(utc(17))
        assertFalse(ny.london)
        assertTrue(ny.ny)
    }

    @Test
    fun weeklyOpenIsLastWeekCandleOpen() {
        val bars = listOf(bar(1.0, 90.0, 95.0), bar(2.0, 96.0, 99.0))
        assertEquals(96.0, SessionClock.weeklyOpen(bars), 0.0)
        assertEquals(0.0, SessionClock.weeklyOpen(emptyList()), 0.0)
    }

    @Test
    fun monthlyOpenPrefers1M() {
        assertEquals(120.0, SessionClock.monthlyOpen(listOf(bar(9.0, 120.0))), 0.0)
    }

    @Test
    fun monthlyOpenFallsBackToFirstDailyOfMonth() {
        val aug1 = utc(0, day = 1).toDouble()
        val aug10 = utc(0, day = 10).toDouble()
        val jul20 = utc(0, day = 20, month = Calendar.JULY).toDouble()
        val dailies = listOf(bar(jul20, 10.0), bar(aug10, 80.0), bar(aug1, 70.0))
        val now = utc(12, day = 24)
        assertEquals(70.0, SessionClock.monthlyOpen(emptyList(), dailies, now), 0.0)
    }

    @Test
    fun distPct() {
        assertEquals(10.0, SessionClock.distPct(110.0, 100.0), 1e-9)
        assertEquals(0.0, SessionClock.distPct(0.0, 100.0), 0.0)
    }
}
