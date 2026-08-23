package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CurvesTest {
    @Test
    fun rsiSmoothNoStairs() {
        assertEquals(0.0, Curves.rsiSignal(50.0), 1e-6)
        assertTrue(Curves.rsiSignal(20.0) > 20)
        assertTrue(Curves.rsiSignal(80.0) < -20)
        var prev = Curves.rsiSignal(0.0)
        var maxJump = 0.0
        var x = 0.5
        while (x <= 100.0) {
            val v = Curves.rsiSignal(x)
            maxJump = maxOf(maxJump, abs(v - prev))
            prev = v
            x += 0.5
        }
        assertTrue(maxJump < 2.0)
    }

    @Test
    fun oiQuadrants() {
        assertTrue(Curves.oiScore(20.0, 10.0) > 40)
        assertTrue(Curves.oiScore(20.0, -10.0) < -25)
        assertTrue(Curves.oiScore(-20.0, 10.0) > 18)
        assertTrue(Curves.oiScore(-20.0, -10.0) < -40)
        assertEquals(0.0, Curves.oiScore(0.0, 0.0), 1e-9)
    }

    @Test
    fun lsCrowding() {
        assertTrue(Curves.lsScore(2.5) < -25)
        assertTrue(Curves.lsScore(0.4) > 20)
        assertEquals(0.0, Curves.lsScore(1.0), 1e-6)
    }

    @Test
    fun riskNormalizesTo100() {
        assertEquals(65.0, Curves.RISK_RAW_MAX, 0.0)
        assertEquals(100, Curves.riskScore(9.0, 0.02, 3.0, 100.0))
        assertEquals(0, Curves.riskScore(0.5, 0.0, 1.0, 50_000_000.0))
        assertEquals(Math.round(20.0 / 65.0 * 100.0).toInt(), Curves.riskScore(0.5, 0.0, 1.0, 100.0))
    }

    @Test
    fun slTpScalesWithAbsScore() {
        val lo = Curves.slTp(100.0, "BULLISH", 2.0, 5.0)
        val hi = Curves.slTp(100.0, "BULLISH", 2.0, 80.0)
        assertTrue(lo.slPct < hi.slPct)
        assertTrue(lo.tpPct / lo.slPct < hi.tpPct / hi.slPct)
        assertTrue(lo.tpPct < hi.tpPct)
        val z = Curves.slTp(100.0, "BULLISH", 2.0, 0.0)
        assertEquals(1.0, z.tpPct / z.slPct, 0.05)
        val full = Curves.slTp(100.0, "BULLISH", 2.0, 100.0)
        assertEquals(2.5, full.tpPct / full.slPct, 0.05)
    }
}
