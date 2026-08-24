package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVmLogicTest {
    @Test
    fun staleNeedsSymbolAndAge() {
        assertFalse(StaleClock.isStale("", 1L, 20_000L, 15))
        assertFalse(StaleClock.isStale("FOOUSDT", 0L, 20_000L, 15))
        assertFalse(StaleClock.isStale("FOOUSDT", 10_000L, 20_000L, 15))
        assertTrue(StaleClock.isStale("FOOUSDT", 1_000L, 20_000L, 15))
    }

    @Test
    fun compareRingDropsOldestAtLimit() {
        assertEquals(listOf("A"), CompareRing.toggle(emptyList(), "A"))
        assertEquals(emptyList<String>(), CompareRing.toggle(listOf("A"), "A"))
        assertEquals(listOf("A", "B"), CompareRing.toggle(listOf("A"), "B"))
        assertEquals(listOf("B", "C"), CompareRing.toggle(listOf("A", "B"), "C"))
        assertEquals(emptyList<String>(), CompareRing.toggle(emptyList(), "  "))
    }

    @Test
    fun watchCycleWrapsNegative() {
        val list = listOf("A", "B", "C")
        assertEquals("C", WatchCycle.pick(list, "A", -1))
        assertEquals("B", WatchCycle.pick(list, "A", 1))
        assertEquals("A", WatchCycle.pick(list, "Z", 0))
        assertNull(WatchCycle.pick(emptyList(), "A", 1))
    }

    @Test
    fun alarmSigChangesWithQuote() {
        val a = AlarmSig.of("FOOUSDT", 10.0, 1.0, 0.001)
        val b = AlarmSig.of("FOOUSDT", 10.1, 1.0, 0.001)
        assertFalse(a == b)
        assertEquals(a, AlarmSig.of("FOOUSDT", 10.0, 1.0, 0.001))
    }
}
