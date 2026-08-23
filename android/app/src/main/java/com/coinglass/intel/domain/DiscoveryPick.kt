package com.coinglass.intel.domain

import kotlinx.serialization.json.JsonElement
import kotlin.math.abs

/** One Binance USD-M 24h ticker row. Not a coin list — parsed from the exchange. */
data class Ticker24h(
    val symbol: String,
    val lastPrice: Double,
    val quoteVolume: Double,
    val priceChangePercent: Double,
)

/**
 * Radar discovery picker. Universe = live ticker, never a hardcoded symbol table.
 * N = top quoteVolume USDT-M; K = prefiltered full-score budget.
 */
object DiscoveryPick {
    const val TOP_N = 40
    const val FULL_K = 12
    const val MIN_QUOTE_VOL = 20_000_000.0
    const val MIN_ABS_CHG = 1.2

    fun parse(raw: String): List<Ticker24h> {
        if (raw.isBlank()) return emptyList()
        val el = runCatching { JsonX.parseToJsonElement(raw) }.getOrNull()
        return fromJson(el)
    }

    fun fromJson(el: JsonElement?): List<Ticker24h> {
        val arr = el.asArr() ?: return emptyList()
        return arr.mapNotNull { row ->
            val o = row.asObj() ?: return@mapNotNull null
            val sym = o.str("symbol")
            if (sym.isBlank()) return@mapNotNull null
            Ticker24h(
                symbol = sym,
                lastPrice = o.num("lastPrice"),
                quoteVolume = o.num("quoteVolume"),
                priceChangePercent = o.num("priceChangePercent"),
            )
        }
    }

    /** USDT-M only, volume desc, cap N. */
    fun universe(rows: List<Ticker24h>, n: Int = TOP_N): List<Ticker24h> =
        rows.filter { it.symbol.endsWith("USDT") }
            .sortedByDescending { it.quoteVolume }
            .take(n)

    /**
     * After universe: drop [exclude] (watchlist), keep vol≥20M and |chg|≥1.2, cap K.
     * Exclusion is after N so we never promote #41 into the window.
     */
    fun prefilter(
        universe: List<Ticker24h>,
        exclude: Set<String> = emptySet(),
        k: Int = FULL_K,
    ): List<Ticker24h> =
        universe.filter { it.symbol !in exclude }
            .filter { it.quoteVolume >= MIN_QUOTE_VOL && abs(it.priceChangePercent) >= MIN_ABS_CHG }
            .take(k)

    fun pick(rows: List<Ticker24h>, exclude: Set<String> = emptySet()): List<Ticker24h> =
        prefilter(universe(rows), exclude)

    /** Opportunity notify gate. Default off in settings — this is the predicate only. */
    fun isOpportunity(grade: String, spoof: Int, netRr: Double, coverage: Double): Boolean =
        (grade == "A" || grade == "B") &&
            spoof < 40 &&
            netRr >= 1.5 &&
            coverage >= 50.0
}
