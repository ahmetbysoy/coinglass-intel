package com.coinglass.intel.work

import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.db.ScoreSnapEntity
import com.coinglass.intel.data.rest.ExchangeRest
import com.coinglass.intel.domain.MarketScorer
import com.coinglass.intel.domain.model.Candle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class WatchlistScanner(
    restClient: OkHttpClient,
    private val db: AppDb,
) {
    private val rest = ExchangeRest(restClient)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun scanAll(): List<ScoreSnapEntity> {
        val out = mutableListOf<ScoreSnapEntity>()
        for (w in db.watch().all()) {
            val feed = runCatching { rest.fetch(w.symbol) }.getOrNull() ?: continue
            val report = MarketScorer.score(feed)
            val row = ScoreSnapEntity(
                symbol = w.symbol,
                price = report.price,
                score = report.totalScore,
                direction = report.direction,
                sl = report.sl,
                tp = report.tp,
                coverage = report.coverage,
                updatedAt = System.currentTimeMillis(),
                candles1hJson = encode(feed.klines1h.takeLast(48)),
                candles4hJson = encode(feed.klines4h.takeLast(48)),
            )
            db.snap().upsert(row)
            out += row
        }
        return out.sortedByDescending { kotlin.math.abs(it.score) }
    }

    private fun encode(cs: List<Candle>): String = json.encodeToString(cs.map {
        listOf(it.openTime, it.open, it.high, it.low, it.close, it.volume)
    })
}
