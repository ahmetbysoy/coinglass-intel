package com.coinglass.intel.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class ObLevel(
    val price: Double,
    val qty: Double,
    val cumQty: Double,
    val isWall: Boolean,
)

data class ObBookUi(
    val bids: List<ObLevel>,
    val asks: List<ObLevel>,
    val maxQty: Double,
    val maxCum: Double,
    val bidTotal: Double,
    val askTotal: Double,
    val spreadPct: Double?,
) {
    fun maxQ(cumulative: Boolean): Double = if (cumulative) maxCum else maxQty
}

object ObBook {
    const val SPOOF_THRESHOLD = 50
    const val WALL_PRICE_TOL = 0.0008
    const val ROWS_NORMAL = 8
    const val ROWS_DEEP = 16
    const val MIN_BAR = 0.04f

    fun spoofActive(spoof: Int): Boolean = spoof >= SPOOF_THRESHOLD

    fun rows(deep: Boolean): Int = if (deep) ROWS_DEEP else ROWS_NORMAL

    fun isWall(price: Double, wall: Double, spoofOn: Boolean): Boolean {
        if (!spoofOn || wall <= 0.0 || price <= 0.0) return false
        return abs(price - wall) / max(price, 1e-9) < WALL_PRICE_TOL
    }

    fun build(
        bids: List<Pair<Double, Double>>,
        asks: List<Pair<Double, Double>>,
        rows: Int,
        spoofOn: Boolean,
        bidWall: Double,
        askWall: Double,
    ): ObBookUi {
        val n = rows.coerceAtLeast(1)
        val b = side(bids, n, spoofOn, bidWall)
        val a = side(asks, n, spoofOn, askWall)
        val maxQty = max(
            b.maxOfOrNull { it.qty } ?: 0.0,
            a.maxOfOrNull { it.qty } ?: 0.0,
        ).let { if (it <= 0) 1.0 else it }
        val maxCum = max(
            b.maxOfOrNull { it.cumQty } ?: 0.0,
            a.maxOfOrNull { it.cumQty } ?: 0.0,
        ).let { if (it <= 0) 1.0 else it }
        val bestBid = bids.firstOrNull()?.first
        val bestAsk = asks.firstOrNull()?.first
        val spread = if (bestBid != null && bestAsk != null && bestBid > 0) {
            (bestAsk - bestBid) / bestBid * 100.0
        } else {
            null
        }
        return ObBookUi(
            bids = b,
            asks = a,
            maxQty = maxQty,
            maxCum = maxCum,
            bidTotal = b.sumOf { it.qty },
            askTotal = a.sumOf { it.qty },
            spreadPct = spread,
        )
    }

    fun fmtQty(q: Double): String = when {
        q >= 1_000_000 -> String.format(Locale.US, "%.2fM", q / 1_000_000)
        q >= 1_000 -> String.format(Locale.US, "%.1fK", q / 1_000)
        else -> String.format(Locale.US, "%.2f", q)
    }

    private fun side(
        src: List<Pair<Double, Double>>,
        rows: Int,
        spoofOn: Boolean,
        wall: Double,
    ): List<ObLevel> {
        var cum = 0.0
        return src.take(rows).map { (p, q) ->
            val qty = if (q.isFinite() && q > 0) q else 0.0
            cum += qty
            ObLevel(
                price = p,
                qty = qty,
                cumQty = cum,
                isWall = isWall(p, wall, spoofOn),
            )
        }
    }
}
