package com.coinglass.intel.work

import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.db.DiscoverySnapEntity
import com.coinglass.intel.data.rest.ExchangeRest
import com.coinglass.intel.domain.DiscoveryPick
import com.coinglass.intel.domain.MarketScorer
import com.coinglass.intel.domain.model.Candle
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.math.abs

/** Ticker → K full scores. Never writes watchlist. */
class MarketDiscovery(
    restClient: OkHttpClient,
    private val db: AppDb,
) {
    private val rest = ExchangeRest(restClient)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discover(): List<DiscoverySnapEntity> {
        val tickers = rest.fetchTickers24h()
        if (tickers.isEmpty()) return db.discovery().all()
        val watch = db.watch().all().map { it.symbol }.toSet()
        val picks = DiscoveryPick.pick(tickers, exclude = watch)
        val intended = picks.map { it.symbol }.toSet()
        val out = mutableListOf<DiscoverySnapEntity>()
        for ((i, chunk) in picks.chunked(4).withIndex()) {
            if (i > 0) delay(250)
            for (t in chunk) {
                val feed = runCatching { rest.fetch(t.symbol) }.getOrNull() ?: continue
                val report = MarketScorer.score(feed)
                val row = DiscoverySnapEntity(
                    symbol = t.symbol,
                    price = report.price,
                    score = report.totalScore,
                    grade = report.grade,
                    spoof = report.spoof,
                    netRr = report.netRr,
                    vol24 = report.vol24,
                    coverage = report.coverage,
                    updatedAt = System.currentTimeMillis(),
                    direction = report.direction,
                    candles1hJson = encode(feed.klines5m.takeLast(48)),
                )
                db.discovery().upsert(row)
                out += row
            }
        }
        for (old in db.discovery().all()) {
            if (old.symbol !in intended) db.discovery().delete(old.symbol)
        }
        return out.sortedByDescending { abs(it.score) }
    }

    private fun encode(cs: List<Candle>): String = json.encodeToString(cs.map {
        listOf(it.openTime, it.open, it.high, it.low, it.close, it.volume)
    })
}
