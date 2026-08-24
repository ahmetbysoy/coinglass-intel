package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import com.coinglass.intel.domain.model.V4Report
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
        val ready = ChartContent.of(src, false, emptyList(), LiqHeat.Grid(), Smc.EMPTY, "e") as ChartContent.Ready
        src.add(Candle(3.0, 1.0, 2.0, 1.0, 1.5, 1.0))
        assertEquals(2, ready.data.candles.size)
    }

    @Test
    fun nullReportMapsToEmptyLevelsAndSignals() {
        val r: V4Report? = null
        assertSame(ChartLevels.EMPTY, r.toChartLevels())
        assertSame(ChartSignals.EMPTY, r.toChartSignals())
        assertEquals(0.0, ChartLevels.EMPTY.entry, 0.0)
        assertEquals(0.0, ChartLevels.EMPTY.sl, 0.0)
        assertEquals(0.0, ChartLevels.EMPTY.tp, 0.0)
        assertEquals(Divergence.NONE, ChartSignals.EMPTY.divergence)
        assertEquals("", ChartSignals.EMPTY.grade)
    }

    @Test
    fun reportMapsLevelsAndSignalsOnce() {
        val r = V4Report(
            symbol = "TESTUSDT",
            price = 100.5,
            chg24 = 0.0,
            vol24 = 0.0,
            direction = "BULLISH",
            totalScore = 12.0,
            confluence = 40.0,
            risk = 20,
            spoof = 51,
            strategy = "x",
            strategyWarnings = emptyList(),
            forecasts = emptyMap(),
            component = emptyMap(),
            signals = emptyMap(),
            text = "x",
            sl = 98.0,
            tp = 104.0,
            support = 97.0,
            resistance = 106.0,
            bidWall = 99.0,
            askWall = 101.0,
            poc = 100.0,
            grade = "B",
            verdict = "UZUN",
            divergeType = "bearish-rsi",
        )
        val lv = r.toChartLevels()
        val sg = r.toChartSignals()
        assertEquals(100.5, lv.entry, 0.0)
        assertEquals(98.0, lv.sl, 0.0)
        assertEquals(104.0, lv.tp, 0.0)
        assertEquals(97.0, lv.support, 0.0)
        assertEquals(106.0, lv.resistance, 0.0)
        assertEquals(99.0, lv.bidWall, 0.0)
        assertEquals(101.0, lv.askWall, 0.0)
        assertEquals(100.0, lv.poc, 0.0)
        assertEquals(51, sg.spoofScore)
        assertTrue(sg.spoofSkip)
        assertEquals(Divergence.BEAR, sg.divergence)
        assertEquals("B", sg.grade)
        assertEquals("UZUN", sg.verdict)
    }
}
