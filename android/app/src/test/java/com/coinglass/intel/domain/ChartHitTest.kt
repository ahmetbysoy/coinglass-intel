package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartHitTest {
    private fun bar(t: Double, close: Double) = Candle(
        openTime = t, open = close - 1, high = close + 2, low = close - 2, close = close, volume = 5.0,
    )

    @Test
    fun indexMapsSlot() {
        assertEquals(0, ChartHit.index(0f, 100f, 10))
        assertEquals(4, ChartHit.index(45f, 100f, 10))
        assertEquals(9, ChartHit.index(99f, 100f, 10))
        assertNull(ChartHit.index(-1f, 100f, 10))
        assertNull(ChartHit.index(101f, 100f, 10))
        assertNull(ChartHit.index(10f, 0f, 10))
        assertNull(ChartHit.index(10f, 100f, 0))
    }

    @Test
    fun tipCarriesOhlcAndTime() {
        val bars = listOf(bar(1_700_000_000_000.0, 100.0), bar(1_700_000_060_000.0, 110.0))
        val tip = ChartHit.tip(bars, 1)
        assertNotNull(tip)
        assertEquals(1, tip!!.idx)
        assertEquals(110.0, tip.candle.close, 0.0)
        assertTrue(tip.line.contains("C"))
        assertTrue(tip.line.contains("liq —"))
    }

    @Test
    fun liqBinFollowsClose() {
        val heat = LiqHeat.build(
            listOf(LiqPrint(100.0, 50_000.0, longSide = true), LiqPrint(101.0, 20_000.0, longSide = false)),
            anchor = 100.0,
            bins = 24,
        )
        val tip = ChartHit.tip(listOf(bar(1.0, 100.0)), 0, heat)
        assertNotNull(tip)
        assertNotNull(tip!!.liqBin)
        assertTrue(tip.line.contains("liq L"))
    }

    @Test
    fun secondsOpenTimeConverted() {
        val ms = ChartHit.timeMs(1_700_000_000.0)
        assertEquals(1_700_000_000_000L, ms)
        assertEquals(1_700_000_000_000L, ChartHit.timeMs(1_700_000_000_000.0))
    }

    @Test
    fun missingIdxIsNull() {
        assertNull(ChartHit.tip(emptyList(), 0))
        assertNull(ChartHit.tip(listOf(bar(1.0, 1.0)), 3))
    }

    @Test
    fun indexOfTimeSurvivesWindowShift() {
        val bars = listOf(bar(10.0, 1.0), bar(20.0, 2.0), bar(30.0, 3.0))
        assertEquals(1, ChartHit.indexOfTime(bars, 20.0))
        val shifted = bars.drop(1)
        assertEquals(0, ChartHit.indexOfTime(shifted, 20.0))
        assertEquals(-1, ChartHit.indexOfTime(shifted, 10.0))
        assertEquals(-1, ChartHit.indexOfTime(bars, null))
        assertEquals(20.0, ChartHit.timeOf(bars, 1)!!, 0.0)
    }

    @Test
    fun magnetSnapsToNearestOhlc() {
        val c = Candle(1.0, open = 100.0, high = 110.0, low = 90.0, close = 105.0, volume = 1.0)
        assertEquals(110.0, ChartHit.magnet(c, 109.0), 0.0)
        assertEquals(90.0, ChartHit.magnet(c, 91.0), 0.0)
        assertEquals(100.0, ChartHit.magnet(c, 99.2), 0.0)
        assertEquals(105.0, ChartHit.magnet(c, 104.0), 0.0)
    }

    @Test
    fun priceAtYMapsTopToHigh() {
        assertEquals(120.0, ChartHit.priceAtY(0f, 100f, 100.0, 120.0), 1e-9)
        assertEquals(100.0, ChartHit.priceAtY(100f, 100f, 100.0, 120.0), 1e-9)
        assertEquals(110.0, ChartHit.priceAtY(50f, 100f, 100.0, 120.0), 1e-9)
    }
}
