package com.coinglass.intel.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDedupTest {
    @Test
    fun firstAlertAlwaysSends() {
        assertFalse(AlertDedup.shouldSkip(null, null, 1_000L, 40.0))
    }

    @Test
    fun sameScoreInsideWindowSkipped() {
        assertTrue(AlertDedup.shouldSkip(1_000L, 40.0, 1_000L + 60_000L, 42.0))
    }

    @Test
    fun bigJumpInsideWindowSends() {
        assertFalse(AlertDedup.shouldSkip(1_000L, 40.0, 1_000L + 60_000L, 55.0))
    }

    @Test
    fun afterWindowSendsAgain() {
        assertFalse(AlertDedup.shouldSkip(1_000L, 40.0, 1_000L + AlertDedup.WINDOW_MS + 1, 40.0))
    }

    @Test
    fun riskModeSwitchesAtTwelve() {
        assertEquals("static", Curves.riskMode(List(11) { 1.0 }))
        assertEquals("percentile", Curves.riskMode(List(12) { 1.0 }))
    }

    private fun assertEquals(a: String, b: String) {
        org.junit.Assert.assertEquals(a, b)
    }
}
