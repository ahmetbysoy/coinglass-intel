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

    @Test
    fun statsPullsTowardFatterSide() {
        val g = LiqHeat.build(
            listOf(
                LiqPrint(101.2, 200.0, false),
                LiqPrint(99.2, 20.0, true),
            ),
            anchor = 100.0,
            bins = 20,
            minRangePct = 2.0,
        )
        val s = LiqHeat.stats(g, 100.0)
        assertTrue(s.upBias > 0.5f)
        assertTrue(s.clusters.isNotEmpty())
        assertEquals(s.clusters.maxOf { it.usd }, s.clusters.first().usd, 1e-9)
    }

    @Test
    fun statsEmptyGridIsNeutral() {
        val s = LiqHeat.stats(LiqHeat.Grid(), 100.0)
        assertEquals(0.5f, s.upBias, 0f)
        assertTrue(s.clusters.isEmpty())
    }

    @Test
    fun binIndexTopIsLastBin() {
        assertEquals(9, LiqHeat.binIndexAt(0f, 100f, 10))
        assertEquals(0, LiqHeat.binIndexAt(99f, 100f, 10))
        assertEquals(-1, LiqHeat.binIndexAt(0f, 0f, 10))
        assertEquals(-1, LiqHeat.binIndexAt(0f, 100f, 0))
    }

    @Test
    fun markTOutsideAndInside() {
        assertEquals(0.5, LiqHeat.markT(100.0, 90.0, 110.0), 1e-9)
        assertTrue(LiqHeat.markT(120.0, 90.0, 110.0) > 1.0)
        assertTrue(LiqHeat.markT(80.0, 90.0, 110.0) < 0.0)
        assertTrue(LiqHeat.markT(100.0, 100.0, 100.0).isNaN())
    }
}
