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
    fun emptyExtrasKeepsCandleRange() {
        val (lo, hi) = ChartRange.bounds(50.0, 51.0, emptyList())
        assertTrue(lo < 50.0)
        assertTrue(hi > 51.0)
        assertEquals(50.0, lo + (50.0 - lo), 1e-9)
    }
}
