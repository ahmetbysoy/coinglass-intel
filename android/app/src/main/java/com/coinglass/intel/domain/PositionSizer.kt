package com.coinglass.intel.domain

import kotlin.math.abs

/** Risk% × bakiye / SL mesafesi. Emir göndermez. */
object PositionSizer {
    data class Plan(
        val riskUsd: Double,
        val sizeUsd: Double,
        val qty: Double,
        val slPct: Double,
    )

    fun plan(balance: Double, riskPct: Double, price: Double, sl: Double): Plan {
        if (balance <= 0 || price <= 0 || sl <= 0) return Plan(0.0, 0.0, 0.0, 0.0)
        val slPct = abs(price - sl) / price * 100.0
        if (slPct <= 0.0) return Plan(0.0, 0.0, 0.0, 0.0)
        val riskUsd = balance * (riskPct.coerceIn(0.05, 10.0) / 100.0)
        val sizeUsd = riskUsd / (slPct / 100.0)
        return Plan(riskUsd, sizeUsd, sizeUsd / price, slPct)
    }
}
