package com.coinglass.intel.domain

import kotlin.math.abs

/** Persistent-friendly gate. Worker must not keep this in RAM only. */
object AlertDedup {
    const val WINDOW_MS = 10 * 60_000L
    const val MIN_DELTA = 8.0

    fun shouldSkip(prevTs: Long?, prevScore: Double?, now: Long, score: Double): Boolean {
        if (prevTs == null || prevScore == null) return false
        return now - prevTs < WINDOW_MS && abs(score - prevScore) < MIN_DELTA
    }
}
