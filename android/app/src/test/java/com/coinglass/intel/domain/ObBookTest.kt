package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ObBookTest {
    @Test
    fun emptyBookHasNullSpread() {
        val b = ObBook.build(emptyList(), emptyList(), 8, false, 0.0, 0.0)
        assertTrue(b.bids.isEmpty())
        assertTrue(b.asks.isEmpty())
        assertNull(b.spreadPct)
        assertEquals(1.0, b.maxQty, 0.0)
    }

    @Test
    fun spreadAndTotals() {
        val bids = listOf(10.0 to 2.0, 9.9 to 3.0)
        val asks = listOf(10.1 to 1.0, 10.2 to 4.0)
        val b = ObBook.build(bids, asks, 8, false, 0.0, 0.0)
        assertEquals(5.0, b.bidTotal, 1e-9)
        assertEquals(5.0, b.askTotal, 1e-9)
        assertEquals(1.0, b.spreadPct!!, 1e-9)
        assertEquals(2.0, b.bids[0].qty, 0.0)
        assertEquals(5.0, b.bids[1].cumQty, 1e-9)
    }

    @Test
    fun rowsCap() {
        val bids = (1..20).map { (10.0 - it * 0.01) to 1.0 }
        assertEquals(8, ObBook.build(bids, emptyList(), ObBook.ROWS_NORMAL, false, 0.0, 0.0).bids.size)
        assertEquals(16, ObBook.build(bids, emptyList(), ObBook.ROWS_DEEP, false, 0.0, 0.0).bids.size)
    }

    @Test
    fun wallOnlyWhenSpoofOnAndClose() {
        val bids = listOf(100.0 to 8.0, 99.9 to 1.0)
        val off = ObBook.build(bids, emptyList(), 8, spoofOn = false, bidWall = 100.0, askWall = 0.0)
        assertTrue(off.bids.none { it.isWall })
        val on = ObBook.build(bids, emptyList(), 8, spoofOn = true, bidWall = 100.0, askWall = 0.0)
        assertTrue(on.bids[0].isWall)
        assertFalse(on.bids[1].isWall)
        val far = ObBook.build(bids, emptyList(), 8, spoofOn = true, bidWall = 90.0, askWall = 0.0)
        assertTrue(far.bids.none { it.isWall })
    }

    @Test
    fun maxQSwitchesWithCumulative() {
        val bids = listOf(1.0 to 2.0, 0.9 to 3.0)
        val b = ObBook.build(bids, emptyList(), 8, false, 0.0, 0.0)
        assertEquals(3.0, b.maxQ(false), 1e-9)
        assertEquals(5.0, b.maxQ(true), 1e-9)
    }

    @Test
    fun fmtQtyUsAndCompact() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("12.50", ObBook.fmtQty(12.5))
            assertEquals("1.5K", ObBook.fmtQty(1500.0))
            assertEquals("2.00M", ObBook.fmtQty(2_000_000.0))
        } finally {
            Locale.setDefault(prev)
        }
    }

    @Test
    fun spoofThresholdAndRowsHelper() {
        assertFalse(ObBook.spoofActive(49))
        assertTrue(ObBook.spoofActive(50))
        assertEquals(8, ObBook.rows(false))
        assertEquals(16, ObBook.rows(true))
    }
}
