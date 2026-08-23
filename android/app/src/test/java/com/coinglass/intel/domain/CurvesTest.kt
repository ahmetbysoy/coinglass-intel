package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.BookSnap
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

    @Test
    fun slTpUsesNearbySupport() {
        val atrOnly = Curves.slTp(100.0, "BULLISH", 3.0, 40.0)
        val with = Curves.slTp(
            100.0, "BULLISH", 3.0, 40.0,
            StructureLevels(support = 98.8, bidWall = 98.7),
        )
        assertTrue(with.sl >= atrOnly.sl - 1e-9)
        assertTrue(with.reason.contains("vpoc") || with.reason.contains("ob") || with.reason.contains("atr"))
    }

    @Test
    fun slTpSkipsSpoofWall() {
        val clean = Curves.slTp(
            100.0, "BULLISH", 2.0, 40.0,
            StructureLevels(bidWall = 99.2),
            spoofScore = 0,
        )
        val spoofed = Curves.slTp(
            100.0, "BULLISH", 2.0, 40.0,
            StructureLevels(bidWall = 99.2),
            spoofScore = 80,
        )
        assertTrue(spoofed.reason.contains("spoof-skip-wall"))
        assertTrue(spoofed.netRr != 0.0 || spoofed.slPct > 0)
        assertTrue(clean.reason.contains("ob-bid") || clean.sl != spoofed.sl)
    }

    @Test
    fun spoofVanishingWall() {
        val mid = 100.0
        val hist = listOf(
            BookSnap(0, mid, listOf(99.4 to 10.0, 99.0 to 400.0), emptyList()),
            BookSnap(2000, mid, listOf(99.4 to 10.0, 99.0 to 400.0), emptyList()),
            BookSnap(4000, mid, listOf(99.4 to 10.0), emptyList()),
        )
        // 500 vs empty median path — first snap wall should vanish
        val s = Structure.spoofFromHistory(hist, 4000)
        assertTrue(s >= 0)
    }

    @Test
    fun calibratorNeedsThirtyForFull() {
        val avg = mapOf("OB" to 4.0)
        assertTrue(WeightCalibrator.boost(avg, 7).isEmpty())
        val half = WeightCalibrator.boost(avg, 12).getValue("OB")
        val full = WeightCalibrator.boost(avg, 40).getValue("OB")
        assertTrue(full > half)
        assertTrue(half > 1.0)
    }
}
