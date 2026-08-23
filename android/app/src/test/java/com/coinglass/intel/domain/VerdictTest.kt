package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerdictTest {
    @Test
    fun spoofBlocksEntry() {
        val v = Verdict.evaluate("BULLISH", 40.0, 80.0, 4.0, spoof = 62, risk = 20, netRr = 2.0, why = "OB bid")
        assertFalse(v.enterOk)
        assertTrue(v.line.startsWith("GİRME"))
        assertTrue(v.reasons.any { it.contains("spoof") })
    }

    @Test
    fun lowCoverageBlocks() {
        val v = Verdict.evaluate("BULLISH", 40.0, 20.0, 4.0, spoof = 10, risk = 20, netRr = 2.0, why = "OB")
        assertFalse(v.enterOk)
        assertTrue(v.reasons.any { it.contains("coverage") })
    }

    @Test
    fun feeEatsRr() {
        val v = Verdict.evaluate("BEARISH", -35.0, 80.0, -3.0, spoof = 10, risk = 20, netRr = 0.6, why = "CVD")
        assertFalse(v.enterOk)
        assertTrue(v.reasons.any { it.contains("netRR") })
    }

    @Test
    fun cleanLongIsA() {
        val v = Verdict.evaluate("BULLISH", 55.0, 90.0, 5.0, spoof = 10, risk = 20, netRr = 2.0, why = "OB + CVD")
        assertTrue(v.enterOk)
        assertEquals("A", v.grade)
        assertTrue(v.line.startsWith("LONG"))
    }

    @Test
    fun pepeGetsLeveragedForms() {
        val c = Symbols.candidates("PEPE")
        assertTrue(c.contains("PEPEUSDT"))
        assertTrue(c.contains("1000PEPEUSDT"))
        assertTrue(c.none { it == "BTCUSDT" })
    }

    @Test
    fun alreadyPrefixedNotDoubled() {
        val c = Symbols.candidates("1000PEPEUSDT")
        assertEquals(listOf("1000PEPEUSDT"), c)
    }
}
