package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun panLeftFromLiveStaysLive() {
        assertEquals(0, ChartViewport.pan(0, -20, visible = 90, total = 200))
    }

    @Test
    fun panClampsAtOldest() {
        val max = ChartViewport.maxOffset(200, 90)
        assertEquals(110, max)
        assertEquals(110, ChartViewport.pan(100, 50, visible = 90, total = 200))
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
    fun zoomOutIncreasesVisible() {
        val (vis, off) = ChartViewport.zoom(90, 0.5f, 1f, 0, 400)
        assertEquals(180, vis)
        assertEquals(0, off)
    }

    @Test
    fun zoomFocusLeftKeepsLeftSide() {
        val (vis, off) = ChartViewport.zoom(
            visible = 100,
            factor = 2f,
            focus01 = 0f,
            offsetFromEnd = 20,
            total = 300,
        )
        assertEquals(50, vis)
        val oldLeftFromEnd = 20 + 100
        val newLeftFromEnd = off + vis
        assertEquals(oldLeftFromEnd, newLeftFromEnd)
    }

    @Test
    fun zoomClampsMinMax() {
        val (lo, _) = ChartViewport.zoom(30, 8f, 0.5f, 0, 600)
        assertEquals(ChartViewport.MIN_BARS, lo)
        val (hi, _) = ChartViewport.zoom(200, 0.1f, 0.5f, 0, 600)
        assertEquals(ChartViewport.MAX_BARS, hi)
    }

    @Test
    fun tinyPinchDoesNotRoundAway() {
        val first = ChartViewport.zoomAccum(90, 1f, 1.02f, 1f, 0, 400)
        assertEquals(90, first.visible)
        assertTrue(first.remain > 1f)
        var acc = first
        repeat(8) {
            acc = ChartViewport.zoomAccum(acc.visible, acc.remain, 1.02f, 1f, acc.offset, 400)
        }
        assertTrue("accum pinch must eventually commit, vis=${acc.visible}", acc.visible < 90)
    }

    @Test
    fun pinchOutCommitsFewerBars() {
        val z = ChartViewport.zoomAccum(90, 1f, 1.2f, 1f, 0, 400)
        assertEquals(75, z.visible)
        assertEquals(1f, z.remain)
        assertEquals(0, z.offset)
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

    @Test
    fun pixelsToBarsPositiveIsOlder() {
        val a = ChartViewport.pixelsToBars(12f, 4f, 0f)
        assertEquals(3, a.bars)
        assertEquals(0f, a.remain, 1e-5f)
    }

    @Test
    fun pixelsToBarsKeepsRemainder() {
        val a = ChartViewport.pixelsToBars(3f, 4f, 0f)
        assertEquals(0, a.bars)
        assertEquals(0.75f, a.remain, 1e-5f)
        val b = ChartViewport.pixelsToBars(2f, 4f, a.remain)
        assertEquals(1, b.bars)
        assertEquals(0.25f, b.remain, 1e-4f)
    }

    @Test
    fun pixelsToBarsNegativeTowardLive() {
        val a = ChartViewport.pixelsToBars(-9f, 4f, 0f)
        assertEquals(-2, a.bars)
        assertEquals(-0.25f, a.remain, 1e-4f)
    }

    @Test
    fun pixelsToBarsBadSlotIsNoop() {
        val a = ChartViewport.pixelsToBars(10f, 0f, 0.3f)
        assertEquals(0, a.bars)
        assertEquals(0f, a.remain, 0f)
    }

    @Test
    fun manySmallMovesBecomeAPan() {
        var off = 0
        var rem = 0f
        repeat(20) {
            val a = ChartViewport.pixelsToBars(4f, 8f, rem)
            rem = a.remain
            off = ChartViewport.pan(off, a.bars, 90, 400)
        }
        assertEquals(10, off)
        assertEquals(0f, rem, 1e-5f)
    }

    @Test
    fun swipeTowardLiveStopsAtZero() {
        var off = 3
        var rem = 0f
        repeat(12) {
            val a = ChartViewport.pixelsToBars(-4f, 8f, rem)
            rem = a.remain
            off = ChartViewport.pan(off, a.bars, 90, 400)
        }
        assertEquals(0, off)
    }

    @Test
    fun badZoomFactorIsNoop() {
        val (v, o) = ChartViewport.zoom(90, 0f, 0.5f, 12, 200)
        assertEquals(90, v)
        assertEquals(12, o)
    }
}
