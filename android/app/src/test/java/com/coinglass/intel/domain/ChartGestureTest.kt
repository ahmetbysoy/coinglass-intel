package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChartGestureTest {
    @Test
    fun horizontalIsPan() {
        assertEquals(ChartGesture.Drag.PAN, ChartGesture.dragKind(24f, 3f, 40f, 300f))
        assertEquals(ChartGesture.Drag.PAN, ChartGesture.dragKind(-18f, 4f, 40f, 300f))
    }

    @Test
    fun gutterVerticalIsPriceZoom() {
        assertEquals(ChartGesture.Drag.PRICE_ZOOM, ChartGesture.dragKind(2f, 20f, 310f, 300f))
        assertEquals(ChartGesture.Drag.PRICE_ZOOM, ChartGesture.dragKind(-1f, -16f, 350f, 300f))
    }

    @Test
    fun plotVerticalStaysPan() {
        assertEquals(ChartGesture.Drag.PAN, ChartGesture.dragKind(2f, 20f, 40f, 300f))
    }

    @Test
    fun slopThreshold() {
        assertFalse(ChartGesture.pastSlop(3f, 4f, 8f))
        assertTrue(ChartGesture.pastSlop(6f, 8f, 8f))
    }

    @Test
    fun doubleTapWindow() {
        assertTrue(ChartGesture.isDoubleTap(1_000L, 800L))
        assertFalse(ChartGesture.isDoubleTap(1_000L, 600L))
        assertFalse(ChartGesture.isDoubleTap(1_000L, 0L))
    }

    @Test
    fun flingOnlyOnFlick() {
        assertFalse(ChartGesture.shouldFling(120f))
        assertTrue(ChartGesture.shouldFling(800f))
        assertTrue(ChartGesture.shouldFling(-900f))
    }

    @Test
    fun flingSignMatchesPan() {
        val slot = 6f
        val v = ChartGesture.flingBarsPerFrame(1200f, slot)
        assertTrue(v > 0f)
        val left = ChartGesture.flingBarsPerFrame(-1200f, slot)
        assertTrue(left < 0f)
        assertEquals(1200f / slot / 60f, v, 1e-4f)
    }

    @Test
    fun flingDecaysAndStops() {
        var v = 4f
        var n = 0
        while (!ChartGesture.flingDone(v) && n < 80) {
            v = ChartGesture.decay(v)
            n++
        }
        assertTrue(n in 10..60)
        assertTrue(abs(v) < ChartGesture.FLING_MIN_BARS)
    }

    @Test
    fun flingBadSlotIsZero() {
        assertEquals(0f, ChartGesture.flingBarsPerFrame(800f, 0f), 0f)
    }
}
