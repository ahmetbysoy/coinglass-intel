package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PulseMathTest {
    @Test
    fun remainingMissingIsNotHot() {
        assertEquals(-1L, PulseMath.remainingMs(0L, 1_000L))
        assertFalse(PulseMath.isHot(PulseMath.remainingMs(0L, System.currentTimeMillis())))
        assertFalse(PulseMath.isHot(-5L))
        assertFalse(PulseMath.isHot(0L))
    }

    @Test
    fun hotInsideThirtyMinutes() {
        assertTrue(PulseMath.isHot(1L))
        assertTrue(PulseMath.isHot(29 * 60_000L))
        assertFalse(PulseMath.isHot(30 * 60_000L + 1))
    }

    @Test
    fun etaSplitsHoursAndMinutes() {
        val rem = 2 * 3_600_000L + 15 * 60_000L
        assertEquals(2L, PulseMath.etaHours(rem))
        assertEquals(15L, PulseMath.etaMinutes(rem))
        assertEquals(0L, PulseMath.etaHours(-1L))
    }

    @Test
    fun progressClamps() {
        assertEquals(1f, PulseMath.fundingProgress(-1L), 0f)
        assertEquals(0f, PulseMath.fundingProgress(PulseMath.FUNDING_INTERVAL_MS), 1e-5f)
        assertTrue(PulseMath.fundingProgress(PulseMath.FUNDING_INTERVAL_MS / 2) in 0.49f..0.51f)
    }

    @Test
    fun signedFormatIgnoresTrLocale() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("+1.25", PulseMath.fmtSigned(1.25, 2))
            assertEquals("-0.0100", PulseMath.fmtSigned(-0.01, 4))
        } finally {
            Locale.setDefault(prev)
        }
    }

    @Test
    fun spreadNeedsTwoRates() {
        assertNull(PulseMath.spreadPct(emptyList()))
        assertNull(PulseMath.spreadPct(listOf(0.0001)))
        assertEquals(0.02, PulseMath.spreadPct(listOf(0.0001, 0.0003))!!, 1e-12)
    }
}
