package com.coinglass.intel.data.outcome

import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.db.OutcomeEntity
import com.coinglass.intel.domain.WeightCalibrator
import com.coinglass.intel.domain.model.HitRate
import com.coinglass.intel.domain.model.V4Report
import kotlin.math.abs

class OutcomeTracker(private val db: AppDb) {
    suspend fun record(report: V4Report, now: Long = System.currentTimeMillis()) {
        if (report.symbol.isBlank() || report.price <= 0) return
        val last = db.outcome().last(report.symbol)
        if (last != null && now - last.ts < 120_000) return
        val c = report.component
        db.outcome().insert(
            OutcomeEntity(
                symbol = report.symbol,
                ts = now,
                price = report.price,
                score = report.totalScore,
                direction = report.direction,
                ob = c["ob"] ?: 0.0,
                tf = c["tf"] ?: 0.0,
                oi = c["oi"] ?: 0.0,
                funding = c["funding"] ?: 0.0,
                liq = c["liq"] ?: 0.0,
                vol = c["vol"] ?: 0.0,
                mom = c["mom"] ?: 0.0,
            ),
        )
    }

    suspend fun settle(symbol: String, price: Double, now: Long = System.currentTimeMillis()) {
        if (price <= 0) return
        for (row in db.outcome().unsettled().filter { it.symbol == symbol }) {
            var next = row
            next = maybe(next, now, price, 5 * 60_000L, row.settled5) { r, px, win ->
                r.copy(px5 = px, win5 = win, settled5 = true)
            }
            next = maybe(next, now, price, 15 * 60_000L, next.settled15) { r, px, win ->
                r.copy(px15 = px, win15 = win, settled15 = true)
            }
            next = maybe(next, now, price, 60 * 60_000L, next.settled1h) { r, px, win ->
                r.copy(px1h = px, win1h = win, settled1h = true)
            }
            if (next != row) db.outcome().update(next)
        }
    }

    private fun maybe(
        row: OutcomeEntity,
        now: Long,
        price: Double,
        horizon: Long,
        already: Boolean,
        apply: (OutcomeEntity, Double, Boolean) -> OutcomeEntity,
    ): OutcomeEntity {
        if (already || now < row.ts + horizon) return row
        val fwd = (price - row.price) / row.price
        val side = when {
            "BULL" in row.direction -> 1
            "BEAR" in row.direction -> -1
            else -> 0
        }
        val win = if (side == 0) abs(fwd) < 0.0015 else fwd * side > 0
        return apply(row, price, win)
    }

    suspend fun hitRate(symbol: String, lastN: Int = 30): HitRate {
        val rows = db.outcome().recent(symbol, lastN).filter { it.settled15 && it.win15 != null }
        if (rows.isEmpty()) return HitRate(horizon = "15m")
        val wins = rows.count { it.win15 == true }
        return HitRate(n = rows.size, wins = wins, rate = wins.toDouble() / rows.size, horizon = "15m")
    }

    suspend fun alignedBoost(): Map<String, Double> {
        val rows = db.outcome().settled15(200)
        val papers = db.paper().settled(200)
        val n = rows.size + papers.size
        if (n < WeightCalibrator.MIN_N) return emptyMap()
        val keys = listOf("ob", "tf", "oi", "funding", "liq", "vol", "mom")
        val avg = mutableMapOf<String, Double>()
        for (k in keys) {
            val xs = mutableListOf<Double>()
            for (r in rows) {
                val v = when (k) {
                    "ob" -> r.ob
                    "tf" -> r.tf
                    "oi" -> r.oi
                    "funding" -> r.funding
                    "liq" -> r.liq
                    "vol" -> r.vol
                    else -> r.mom
                }
                pushAligned(xs, v, r.price, r.px15)
            }
            for (p in papers) {
                val v = when (k) {
                    "ob" -> p.ob
                    "tf" -> p.tf
                    "oi" -> p.oi
                    "funding" -> p.funding
                    "liq" -> p.liq
                    "vol" -> p.vol
                    else -> p.mom
                }
                pushAligned(xs, v, p.entry, p.exitPx)
            }
            val label = when (k) {
                "ob" -> "OB"
                "tf" -> "TF"
                "oi" -> "OI"
                "funding" -> "Funding"
                "liq" -> "Liq"
                "vol" -> "Vol"
                else -> "Mom"
            }
            if (xs.isNotEmpty()) avg[label] = xs.average()
        }
        return WeightCalibrator.boost(avg, n)
    }

    private fun pushAligned(xs: MutableList<Double>, v: Double, entry: Double, exit: Double?) {
        if (abs(v) < 1.0 || exit == null || entry == 0.0) return
        val fwd = (exit - entry) / entry * 100.0
        val side = if (v > 0) 1.0 else -1.0
        xs += fwd * side
    }
}
