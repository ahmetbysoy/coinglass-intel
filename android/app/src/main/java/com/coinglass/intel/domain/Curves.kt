package com.coinglass.intel.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

/** Continuous scoring curves — keep in lockstep with python/engine/curves.py */
object Curves {
    const val RISK_RAW_MAX = 65.0

    fun rsiSignal(rsi: Double): Double = 30.0 * tanh((50.0 - rsi) / 12.0)

    fun oiScore(oiChgPct: Double, chg24: Double): Double {
        val upOi = max(oiChgPct, 0.0)
        val dnOi = max(-oiChgPct, 0.0)
        val upPx = max(chg24, 0.0)
        val dnPx = max(-chg24, 0.0)
        return 60.0 * tanh(upOi / 3.0) * tanh(upPx / 2.0) +
            (-40.0) * tanh(upOi / 3.0) * tanh(dnPx / 2.0) +
            30.0 * tanh(dnOi / 3.0) * tanh(upPx / 2.0) +
            (-60.0) * tanh(dnOi / 3.0) * tanh(dnPx / 2.0)
    }

    fun lsScore(lsAvg: Double): Double = -40.0 * tanh((lsAvg - 1.0) / 0.55)

    fun riskRaw(atrPct: Double, funding: Double, lsAvg: Double, vol24: Double): Double {
        var raw = 0.0
        if (atrPct > 4) raw += 20.0 else if (atrPct > 2) raw += 10.0
        if (abs(funding) > 0.01) raw += 10.0
        if (lsAvg > 2 || lsAvg < 0.5) raw += 15.0
        if (vol24 < 1_000_000) raw += 20.0 else if (vol24 < 10_000_000) raw += 10.0
        return raw
    }

    fun riskScore(atrPct: Double, funding: Double, lsAvg: Double, vol24: Double): Int {
        val raw = min(riskRaw(atrPct, funding, lsAvg, vol24), RISK_RAW_MAX)
        return kotlin.math.round(raw / RISK_RAW_MAX * 100.0).toInt()
    }

    data class Levels(val sl: Double, val tp: Double, val slPct: Double, val tpPct: Double)

    fun slTp(price: Double, direction: String, atrPct: Double, totalScore: Double): Levels {
        if (price <= 0) return Levels(0.0, 0.0, 0.0, 0.0)
        val conf = min(abs(totalScore) / 100.0, 1.0)
        val atr = max(atrPct, 0.0)
        val slPct = if (atr > 0) max(0.35, atr * (0.6 + 0.9 * conf))
        else max(0.35, 1.0 * (0.6 + 0.4 * conf))
        val rr = 1.0 + 1.5 * conf
        val tpPct = slPct * rr
        return when {
            "BULL" in direction -> Levels(price * (1 - slPct / 100), price * (1 + tpPct / 100), slPct, tpPct)
            "BEAR" in direction -> Levels(price * (1 + slPct / 100), price * (1 - tpPct / 100), slPct, tpPct)
            else -> Levels(price * (1 - slPct / 100), price * (1 + slPct / 100), slPct, slPct)
        }
    }
}
