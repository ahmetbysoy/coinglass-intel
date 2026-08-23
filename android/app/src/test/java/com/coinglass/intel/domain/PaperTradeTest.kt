package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperTradeTest {
    private val t0 = 1_000_000L

    @Test
    fun longHitsSlIsLoss() {
        val hit = PaperEngine.checkExit("LONG", 100.0, 95.0, 110.0, 94.0, t0, t0 + 1_000)
        assertNotNull(hit)
        assertFalse(hit!!.win)
        assertEquals("sl", hit.reason)
        assertEquals(94.0, hit.px, 1e-9)
    }

    @Test
    fun longHitsTpIsWin() {
        val hit = PaperEngine.checkExit("LONG", 100.0, 95.0, 110.0, 110.0, t0, t0 + 1_000)
        assertNotNull(hit)
        assertTrue(hit!!.win)
        assertEquals("tp", hit.reason)
    }

    @Test
    fun shortHitsSlIsLoss() {
        val hit = PaperEngine.checkExit("SHORT", 100.0, 105.0, 90.0, 106.0, t0, t0 + 1_000)
        assertNotNull(hit)
        assertFalse(hit!!.win)
        assertEquals("sl", hit.reason)
    }

    @Test
    fun midPriceStaysOpen() {
        assertNull(PaperEngine.checkExit("LONG", 100.0, 95.0, 110.0, 101.0, t0, t0 + 60_000))
    }

    @Test
    fun timeoutUsesEntrySide() {
        val now = t0 + PaperEngine.TIMEOUT_MS + 1
        val lose = PaperEngine.checkExit("LONG", 100.0, 95.0, 110.0, 99.0, t0, now)
        assertNotNull(lose)
        assertFalse(lose!!.win)
        assertEquals("timeout", lose.reason)
        val win = PaperEngine.checkExit("LONG", 100.0, 95.0, 110.0, 101.0, t0, now)
        assertTrue(win!!.win)
    }

    @Test
    fun dedupBlocksSecondPaperInside120s() {
        assertFalse(
            PaperEngine.canOpen(true, "B", "ZZZUSDT", lastOpenAt = t0, now = t0 + 60_000),
        )
        assertFalse(
            PaperEngine.canOpen(true, "A", "ZZZUSDT", lastOpenAt = t0, now = t0 + 119_000),
        )
        assertTrue(
            PaperEngine.canOpen(true, "A", "ZZZUSDT", lastOpenAt = t0, now = t0 + PaperEngine.DEDUP_MS + 1),
        )
    }

    @Test
    fun firstPaperAlwaysAllowed() {
        assertTrue(PaperEngine.canOpen(true, "A", "FOOUSDT", null, t0))
    }

    @Test
    fun rejectsWeakVerdict() {
        assertFalse(PaperEngine.canOpen(false, "A", "FOOUSDT", null, t0))
        assertFalse(PaperEngine.canOpen(true, "C", "FOOUSDT", null, t0))
        assertFalse(PaperEngine.canOpen(true, "D", "FOOUSDT", null, t0))
        assertFalse(PaperEngine.canOpen(true, "A", "", null, t0))
        assertFalse(PaperEngine.canOpen(true, "A", "FOOUSDT", null, t0, hasOpen = true))
    }

    @Test
    fun sideFromDirection() {
        assertEquals("LONG", PaperEngine.sideOf("BULLISH"))
        assertEquals("LONG", PaperEngine.sideOf("HAFIF BULLISH"))
        assertEquals("SHORT", PaperEngine.sideOf("BEARISH"))
        assertNull(PaperEngine.sideOf("NEUTRAL"))
    }

    @Test
    fun calibratorStillNeedsEight() {
        assertTrue(WeightCalibrator.boost(mapOf("OB" to 1.0), 7).isEmpty())
        assertTrue(WeightCalibrator.boost(mapOf("OB" to 1.0), 8).isNotEmpty())
    }
}
