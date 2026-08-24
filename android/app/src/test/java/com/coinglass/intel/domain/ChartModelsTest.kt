package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartModelsTest {
    @Test
    fun divergenceFromRaw() {
        assertEquals(Divergence.BEAR, Divergence.from("bearish-rsi"))
        assertEquals(Divergence.BULL, Divergence.from("BULL hid"))
        assertEquals(Divergence.NONE, Divergence.from(""))
        assertEquals(Divergence.NONE, Divergence.from("flat"))
    }

    @Test
    fun spoofThresholdIsDomainConstant() {
        assertFalse(ChartSignals(49).spoofSkip)
        assertTrue(ChartSignals(50).spoofSkip)
        assertEquals(50, ChartSignals.SPOOF_THRESHOLD)
    }

    @Test
    fun overlayPackRoundTrip() {
        val set = setOf(Overlay.HEAT, Overlay.VOL, Overlay.EMA)
        assertEquals(set, overlaySet(set.pack()))
        assertEquals(DEFAULT_OVERLAYS, overlaySet(DEFAULT_OVERLAYS.pack()))
        assertTrue(overlaySet(0).isEmpty())
    }

    @Test
    fun contentReadyNeedsTwoBars() {
        val bars = listOf(
            Candle(1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
            Candle(2.0, 1.0, 1.0, 1.0, 1.0, 1.0),
        )
        val ready = ChartContent.of(bars, loading = true, restErrors = listOf("x"), LiqHeat.Grid(), Smc.Report(), "err")
        assertTrue(ready is ChartContent.Ready)
        assertEquals(2, (ready as ChartContent.Ready).data.candles.size)
    }

    @Test
    fun contentErrorOnlyWhenEmptyAndNotLoading() {
        val err = ChartContent.of(emptyList(), loading = false, restErrors = listOf("418"), LiqHeat.Grid(), Smc.Report(), "Grafik yüklenemedi")
        assertTrue(err is ChartContent.Error)
        val load = ChartContent.of(emptyList(), loading = true, restErrors = listOf("418"), LiqHeat.Grid(), Smc.Report(), "x")
        assertEquals(ChartContent.Loading, load)
        val wait = ChartContent.of(emptyList(), loading = false, restErrors = emptyList(), LiqHeat.Grid(), Smc.Report(), "x")
        assertEquals(ChartContent.Loading, wait)
    }

    @Test
    fun contentSnapshotsList() {
        val src = mutableListOf(Candle(1.0, 1.0, 2.0, 1.0, 1.5, 1.0), Candle(2.0, 1.0, 2.0, 1.0, 1.5, 1.0))
        val ready = ChartContent.of(src, false, emptyList(), LiqHeat.Grid(), Smc.Report(), "e") as ChartContent.Ready
        src.add(Candle(3.0, 1.0, 2.0, 1.0, 1.5, 1.0))
        assertEquals(2, ready.data.candles.size)
    }
}
