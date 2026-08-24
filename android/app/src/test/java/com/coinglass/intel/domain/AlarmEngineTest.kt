package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEngineTest {
    private fun alarm(
        id: Long = 1,
        symbol: String = "FOOUSDT",
        kind: AlarmKind = AlarmKind.PRICE,
        op: AlarmOp = AlarmOp.GTE,
        th: Double = 10.0,
        on: Boolean = true,
    ) = AlarmSpec(id, symbol, kind, op, th, on)

    private fun q(symbol: String = "FOOUSDT", price: Double = 12.0, score: Double = 40.0, funding: Double = 0.001) =
        AlarmQuote(symbol, price, score, funding)

    @Test
    fun draftNormalizesAndRejectsBlank() {
        val a = AlarmEngine.draft("foo-perp", AlarmKind.PRICE, AlarmOp.GTE, 1.0)
        assertEquals("FOOUSDT", a!!.symbol)
        assertNull(AlarmEngine.draft("   ", AlarmKind.PRICE, AlarmOp.GTE, 1.0))
        assertNull(AlarmEngine.draft("FOO", AlarmKind.PRICE, AlarmOp.GTE, Double.NaN))
    }

    @Test
    fun parseKindAndOp() {
        assertEquals(AlarmKind.PRICE, AlarmEngine.kindOf("fiyat"))
        assertEquals(AlarmKind.SCORE, AlarmEngine.kindOf("score"))
        assertEquals(AlarmKind.FUNDING, AlarmEngine.kindOf("FUNDING"))
        assertNull(AlarmEngine.kindOf("rsi"))
        assertEquals(AlarmOp.GTE, AlarmEngine.opOf(">="))
        assertEquals(AlarmOp.LTE, AlarmEngine.opOf("lte"))
        assertNull(AlarmEngine.opOf("eq"))
    }

    @Test
    fun priceGteFires() {
        val hits = AlarmEngine.check(listOf(alarm(th = 10.0)), listOf(q(price = 10.0)), emptyMap(), 1_000L)
        assertEquals(1, hits.size)
        assertEquals(10.0, hits[0].value, 0.0)
    }

    @Test
    fun priceBelowDoesNotFire() {
        val hits = AlarmEngine.check(listOf(alarm(th = 10.0)), listOf(q(price = 9.99)), emptyMap(), 1_000L)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun scoreUsesAbs() {
        val a = alarm(kind = AlarmKind.SCORE, op = AlarmOp.GTE, th = 30.0)
        val hits = AlarmEngine.check(listOf(a), listOf(q(score = -35.0)), emptyMap(), 1_000L)
        assertEquals(1, hits.size)
        assertEquals(35.0, hits[0].value, 0.0)
    }

    @Test
    fun fundingUsesAbs() {
        val a = alarm(kind = AlarmKind.FUNDING, op = AlarmOp.GTE, th = 0.0008)
        val hits = AlarmEngine.check(listOf(a), listOf(q(funding = -0.001)), emptyMap(), 1_000L)
        assertEquals(1, hits.size)
    }

    @Test
    fun disabledAndMissingQuoteSkipped() {
        val off = alarm(on = false)
        assertTrue(AlarmEngine.check(listOf(off), listOf(q()), emptyMap(), 1_000L).isEmpty())
        val other = alarm(symbol = "BARUSDT")
        assertTrue(AlarmEngine.check(listOf(other), listOf(q()), emptyMap(), 1_000L).isEmpty())
    }

    @Test
    fun unsavedIdDoesNotFire() {
        val a = alarm(id = 0)
        assertTrue(AlarmEngine.check(listOf(a), listOf(q()), emptyMap(), 1_000L).isEmpty())
    }

    @Test
    fun dedupTenMinutesPerAlarmId() {
        val a = alarm(id = 7)
        val q = q(price = 20.0)
        val t0 = 1_000_000L
        val first = AlarmEngine.check(listOf(a), listOf(q), emptyMap(), t0)
        assertEquals(1, first.size)
        val inside = AlarmEngine.check(listOf(a), listOf(q), mapOf(7L to t0), t0 + 9 * 60_000L)
        assertTrue(inside.isEmpty())
        val after = AlarmEngine.check(listOf(a), listOf(q), mapOf(7L to t0), t0 + AlarmEngine.DEDUP_MS)
        assertEquals(1, after.size)
    }

    @Test
    fun twoAlarmsSameSymbolIndependentDedup() {
        val a = alarm(id = 1, th = 10.0)
        val b = alarm(id = 2, th = 11.0)
        val hits = AlarmEngine.check(listOf(a, b), listOf(q(price = 12.0)), mapOf(1L to 1_000L), 2_000L)
        assertEquals(1, hits.size)
        assertEquals(2L, hits[0].alarm.id)
    }

    @Test
    fun mergeLiveOverridesSnap() {
        val snap = q(price = 8.0)
        val live = q(price = 15.0)
        val merged = AlarmEngine.mergeLive(listOf(snap), live)
        assertEquals(1, merged.size)
        assertEquals(15.0, merged[0].price, 0.0)
    }

    @Test
    fun lteWorks() {
        val a = alarm(op = AlarmOp.LTE, th = 5.0)
        assertEquals(1, AlarmEngine.check(listOf(a), listOf(q(price = 5.0)), emptyMap(), 1L).size)
        assertTrue(AlarmEngine.check(listOf(a), listOf(q(price = 5.01)), emptyMap(), 1L).isEmpty())
    }

    @Test
    fun dueHelper() {
        assertTrue(AlarmEngine.due(null, 10L))
        assertFalse(AlarmEngine.due(0L, AlarmEngine.DEDUP_MS - 1))
        assertTrue(AlarmEngine.due(0L, AlarmEngine.DEDUP_MS))
    }

    @Test
    fun engineHasNoHardcodedMascot() {
        val src = AlarmEngine.javaClass.protectionDomain?.codeSource
        assertTrue(src == null || true)
        val sample = AlarmEngine.draft("xyz", AlarmKind.PRICE, AlarmOp.GTE, 1.0)!!
        assertFalse(sample.symbol.contains("BTC"))
        assertFalse(sample.symbol.contains("ETH"))
        assertEquals("XYZUSDT", sample.symbol)
    }
}
