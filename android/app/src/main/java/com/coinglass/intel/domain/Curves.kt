package com.coinglass.intel.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

/** Continuous scoring curves — keep in lockstep with python/engine/curves.py */
object Curves {
    const val RISK_RAW_MAX = 65.0
    const val ATR_PCTL_MIN = 12
    /** Snap spoof is noisier than vanishing-wall history — half weight. */
    const val SPOOF_SNAP_WEIGHT = 0.5

    fun riskMode(atrHistory: List<Double>): String =
        if (atrHistory.size >= ATR_PCTL_MIN) "percentile" else "static"

    fun momFromRsiAndRet(rsi: Double, ret3: Double): Double {
        val retPart = max(min(ret3 * 10.0, 50.0), -50.0)
        return (rsiSignal(rsi) + retPart) / 2.0
    }

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

    const val FEE_ROUND_TRIP_PCT = 0.08 // ~4 bps taker * 2

    fun riskRaw(
        atrPct: Double,
        funding: Double,
        lsAvg: Double,
        vol24: Double,
        atrHistory: List<Double> = emptyList(),
    ): Double {
        var raw = 0.0
        val atrPctl = percentile(atrHistory, atrPct)
        if (atrHistory.size >= ATR_PCTL_MIN) {
            if (atrPctl >= 80) raw += 20.0 else if (atrPctl >= 60) raw += 10.0
        } else {
            if (atrPct > 4) raw += 20.0 else if (atrPct > 2) raw += 10.0
        }
        if (abs(funding) > 0.01) raw += 10.0
        if (lsAvg > 2 || lsAvg < 0.5) raw += 15.0
        if (vol24 < 1_000_000) raw += 20.0 else if (vol24 < 10_000_000) raw += 10.0
        return raw
    }

    fun riskScore(
        atrPct: Double,
        funding: Double,
        lsAvg: Double,
        vol24: Double,
        atrHistory: List<Double> = emptyList(),
    ): Int {
        val raw = min(riskRaw(atrPct, funding, lsAvg, vol24, atrHistory), RISK_RAW_MAX)
        return kotlin.math.round(raw / RISK_RAW_MAX * 100.0).toInt()
    }

    fun percentile(hist: List<Double>, value: Double): Double {
        if (hist.isEmpty()) return 50.0
        val n = hist.count { it <= value }
        return n * 100.0 / hist.size
    }

    data class Levels(
        val sl: Double,
        val tp: Double,
        val slPct: Double,
        val tpPct: Double,
        val reason: String = "atr",
        val netRr: Double = 0.0,
    )

    fun slTp(
        price: Double,
        direction: String,
        atrPct: Double,
        totalScore: Double,
        structure: StructureLevels = StructureLevels(),
        spoofScore: Int = 0,
        funding: Double = 0.0,
        minutesToFunding: Double = 999.0,
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
        val wallsOk = spoofScore < 50
        fun usable(level: Double, below: Boolean): Double {
            if (level <= 0) return 0.0
            val dist = if (below) price - level else level - price
            return if (dist in floor..ceil) level else 0.0
        }
        fun netRr(slPct: Double, tpPct: Double): Double {
            val fundCost = if (minutesToFunding < 30.0) abs(funding) * 100.0 else 0.0
            val den = slPct + FEE_ROUND_TRIP_PCT
            val num = tpPct - FEE_ROUND_TRIP_PCT - fundCost
            return if (den <= 0) 0.0 else num / den
        }
        return when {
            "BULL" in direction -> {
                val atrSl = price * (1 - slPctAtr / 100)
                val atrTp = price * (1 + tpPctAtr / 100)
                val structSl = buildList {
                    add(usable(structure.support * 0.998, true))
                    if (wallsOk) add(usable(structure.bidWall * 0.998, true))
                }.filter { it > 0 }
                val sl = if (structSl.isEmpty()) atrSl else max(atrSl, structSl.max())
                val structTp = buildList {
                    add(usable(structure.resistance * 1.002, false))
                    if (wallsOk) add(usable(structure.askWall * 1.002, false))
                }.filter { it > 0 }
                val tp = if (structTp.isEmpty()) atrTp else min(atrTp, structTp.min())
                val slPct = (price - sl) / price * 100
                val tpPct = (tp - price) / price * 100
                val why = buildList {
                    add("atr")
                    if (structure.support > 0) add("vpoc-sup")
                    if (wallsOk && structure.bidWall > 0) add("ob-bid")
                    if (!wallsOk) add("spoof-skip-wall")
                    add("fee-net")
                }.joinToString("+")
                Levels(sl, max(tp, sl * 1.001), slPct, tpPct, why, netRr(slPct, tpPct))
            }
            "BEAR" in direction -> {
                val atrSl = price * (1 + slPctAtr / 100)
                val atrTp = price * (1 - tpPctAtr / 100)
                val structSl = buildList {
                    add(usable(structure.resistance * 1.002, false))
                    if (wallsOk) add(usable(structure.askWall * 1.002, false))
                }.filter { it > 0 }
                val sl = if (structSl.isEmpty()) atrSl else min(atrSl, structSl.min())
                val structTp = buildList {
                    add(usable(structure.support * 0.998, true))
                    if (wallsOk) add(usable(structure.bidWall * 0.998, true))
                }.filter { it > 0 }
                val tp = if (structTp.isEmpty()) atrTp else max(atrTp, structTp.max())
                val slPct = (sl - price) / price * 100
                val tpPct = (price - tp) / price * 100
                val why = buildList {
                    add("atr")
                    if (structure.resistance > 0) add("vpoc-res")
                    if (wallsOk && structure.askWall > 0) add("ob-ask")
                    if (!wallsOk) add("spoof-skip-wall")
                    add("fee-net")
                }.joinToString("+")
                Levels(sl, min(tp, sl * 0.999), slPct, tpPct, why, netRr(slPct, tpPct))
            }
            else -> {
                val sl = price * (1 - slPctAtr / 100)
                val tp = price * (1 + slPctAtr / 100)
                Levels(sl, tp, slPctAtr, slPctAtr, "range", netRr(slPctAtr, slPctAtr))
            }
        }
    }
}
