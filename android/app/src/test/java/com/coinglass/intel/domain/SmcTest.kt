package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmcTest {
    private fun bar(
        t: Double,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
    ) = Candle(t, open, high, low, close, 1.0)

    @Test
    fun emptyAndShortYieldNothing() {
        assertTrue(Smc.analyze(emptyList()).fvgs.isEmpty())
        assertTrue(Smc.analyze(listOf(bar(1.0, 1.0, 1.1, 0.9, 1.0))).obs.isEmpty())
    }

    @Test
    fun bullFvgThreeCandles() {
        val bars = listOf(
            bar(1.0, 9.0, 10.0, 8.0, 9.5),
            bar(2.0, 9.5, 11.0, 9.4, 10.8),
            bar(3.0, 12.2, 13.0, 12.1, 12.8),
        )
        val z = Smc.analyze(bars).fvgs.single()
        assertEquals("fvg", z.kind)
        assertEquals("bull", z.side)
        assertEquals(10.0, z.low, 1e-9)
        assertEquals(12.1, z.high, 1e-9)
        assertFalse(z.touched)
    }

    @Test
    fun bearFvgThreeCandles() {
        val bars = listOf(
            bar(1.0, 13.0, 13.5, 12.0, 12.2),
            bar(2.0, 12.1, 12.4, 11.0, 11.2),
            bar(3.0, 10.5, 10.8, 9.5, 9.8),
        )
        val z = Smc.analyze(bars).fvgs.single()
        assertEquals("bear", z.side)
        assertEquals(10.8, z.low, 1e-9)
        assertEquals(12.0, z.high, 1e-9)
        assertFalse(z.touched)
    }

    @Test
    fun fvgFillsWhenLaterBarEntersGap() {
        val bars = listOf(
            bar(1.0, 9.0, 10.0, 8.0, 9.5),
            bar(2.0, 9.5, 11.0, 9.4, 10.8),
            bar(3.0, 12.2, 13.0, 12.1, 12.8),
            bar(4.0, 11.2, 11.8, 10.5, 10.8),
        )
        val z = Smc.analyze(bars).fvgs.single()
        assertTrue(z.touched)
        assertEquals(3, z.touchIdx)
    }

    @Test
    fun orderBlockIsOppositeCandleBeforeImpulse() {
        val small = (1..10).map { i ->
            bar(i.toDouble(), 100.0, 100.2, 99.8, 100.1)
        }
        val bear = bar(11.0, 100.1, 100.15, 99.5, 99.6)
        val impulse = bar(12.0, 99.7, 102.0, 99.65, 101.8)
        val r = Smc.analyze(small + bear + impulse)
        val bull = r.obs.filter { it.side == "bull" }
        assertTrue(bull.isNotEmpty())
        val z = bull.last()
        assertEquals(99.5, z.low, 1e-9)
        assertEquals(100.15, z.high, 1e-9)
        assertFalse(z.touched)
    }

    @Test
    fun orderBlockTouchedAfterReturn() {
        val small = (1..10).map { i ->
            bar(i.toDouble(), 100.0, 100.2, 99.8, 100.1)
        }
        val bear = bar(11.0, 100.1, 100.15, 99.5, 99.6)
        val impulse = bar(12.0, 99.7, 102.0, 99.65, 101.8)
        val back = bar(13.0, 101.0, 101.2, 99.7, 100.0)
        val r = Smc.analyze(small + bear + impulse + back)
        val z = r.obs.last { it.side == "bull" }
        assertTrue(z.touched)
    }

    @Test
    fun obsCappedAtSix() {
        val bars = mutableListOf<Candle>()
        var t = 1.0
        repeat(8) {
            repeat(3) {
                bars += bar(t++, 100.0, 100.15, 99.9, 100.05)
            }
            bars += bar(t++, 100.0, 100.1, 99.4, 99.5)
            bars += bar(t++, 99.6, 103.0, 99.5, 102.5)
        }
        val r = Smc.analyze(bars)
        assertTrue(r.obs.size <= Smc.OB_MAX)
    }

    @Test
    fun sweepWickAboveEqualHighClosesInside() {
        val noise = (1..15).map { i ->
            bar(i.toDouble(), 40.0, 40.0 + i * 0.25, 39.4, 40.05)
        }
        val e1 = bar(16.0, 50.2, 51.0, 50.0, 50.3)
        val e2 = bar(17.0, 50.1, 51.0, 50.0, 50.2)
        val wick = bar(18.0, 50.5, 51.4, 50.1, 50.4)
        val r = Smc.analyze(noise + e1 + e2 + wick)
        val s = r.sweeps.firstOrNull { it.side == "bear" }
        assertTrue("bear sweep missing", s != null)
        assertEquals(17, s!!.endIdx)
        assertEquals(51.4, s.high, 1e-9)
        assertEquals(51.0, s.low, 1e-9)
    }

    @Test
    fun sweepWickBelowEqualLowClosesInside() {
        val noise = (1..15).map { i ->
            bar(i.toDouble(), 60.0, 60.6, 60.0 - i * 0.2, 60.1)
        }
        val e1 = bar(16.0, 50.4, 50.6, 50.0, 50.3)
        val e2 = bar(17.0, 50.3, 50.5, 50.0, 50.2)
        val wick = bar(18.0, 50.2, 50.4, 49.6, 50.15)
        val r = Smc.analyze(noise + e1 + e2 + wick)
        val s = r.sweeps.firstOrNull { it.side == "bull" }
        assertTrue("bull sweep missing", s != null)
        assertEquals(49.6, s!!.low, 1e-9)
    }

    @Test
    fun alignedUntouchedOnlySameSide() {
        val bars = listOf(
            bar(1.0, 9.0, 10.0, 8.0, 9.5),
            bar(2.0, 9.5, 11.0, 9.4, 10.8),
            bar(3.0, 12.2, 13.0, 12.1, 12.8),
        )
        val r = Smc.analyze(bars)
        assertTrue(r.alignedUntouched("BULLISH"))
        assertTrue(r.alignedUntouched("HAFIF BULLISH"))
        assertFalse(r.alignedUntouched("BEARISH"))
        assertFalse(r.alignedUntouched("NEUTRAL"))
    }

    @Test
    fun smcBoostCapsGradeAtA() {
        val weak = Verdict.evaluate(
            "BULLISH", 12.0, 50.0, 0.5,
            spoof = 40, risk = 50, netRr = 1.0, why = "x",
        )
        assertTrue(weak.grade == "C" || weak.grade == "D")
        val boosted = Verdict.evaluate(
            "BULLISH", 12.0, 50.0, 0.5,
            spoof = 40, risk = 50, netRr = 1.0, why = "x",
            smcBoost = Smc.BOOST,
        )
        assertEquals("A", boosted.grade)
        assertEquals(Smc.BOOST, boosted.smcBoost)
        assertTrue(boosted.line.contains("SMC"))
    }
}
