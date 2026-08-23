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

    data class Levels(
        val sl: Double,
        val tp: Double,
        val slPct: Double,
        val tpPct: Double,
        val reason: String = "atr",
    )

    fun slTp(
        price: Double,
        direction: String,
        atrPct: Double,
        totalScore: Double,
        structure: StructureLevels = StructureLevels(),
    ): Levels {
        if (price <= 0) return Levels(0.0, 0.0, 0.0, 0.0)
        val conf = min(abs(totalScore) / 100.0, 1.0)
        val atr = max(atrPct, 0.0)
        val slPctAtr = if (atr > 0) max(0.35, atr * (0.6 + 0.9 * conf))
        else max(0.35, 1.0 * (0.6 + 0.4 * conf))
        val rr = 1.0 + 1.5 * conf
        val tpPctAtr = slPctAtr * rr
        val floor = price * (slPctAtr / 100.0) * 0.45
        val ceil = price * (slPctAtr / 100.0) * 2.4
        fun usable(level: Double, below: Boolean): Double {
            if (level <= 0) return 0.0
            val dist = if (below) price - level else level - price
            return if (dist in floor..ceil) level else 0.0
        }
        return when {
            "BULL" in direction -> {
                val atrSl = price * (1 - slPctAtr / 100)
                val atrTp = price * (1 + tpPctAtr / 100)
                val structSl = listOf(
                    usable(structure.support * 0.998, true),
                    usable(structure.bidWall * 0.998, true),
                ).filter { it > 0 }
                val sl = if (structSl.isEmpty()) atrSl else max(atrSl, structSl.max())
                val structTp = listOf(
                    usable(structure.resistance * 1.002, false),
                    usable(structure.askWall * 1.002, false),
                ).filter { it > 0 }
                val tp = if (structTp.isEmpty()) atrTp else min(atrTp, structTp.min())
                val why = buildList {
                    add("atr")
                    if (structure.support > 0) add("swing-sup")
                    if (structure.bidWall > 0) add("ob-bid")
                }.joinToString("+")
                Levels(sl, max(tp, sl * 1.001), (price - sl) / price * 100, (tp - price) / price * 100, why)
            }
            "BEAR" in direction -> {
                val atrSl = price * (1 + slPctAtr / 100)
                val atrTp = price * (1 - tpPctAtr / 100)
                val structSl = listOf(
                    usable(structure.resistance * 1.002, false),
                    usable(structure.askWall * 1.002, false),
                ).filter { it > 0 }
                val sl = if (structSl.isEmpty()) atrSl else min(atrSl, structSl.min())
                val structTp = listOf(
                    usable(structure.support * 0.998, true),
                    usable(structure.bidWall * 0.998, true),
                ).filter { it > 0 }
                val tp = if (structTp.isEmpty()) atrTp else max(atrTp, structTp.max())
                val why = buildList {
                    add("atr")
                    if (structure.resistance > 0) add("swing-res")
                    if (structure.askWall > 0) add("ob-ask")
                }.joinToString("+")
                Levels(sl, min(tp, sl * 0.999), (sl - price) / price * 100, (price - tp) / price * 100, why)
            }
            else -> Levels(price * (1 - slPctAtr / 100), price * (1 + slPctAtr / 100), slPctAtr, slPctAtr, "range")
        }
    }
}
