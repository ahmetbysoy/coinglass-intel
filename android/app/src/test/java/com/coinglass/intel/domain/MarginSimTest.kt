package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarginSimTest {
    @Test
    fun longLev10BelowEntry() {
        val liq = MarginSimulator.liqPrice(100.0, 10.0, "long")
        assertTrue(liq < 100.0)
        assertEquals(90.4, liq, 1e-9)
    }

    @Test
    fun shortLev10AboveEntry() {
        val liq = MarginSimulator.liqPrice(100.0, 10.0, "short")
        assertTrue(liq > 100.0)
        assertEquals(109.6, liq, 1e-9)
    }

    @Test
    fun bullAliasIsLong() {
        assertEquals(
            MarginSimulator.liqPrice(50.0, 5.0, "long"),
            MarginSimulator.liqPrice(50.0, 5.0, "BULLISH"),
            1e-12,
        )
    }

    @Test
    fun badInputsZero() {
        assertEquals(0.0, MarginSimulator.liqPrice(0.0, 10.0, "long"), 0.0)
        assertEquals(0.0, MarginSimulator.liqPrice(100.0, 0.5, "long"), 0.0)
        assertEquals(0.0, MarginSimulator.liqPrice(100.0, 10.0, "flat"), 0.0)
    }
}
