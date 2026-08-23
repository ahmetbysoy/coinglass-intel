package com.coinglass.intel.widget

import com.coinglass.intel.data.db.ScoreSnapEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPicksTest {
    private fun snap(sym: String, score: Double) = ScoreSnapEntity(
        symbol = sym,
        price = 1.0,
        score = score,
        direction = if (score >= 0) "BULLISH" else "BEARISH",
        sl = 0.0,
        tp = 0.0,
        coverage = 50.0,
        updatedAt = 1L,
    )

    @Test
    fun emptyWatchlistStaysEmpty() {
        assertTrue(WidgetPicks.top(emptyList()).isEmpty())
    }

    @Test
    fun ranksByAbsScoreOnlyFromInput() {
        val top = WidgetPicks.top(
            listOf(snap("AAAUSDT", 12.0), snap("BBBUSDT", -40.0), snap("CCCUSDT", 5.0)),
            2,
        )
        assertEquals(listOf("BBBUSDT", "AAAUSDT"), top.map { it.symbol })
        assertTrue(top.none { it.symbol == "BTCUSDT" && it.score == 0.0 })
    }
}
