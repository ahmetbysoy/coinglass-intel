package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RadarTest {
    private fun row(
        symbol: String,
        score: Double = 10.0,
        risk: Int? = 40,
        spoof: Int = 10,
        rr: Double = 1.2,
        vol: Double = 1.0,
        coverage: Double = 50.0,
        discovery: Boolean = false,
    ) = RadarRow(
        symbol = symbol,
        price = 1.0,
        score = score,
        direction = "BULLISH",
        coverage = coverage,
        updatedAt = 1L,
        risk = risk,
        spoof = spoof,
        netRr = rr,
        vol24 = vol,
        grade = if (discovery) "B" else "",
        candles = "[]",
        discovery = discovery,
    )

    @Test
    fun discoveryNullRiskSurvivesRiskFilter() {
        val disc = row("FOOUSDT", risk = null, discovery = true)
        val watch = row("BARUSDT", risk = 80)
        val q = RadarQuery(maxRisk = 50)
        val out = Radar.rank(listOf(disc, watch), q)
        assertTrue(out.any { it.symbol == "FOOUSDT" })
        assertFalse(out.any { it.symbol == "BARUSDT" })
    }

    @Test
    fun queryMatchesSymbolCaseInsensitive() {
        val rows = listOf(row("FOOUSDT"), row("XYZUSDT"))
        val out = Radar.rank(rows, RadarQuery(text = "foo"))
        assertEquals(listOf("FOOUSDT"), out.map { it.symbol })
    }

    @Test
    fun sortDirectionFlips() {
        val rows = listOf(row("LOWUSDT", score = 5.0), row("HIUSDT", score = 30.0))
        val desc = Radar.rank(rows, RadarQuery(sort = ScanSort.ABS_SCORE, sortDesc = true))
        val asc = Radar.rank(rows, RadarQuery(sort = ScanSort.ABS_SCORE, sortDesc = false))
        assertEquals(listOf("HIUSDT", "LOWUSDT"), desc.map { it.symbol })
        assertEquals(listOf("LOWUSDT", "HIUSDT"), asc.map { it.symbol })
    }

    @Test
    fun spoofAndRrFilters() {
        val keep = row("KEEPUSDT", spoof = 20, rr = 1.5)
        val dropS = row("SPOOFUSDT", spoof = 80, rr = 2.0)
        val dropR = row("THINUSDT", spoof = 10, rr = 0.2)
        val out = Radar.rank(listOf(keep, dropS, dropR), RadarQuery(maxSpoof = 49, minRr = 1.0))
        assertEquals(listOf("KEEPUSDT"), out.map { it.symbol })
    }

    @Test
    fun hotNeedsScoreSpoofCoverageRr() {
        val ok = row("HOTUSDT", score = 25.0, spoof = 20, rr = 1.4, coverage = 55.0)
        val weak = row("MEHUSDT", score = 25.0, spoof = 60, rr = 1.4, coverage = 55.0)
        assertEquals(listOf("HOTUSDT"), Radar.hot(listOf(ok, weak)).map { it.symbol })
    }

    @Test
    fun cycleMinRrAndSort() {
        assertEquals(1.0, Radar.cycleMinRr(0.0), 0.0)
        assertEquals(2.0, Radar.cycleMinRr(1.0), 0.0)
        assertEquals(0.0, Radar.cycleMinRr(2.0), 0.0)
        assertEquals(ScanSort.RISK, Radar.nextSort(ScanSort.ABS_SCORE))
        assertEquals(ScanSort.ABS_SCORE, Radar.nextSort(ScanSort.VOL))
    }

    @Test
    fun signedFormatUsesUsLocale() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("+12.5", Radar.fmtSigned(12.5, 1))
            assertEquals("-1.00", Radar.fmtFixed(-1.0, 2))
        } finally {
            Locale.setDefault(prev)
        }
    }

    @Test
    fun parseClosesReadsCloseIndex() {
        val json = "[[0,1,2,3,10.5,5],[1,1,2,3,11.0,5]]"
        assertEquals(listOf(10.5, 11.0), Radar.parseCloses(json))
        assertTrue(Radar.parseCloses("[]").isEmpty())
        assertTrue(Radar.parseCloses("nope").isEmpty())
    }

    @Test
    fun noHardcodedMascotInRank() {
        val out = Radar.rank(listOf(row("XYZUSDT")), RadarQuery())
        assertEquals("XYZUSDT", out.single().symbol)
        assertFalse(out.any { it.symbol.contains("BTC") || it.symbol.contains("ETH") })
    }
}
