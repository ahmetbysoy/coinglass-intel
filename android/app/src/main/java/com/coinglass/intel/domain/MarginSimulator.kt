package com.coinglass.intel.domain

/** Isolated-approx liq. Emir yok. */
object MarginSimulator {
    const val MMR = 0.004

    /**
     * long: entry * (1 - 1/lev + mmr)
     * short: entry * (1 + 1/lev - mmr)
     */
    fun liqPrice(entry: Double, lev: Double, side: String, mmr: Double = MMR): Double {
        if (entry <= 0.0 || !entry.isFinite()) return 0.0
        if (lev < 1.0 || !lev.isFinite()) return 0.0
        if (!mmr.isFinite() || mmr < 0.0) return 0.0
        val s = side.lowercase()
        val longSide = s == "long" || s == "buy" || "bull" in s
        val shortSide = s == "short" || s == "sell" || "bear" in s
        if (!longSide && !shortSide) return 0.0
        val inv = 1.0 / lev
        return if (longSide) entry * (1.0 - inv + mmr) else entry * (1.0 + inv - mmr)
    }
}
