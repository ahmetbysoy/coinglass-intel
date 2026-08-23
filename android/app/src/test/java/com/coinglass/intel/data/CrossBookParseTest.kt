package com.coinglass.intel.data

import com.coinglass.intel.data.ws.parseBybit
import com.coinglass.intel.data.ws.parseOkx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossBookParseTest {
    @Test
    fun bybitSnapshot() {
        val ev = parseBybit(
            """{"topic":"orderbook.1.ETHUSDT","data":{"b":[["3500.1","2.5"]],"a":[["3500.4","1.1"]]}}""",
            "ETHUSDT",
        )
        assertNotNull(ev)
        assertEquals("ETHUSDT", ev!!.symbol)
        assertEquals("Bybit", ev.exchange)
        @Suppress("UNCHECKED_CAST")
        val bids = ev.extra["bids"] as List<Pair<Double, Double>>
        assertEquals(3500.1, bids.first().first, 1e-6)
        assertTrue(ev.price > 3500)
    }

    @Test
    fun okxBbo() {
        val ev = parseOkx(
            """{"data":[{"bidPx":"100.2","bidSz":"3","askPx":"100.4","askSz":"2"}]}""",
            "ABCUSDT",
        )
        assertNotNull(ev)
        assertEquals("OKX", ev!!.exchange)
        assertEquals(100.3, ev.price, 1e-6)
    }
}
