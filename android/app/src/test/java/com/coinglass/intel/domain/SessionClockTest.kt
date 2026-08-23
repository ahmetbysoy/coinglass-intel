package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SessionClockTest {
    private fun utc(h: Int): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.set(2026, Calendar.AUGUST, 24, h, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    @Test
    fun windows() {
        assertEquals("ASIA", SessionClock.name(utc(3)))
        assertEquals("LONDON", SessionClock.name(utc(9)))
        assertEquals("LONDON+NY", SessionClock.name(utc(14)))
        assertEquals("NY", SessionClock.name(utc(18)))
        assertEquals("ASIA", SessionClock.name(utc(22)))
    }
}
