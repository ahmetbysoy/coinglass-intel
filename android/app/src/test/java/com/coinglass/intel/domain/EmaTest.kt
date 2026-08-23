package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmaTest {
    @Test
    fun shortSeriesEmpty() {
        assertTrue(Ema.of(listOf(1.0, 2.0), 5).isEmpty())
    }

    @Test
    fun seedIsSmaThenFollows() {
        val xs = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val e = Ema.of(xs, 3)
        assertEquals(5, e.size)
        assertTrue(e[0].isNaN())
        assertTrue(e[1].isNaN())
        assertEquals(2.0, e[2], 1e-9)
        val k = 2.0 / 4.0
        assertEquals(4.0 * k + 2.0 * (1 - k), e[3], 1e-9)
    }
}
