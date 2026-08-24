package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartLayoutTest {
    private fun g(
        shown: Int = 80,
        following: Boolean = true,
        showVol: Boolean = true,
        showHeat: Boolean = true,
        w: Float = 360f,
        h: Float = 640f,
        shift: Float = 0f,
    ) = ChartLayout.geo(
        width = w,
        height = h,
        shown = shown,
        following = following,
        showVol = showVol,
        showHeat = showHeat,
        priceGutter = 62f,
        heatGutter = 24f,
        timeH = 14f,
        shiftBars = shift,
    )

    @Test
    fun guttersLeavePlot() {
        val geo = g()
        assertEquals(360f, geo.priceW + geo.heatW + geo.plotWidth, 0.01f)
        assertEquals(geo.plotLeft + geo.plotWidth, geo.heatLeft, 0.01f)
        assertEquals(geo.heatLeft + geo.heatW, geo.priceLeft, 0.01f)
        assertTrue(geo.plotWidth > 200f)
        assertEquals(62f, geo.priceW, 0.01f)
        assertEquals(24f, geo.heatW, 0.01f)
    }

    @Test
    fun followingHasRightPad() {
        val live = g(following = true)
        val hist = g(following = false)
        assertEquals(85, live.slots)
        assertEquals(80, hist.slots)
        assertTrue(live.slot < hist.slot)
        val last = live.xCenter(79)
        assertTrue("last candle must sit left of heat, last=$last heat=${live.heatLeft}", last < live.heatLeft)
    }

    @Test
    fun hitIndexMatchesDrawnCenter() {
        val geo = g(following = false, showHeat = false)
        for (i in 0 until 80) {
            assertEquals("i=$i x=${geo.xCenter(i)}", i, geo.hitIndex(geo.xCenter(i)))
        }
    }

    @Test
    fun tapOnPriceGutterIsNotACandle() {
        val geo = g()
        assertNull(geo.hitIndex(geo.priceLeft + 4f))
        assertTrue(geo.inPriceScale(geo.priceLeft + 1f))
        assertFalse(geo.inPlotX(geo.priceLeft + 1f))
    }

    @Test
    fun padHitClampsToLastCandle() {
        val geo = g(following = true)
        val padX = geo.xCenter(84)
        assertTrue(geo.inPlotX(padX))
        assertEquals(79, geo.candleIndex(padX, lastIdx = 79))
    }

    @Test
    fun heatOffGivesPlotTheSpace() {
        val on = g(showHeat = true)
        val off = g(showHeat = false)
        assertEquals(0f, off.heatW, 0f)
        assertTrue(off.plotWidth > on.plotWidth)
    }

    @Test
    fun timeLabelsAreSpread() {
        val idx = ChartLayout.timeLabelIndices(80, 4)
        assertEquals(4, idx.size)
        assertEquals(0, idx.first())
        assertEquals(79, idx.last())
        assertEquals(listOf(0, 26, 53, 79), idx)
    }

    @Test
    fun shiftMovesHitWithDraw() {
        val geo = g(following = false, shift = 0.4f)
        val i = 10
        assertEquals(i, geo.hitIndex(geo.xCenter(i)))
    }

    @Test
    fun emptyShownStillPositiveSlot() {
        val geo = g(shown = 0, following = false)
        assertTrue(geo.slot > 0f)
        assertEquals(1, geo.slots)
    }
}
