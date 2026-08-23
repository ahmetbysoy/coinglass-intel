package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import com.coinglass.intel.domain.model.NamedPrice
import com.coinglass.intel.domain.model.ScoreInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChartSeriesTest {
    private fun bar(t: Double, close: Double, vol: Double = 10.0) = Candle(
        openTime = t, open = close - 0.1, high = close + 0.2, low = close - 0.2, close = close, volume = vol,
    )

    @Test
    fun mergeKeepsRestWhenLiveHasOneBar() {
        val rest = (1..20).map { i -> bar(i * 60_000.0, 100.0 + i) }
        val live = listOf(bar(20 * 60_000.0, 130.0), bar(21 * 60_000.0, 131.0))
        val m = ChartSeries.merge(rest, live)
        assertEquals(21, m.size)
        assertEquals(130.0, m.first { it.openTime == 20 * 60_000.0 }.close, 0.0)
        assertEquals(131.0, m.last().close, 0.0)
        assertEquals(1 * 60_000.0, m.first().openTime, 0.0)
    }

    @Test
    fun mergeEmptyLiveReturnsRest() {
        val rest = listOf(bar(1.0, 10.0), bar(2.0, 11.0))
        val m = ChartSeries.merge(rest, emptyList())
        assertEquals(2, m.size)
        assertEquals(10.0, m[0].close, 0.0)
    }

    @Test
    fun visibleTakesLastNinety() {
        val all = (1..200).map { i -> bar(i.toDouble(), i.toDouble()) }
        val v = ChartSeries.visible(all)
        assertEquals(90, v.size)
        assertEquals(111.0, v.first().openTime, 0.0)
        assertEquals(200.0, v.last().openTime, 0.0)
    }

    @Test
    fun oneMinuteMoveMovesConfluence() {
        fun ramp(n: Int, start: Double, step: Double) = (0 until n).map { i ->
            val c = start + i * step
            Candle(
                openTime = i * 60_000.0,
                open = c - step / 2,
                high = c + abs(step) + 0.05,
                low = c - abs(step) - 0.05,
                close = c,
                volume = 20.0,
            )
        }
        fun flat(n: Int, px: Double) = (0 until n).map { i ->
            Candle(i * 60_000.0, px, px + 0.01, px - 0.01, px, 20.0)
        }
        fun input(k1: List<Candle>, k3: List<Candle>, k5: List<Candle>, k15: List<Candle>) = ScoreInput(
            symbol = "TESTUSDT",
            prices = listOf(NamedPrice("t", 100.0)),
            chg24 = 0.0,
            vol24 = 50_000_000.0,
            oi = 1_000.0,
            oiHist = listOf(1000.0, 1000.0),
            orderBooks = emptyMap(),
            trades = emptyList(),
            fundingRates = listOf(0.0),
            lsRatio = 1.0,
            takerHist = emptyList(),
            klines5m = k5,
            klines15m = k15,
            klines1h = emptyList(),
            klines1m = k1,
            klines3m = k3,
        )
        val onlyHigher = MarketScorer.score(input(emptyList(), emptyList(), flat(40, 100.0), flat(40, 100.0)))
        val withFast = MarketScorer.score(input(ramp(40, 90.0, 0.6), ramp(40, 90.0, 0.5), flat(40, 100.0), flat(40, 100.0)))
        assertTrue(withFast.confluence > onlyHigher.confluence + 0.2)
        assertEquals(setOf("1m", "3m", "5m", "15m"), withFast.tfPreds.map { it.timeframe }.toSet())
        assertTrue(withFast.rsiTf.containsKey("1m"))
    }

    @Test
    fun calibratorMapsMomToMomentumNotVolume() {
        val ens = WeightCalibrator.toEnsemble(mapOf("Mom" to 40.0, "Vol" to 10.0, "OB" to 50.0))
        assertTrue(ens.containsKey("momentum"))
        assertTrue(ens.containsKey("volume_signal"))
        assertTrue(ens.getValue("momentum") > ens.getValue("volume_signal"))
    }
}
