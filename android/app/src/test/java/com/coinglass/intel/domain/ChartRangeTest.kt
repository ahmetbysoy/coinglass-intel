package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
