package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiqHeatTest {
    @Test
    fun emptyWhenNoAnchor() {
        assertTrue(LiqHeat.build(listOf(LiqPrint(100.0, 50.0, true)), 0.0).empty)
    }

    @Test
    fun bucketsSamePriceTogether() {
        val g = LiqHeat.build(
            listOf(
                LiqPrint(100.0, 40.0, true),
                LiqPrint(100.05, 10.0, true),
                LiqPrint(101.5, 80.0, false),
            ),
            anchor = 100.0,
            bins = 20,
            minRangePct = 2.0,
        )
        assertEquals(50.0, g.longTot, 1e-6)
        assertEquals(80.0, g.shortTot, 1e-6)
        assertTrue(g.bins.any { it.longUsd >= 49 })
        assertTrue(g.bins.any { it.shortUsd >= 79 })
    }

    @Test
    fun farPrintsDoNotExplodeRangeBeyondNeed() {
        val g = LiqHeat.build(
            listOf(LiqPrint(100.2, 5.0, true)),
            anchor = 100.0,
            bins = 10,
            minRangePct = 1.2,
        )
        assertTrue(g.hi - g.lo <= 100.0 * 0.03)
        assertTrue(g.bins.sumOf { it.longUsd } >= 5.0)
    }
}
