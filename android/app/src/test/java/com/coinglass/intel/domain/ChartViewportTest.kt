package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartViewportTest {
    @Test
    fun liveWindowIsTail() {
        val w = ChartViewport.window(total = 200, visible = 90, offsetFromEnd = 0)
        assertEquals(110, w.start)
        assertEquals(200, w.endExclusive)
        assertEquals(90, w.size)
    }

    @Test
    fun panRevealsOlderBars() {
        val off = ChartViewport.pan(0, 10, visible = 90, total = 200)
        assertEquals(10, off)
        val w = ChartViewport.window(200, 90, off)
        assertEquals(100, w.start)
        assertEquals(190, w.endExclusive)
    }

    @Test
    fun zoomKeepsFocusNearRight() {
        val (vis, off) = ChartViewport.zoom(
            visible = 90,
            factor = 2f,
            focus01 = 1f,
            offsetFromEnd = 0,
            total = 200,
        )
        assertEquals(45, vis)
        assertEquals(0, off)
    }

    @Test
    fun newBarsDoNotStealWhenNotFollowing() {
        assertEquals(12, ChartViewport.holdOnAppend(10, following = false, added = 2))
        assertEquals(10, ChartViewport.holdOnAppend(10, following = true, added = 2))
    }

    @Test
    fun liveAddsRightPad() {
        assertEquals(95, ChartViewport.slotCount(90, following = true))
        assertEquals(90, ChartViewport.slotCount(90, following = false))
    }

    @Test
    fun emptyTotalIsEmptyWindow() {
        val w = ChartViewport.window(0, 90, 0)
        assertEquals(0, w.size)
        assertTrue(w.start == 0 && w.endExclusive == 0)
    }
}
