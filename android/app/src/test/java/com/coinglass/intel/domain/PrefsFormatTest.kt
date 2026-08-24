package com.coinglass.intel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

class PrefsFormatTest {
    @Test
    fun compactUsdIgnoresTurkishLocale() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("$1.2M", PrefsFormat.compactUsd(1_200_000.0))
            assertEquals("$350K", PrefsFormat.compactUsd(350_000.0))
            assertEquals("$250", PrefsFormat.compactUsd(250.0))
            assertEquals("12.50", PrefsFormat.fmt("%.2f", 12.5))
        } finally {
            Locale.setDefault(prev)
        }
    }

    @Test
    fun logSliderRoundTrip() {
        val min = 100.0
        val max = 100_000.0
        for (v in listOf(100.0, 1_000.0, 10_000.0, 100_000.0)) {
            val t = PrefsFormat.logToLinear(v, min, max)
            val back = PrefsFormat.linearToLog(t, min, max)
            assertTrue("v=$v back=$back", abs(back - v) / v < 1e-6)
        }
        assertEquals(0f, PrefsFormat.logToLinear(100.0, min, max), 1e-6f)
        assertEquals(1f, PrefsFormat.logToLinear(100_000.0, min, max), 1e-6f)
    }

    @Test
    fun turkishUpperIDoesNotBreakRoot() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("BTCUSDT", "btcusdt".uppercase(Locale.ROOT))
            assertEquals("I", "i".uppercase(Locale.ROOT))
        } finally {
            Locale.setDefault(prev)
        }
    }
}
