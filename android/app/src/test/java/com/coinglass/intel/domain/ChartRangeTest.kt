package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChartRangeTest {
    @Test
    fun farWallDoesNotExpandRange() {
        val (lo, hi) = ChartRange.bounds(100.0, 102.0, listOf(40.0, 200.0))
        assertTrue(lo > 90.0)
        assertTrue(hi < 120.0)
        assertFalse(ChartRange.inView(40.0, lo, hi))
        assertFalse(ChartRange.inView(200.0, lo, hi))
    }

    @Test
    fun nearbySlExpandsSlightly() {
        val (lo, hi) = ChartRange.bounds(100.0, 102.0, listOf(99.5, 103.0))
        assertTrue(lo < 99.6)
        assertTrue(hi > 102.9)
    }

    @Test
    fun tightBandUsesFewerTicks() {
        assertEquals(3, ChartRange.tickCount(100.0, 100.3))
        assertEquals(4, ChartRange.tickCount(100.0, 101.5))
        assertEquals(5, ChartRange.tickCount(100.0, 104.0))
    }

    @Test
    fun niceStepIsOneTwoFiveFamily() {
        val step = ChartRange.niceStep(650.0, 5)
        val norm = step / Math.pow(10.0, Math.floor(Math.log10(step)))
        assertTrue("step=$step norm=$norm", abs(norm - 1.0) < 1e-9 || abs(norm - 2.0) < 1e-9 || abs(norm - 2.5) < 1e-9 || abs(norm - 5.0) < 1e-9)
    }

    @Test
    fun niceTicksAreInsideAndIncreasing() {
        val ticks = ChartRange.niceTicks(67100.0, 67800.0, 5)
        assertTrue(ticks.size >= 3)
        for (i in 1 until ticks.size) assertTrue(ticks[i] > ticks[i - 1])
        assertTrue(ticks.first() >= 67100.0 - 1e-6)
        assertTrue(ticks.last() <= 67800.0 + 1e-6)
        val step = ticks[1] - ticks[0]
        for (i in 1 until ticks.size) {
            assertEquals(step, ticks[i] - ticks[i - 1], step * 1e-6)
        }
    }

    @Test
    fun niceTicksBtcLikeAreRound() {
        val ticks = ChartRange.niceTicks(67000.0, 67500.0, 5)
        assertTrue(ticks.isNotEmpty())
        ticks.forEach { t ->
            assertEquals(t, Math.rint(t), 1e-6)
        }
    }

    @Test
    fun niceTicksSubPenny() {
        val ticks = ChartRange.niceTicks(0.00021, 0.00029, 4)
        assertTrue(ticks.size >= 2)
        assertTrue(ticks.first() >= 0.00020)
        assertTrue(ticks.last() <= 0.00030)
    }

    @Test
    fun niceTicksInvertedIsEmpty() {
        assertTrue(ChartRange.niceTicks(10.0, 10.0).isEmpty())
        assertTrue(ChartRange.niceTicks(12.0, 10.0).isEmpty())
    }

    @Test
    fun fmtAxisFollowsStep() {
        assertEquals("67200", ChartRange.fmtAxis(67200.0, 100.0))
        assertEquals("3500.5", ChartRange.fmtAxis(3500.50, 0.5))
        assertEquals("0.00024", ChartRange.fmtAxis(0.00024, 0.00002))
        assertFalse(ChartRange.fmtAxis(67234.18, 100.0).contains(","))
        assertFalse(ChartRange.fmtAxis(67234.18, 100.0).contains("$"))
    }

    @Test
    fun labelYStaysOnCanvas() {
        val y = ChartRange.clampLabelY(0f, 12f, 0f, 200f)
        assertEquals(0f, y, 0.01f)
        val y2 = ChartRange.clampLabelY(200f, 12f, 0f, 200f)
        assertEquals(188f, y2, 0.01f)
    }

    @Test
    fun placeSkipsOverlapAndAvoid() {
        val ticks = listOf(1.0, 2.0, 3.0)
        val ys = listOf(10f, 14f, 80f)
        val placed = ChartRange.placeAxisLabels(ticks, ys, labelH = 12f, top = 0f, bottom = 120f, avoidY = 80f, avoidGap = 10f)
        assertEquals(1, placed.size)
        assertEquals(1.0, placed[0].first, 0.0)
    }
}
