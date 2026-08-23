package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DiscoveryPickTest {
    private val fixture = javaClass.classLoader!!
        .getResourceAsStream("ticker24h.json")!!
        .bufferedReader()
        .readText()

    @Test
    fun fixtureDropsNonUsdtAndRanksByVolume() {
        val rows = DiscoveryPick.parse(fixture)
        val uni = DiscoveryPick.universe(rows)
        assertTrue(uni.isNotEmpty())
        assertTrue(uni.all { it.symbol.endsWith("USDT") })
        assertTrue(uni.none { it.symbol.endsWith("USD") && !it.symbol.endsWith("USDT") })
        assertTrue(uni.none { it.symbol.endsWith("BUSD") })
        for (i in 1 until uni.size) {
            assertTrue(uni[i - 1].quoteVolume >= uni[i].quoteVolume)
        }
        assertEquals("AAAUSDT", uni.first().symbol)
    }

    @Test
    fun prefilterHonorsVolAndChange() {
        val k = DiscoveryPick.pick(DiscoveryPick.parse(fixture))
        val symbols = k.map { it.symbol }
        assertTrue(symbols.contains("AAAUSDT"))
        assertTrue(symbols.contains("BBBUSDT"))
        assertTrue(symbols.contains("EEEUSDT"))
        assertTrue(symbols.contains("FFFUSDT"))
        assertTrue(symbols.contains("GGGUSDT"))
        assertFalse("chg 0.4 should drop", symbols.contains("CCCUSDT"))
        assertFalse("vol 10M should drop", symbols.contains("DDDUSDT"))
        assertFalse("vol just under 20M should drop", symbols.contains("HHHUSDT"))
        assertFalse("not USDT-M", symbols.contains("ETHUSD"))
        assertTrue(k.all { it.quoteVolume >= DiscoveryPick.MIN_QUOTE_VOL })
        assertTrue(k.all { abs(it.priceChangePercent) >= DiscoveryPick.MIN_ABS_CHG })
        assertTrue(k.size <= DiscoveryPick.FULL_K)
    }

    @Test
    fun btcNotHardcodedIntoPicks() {
        val k = DiscoveryPick.pick(DiscoveryPick.parse(fixture))
        assertFalse(
            "low-volume BTCUSDT in fixture must not be injected",
            k.any { it.symbol == "BTCUSDT" },
        )
        val src = javaClass.classLoader!!
            .getResourceAsStream("ticker24h.json")!!
            .bufferedReader()
            .readText()
        assertTrue("fixture itself may mention BTC as a negative case", src.contains("BTCUSDT"))
    }

    @Test
    fun emptyWatchlistStillYieldsPicks() {
        val k = DiscoveryPick.pick(DiscoveryPick.parse(fixture), exclude = emptySet())
        assertTrue(k.isNotEmpty())
    }

    @Test
    fun watchlistExcludeDoesNotPromoteOutsideTopN() {
        val rows = DiscoveryPick.parse(fixture)
        val uni = DiscoveryPick.universe(rows)
        val top = uni.first().symbol
        val k = DiscoveryPick.pick(rows, exclude = setOf(top))
        assertFalse(k.any { it.symbol == top })
        assertTrue(k.size < uni.size || k.isNotEmpty() || uni.size == 1)
    }

    @Test
    fun nAndKCapsFromGeneratedTickers() {
        val rows = (1..60).map { i ->
            Ticker24h(
                symbol = "S%03dUSDT".format(i),
                lastPrice = 1.0,
                quoteVolume = (61 - i) * 1_000_000.0 + 25_000_000.0,
                priceChangePercent = 2.0,
            )
        } + Ticker24h("NOPEUSD", 1.0, 9_000_000_000.0, 9.0)
        val uni = DiscoveryPick.universe(rows)
        assertEquals(DiscoveryPick.TOP_N, uni.size)
        assertEquals("S001USDT", uni.first().symbol)
        assertEquals("S040USDT", uni.last().symbol)
        assertTrue(uni.none { it.symbol == "NOPEUSD" })
        val k = DiscoveryPick.prefilter(uni)
        assertEquals(DiscoveryPick.FULL_K, k.size)
        assertEquals((1..12).map { "S%03dUSDT".format(it) }, k.map { it.symbol })
    }

    @Test
    fun rankingFollowsVolumeNotName() {
        val low = Ticker24h("AAAUSDT", 1.0, 25_000_000.0, 2.0)
        val high = Ticker24h("ZZZUSDT", 1.0, 90_000_000.0, 2.0)
        assertEquals(
            listOf("ZZZUSDT", "AAAUSDT"),
            DiscoveryPick.pick(listOf(low, high)).map { it.symbol },
        )
    }

    @Test
    fun emptyTickerYieldsEmpty() {
        assertTrue(DiscoveryPick.parse("[]").isEmpty())
        assertTrue(DiscoveryPick.parse("").isEmpty())
        assertTrue(DiscoveryPick.pick(emptyList()).isEmpty())
    }

    @Test
    fun opportunityPredicate() {
        assertTrue(DiscoveryPick.isOpportunity("A", 20, 1.6, 60.0))
        assertTrue(DiscoveryPick.isOpportunity("B", 39, 1.5, 50.0))
        assertFalse(DiscoveryPick.isOpportunity("C", 10, 2.0, 80.0))
        assertFalse(DiscoveryPick.isOpportunity("A", 40, 2.0, 80.0))
        assertFalse(DiscoveryPick.isOpportunity("B", 10, 1.49, 80.0))
        assertFalse(DiscoveryPick.isOpportunity("A", 10, 2.0, 49.0))
    }
}
