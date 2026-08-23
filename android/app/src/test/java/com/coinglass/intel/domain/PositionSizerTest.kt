package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionSizerTest {
    @Test
    fun onePercentAtOnePctSl() {
        val p = PositionSizer.plan(10_000.0, 1.0, 100.0, 99.0)
        assertEquals(100.0, p.riskUsd, 1e-6)
        assertEquals(1.0, p.slPct, 1e-6)
        assertEquals(10_000.0, p.sizeUsd, 1e-4)
        assertEquals(100.0, p.qty, 1e-4)
    }

    @Test
    fun zeroOnBadInputs() {
        assertEquals(0.0, PositionSizer.plan(0.0, 1.0, 100.0, 99.0).sizeUsd, 0.0)
        assertEquals(0.0, PositionSizer.plan(1000.0, 1.0, 0.0, 99.0).qty, 0.0)
    }

    @Test
    fun widerSlSmallerSize() {
        val tight = PositionSizer.plan(10_000.0, 1.0, 100.0, 99.5)
        val wide = PositionSizer.plan(10_000.0, 1.0, 100.0, 98.0)
        assertTrue(wide.sizeUsd < tight.sizeUsd)
    }
}
