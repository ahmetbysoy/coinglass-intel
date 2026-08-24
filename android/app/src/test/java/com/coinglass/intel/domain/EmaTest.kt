package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
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

    private fun bars(closes: List<Double>, t0: Double = 1_000.0) =
        closes.mapIndexed { i, c -> Candle(t0 + i, c, c + 1, c - 1, c, 1.0) }

    @Test
    fun cacheMatchesFullRebuild() {
        val cache = EmaCache(3)
        val all = bars((1..20).map { it.toDouble() })
        val got = cache.update(all)
        val want = Ema.of(all.map { it.close }, 3)
        assertEquals(want.size, got.size)
        for (i in want.indices) {
            if (want[i].isNaN()) assertTrue(got[i].isNaN())
            else assertEquals(want[i], got[i], 1e-9)
        }
    }

    @Test
    fun cacheLastTickIsO1Equivalent() {
        val cache = EmaCache(3)
        val first = bars((1..12).map { it.toDouble() })
        cache.update(first)
        val updated = first.dropLast(1) + first.last().copy(close = 99.0)
        val got = cache.update(updated)
        val want = Ema.of(updated.map { it.close }, 3)
        assertEquals(want.last(), got.last(), 1e-9)
        assertEquals(want.size, got.size)
    }

    @Test
    fun cacheAppendMatchesFull() {
        val cache = EmaCache(3)
        val first = bars((1..10).map { it.toDouble() })
        cache.update(first)
        val next = first + Candle(1_000.0 + 10, 11.0, 12.0, 10.0, 11.0, 1.0)
        val got = cache.update(next)
        val want = Ema.of(next.map { it.close }, 3)
        assertEquals(want.size, got.size)
        assertEquals(want.last(), got.last(), 1e-9)
    }

    @Test
    fun cacheResetsOnNewSeries() {
        val cache = EmaCache(3)
        cache.update(bars((1..10).map { it.toDouble() }, t0 = 1.0))
        val other = bars((50..60).map { it.toDouble() }, t0 = 9_000.0)
        val got = cache.update(other)
        val want = Ema.of(other.map { it.close }, 3)
        assertEquals(want.last(), got.last(), 1e-9)
    }
}
