package com.coinglass.intel.domain.model

data class StreamEvent(
    val channel: String,
    val kind: String,
    val symbol: String,
    val exchange: String,
    val timestamp: Double,
    val price: Double = 0.0,
    val sizeUsd: Double = 0.0,
    val side: String = "",
    val extra: Map<String, Any?> = emptyMap(),
)

data class Candle(
    val openTime: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

data class TradePrint(
    val price: Double,
    val qty: Double,
    val buyerMaker: Boolean,
)

data class TakerBar(
    val timestamp: Long,
    val buyVol: Double,
    val sellVol: Double,
)

data class BookSnap(
    val ts: Long,
    val mid: Double,
    val bidWalls: List<Pair<Double, Double>>,
    val askWalls: List<Pair<Double, Double>>,
)

data class HitRate(
    val n: Int = 0,
    val wins: Int = 0,
    val rate: Double = 0.0,
    val horizon: String = "15m",
) {
    val line: String
        get() = if (n == 0) "isabet: veri yok (henuz settle yok)"
        else "bu tahmin gecmiste %${(rate * 100).toInt()} isabetli (n=$n, $horizon)"
}

data class SourceFresh(
    val priceMs: Long = 0L,
    val oiMs: Long = 0L,
    val fundMs: Long = 0L,
    val obMs: Long = 0L,
    val restMs: Long = 0L,
    val bybitMs: Long = 0L,
    val okxMs: Long = 0L,
)

data class OrderBook(
    val bids: List<Pair<Double, Double>>,
    val asks: List<Pair<Double, Double>>,
    val mid: Double,
    val bidVol: Double,
    val askVol: Double,
    val imbalance: Double,
    val spreadPct: Double,
)

data class NamedPrice(val exchange: String, val price: Double)

data class ScoreInput(
    val symbol: String,
    val prices: List<NamedPrice>,
    val chg24: Double,
    val vol24: Double,
    val oi: Double,
    val oiHist: List<Double>,
    val orderBooks: Map<String, OrderBook>,
    val trades: List<TradePrint>,
    val fundingRates: List<Double>,
    val lsRatio: Double?,
    val takerHist: List<TakerBar>,
    val klines5m: List<Candle>,
    val klines15m: List<Candle>,
    val klines1h: List<Candle>,
    val liveLiqLong: Double = 0.0,
    val liveLiqShort: Double = 0.0,
    val liqSeen: Boolean = false,
    val restErrors: List<String> = emptyList(),
    val bookHistory: List<BookSnap> = emptyList(),
    val weightBoost: Map<String, Double> = emptyMap(),
    val klines1m: List<Candle> = emptyList(),
    val klines3m: List<Candle> = emptyList(),
    val btcChg24: Double = 0.0,
    val nextFundingMs: Long = 0L,
    val minutesToFunding: Double = 999.0,
)

data class SimpleSignal(
    val directionalScore: Double,
    val signalStrength: Double,
    val currentPrice: Double = 0.0,
    val narrative: String = "",
)

data class TfPred(
    val timeframe: String,
    val direction: String,
    val confidence: Double,
    val weightedScore: Double,
    val expectedMovePct: Double,
    val risk: String,
)

data class V4Report(
    val symbol: String,
    val price: Double,
    val chg24: Double,
    val vol24: Double,
    val direction: String,
    val totalScore: Double,
    val confluence: Double,
    val risk: Int,
    val spoof: Int,
    val strategy: String,
    val strategyWarnings: List<String>,
    val forecasts: Map<String, Double>,
    val component: Map<String, Double>,
    val signals: Map<String, SimpleSignal>,
    val text: String,
    val warnings: List<String> = emptyList(),
    val coverage: Double = 0.0,
    val divergence: Map<String, Any?> = emptyMap(),
    val tfPreds: List<TfPred> = emptyList(),
    val sl: Double = 0.0,
    val tp: Double = 0.0,
    val oi: Double = 0.0,
    val funding: Double = 0.0,
    val ls: Double = 1.0,
    val cvdPct: Double = 0.0,
    val atrPct: Double = 0.0,
    val liqLong: Double = 0.0,
    val liqShort: Double = 0.0,
    val rsi5m: Double = 50.0,
    val rsiTf: Map<String, Double> = emptyMap(),
    val liqSeen: Boolean = false,
    val restErrors: List<String> = emptyList(),
    val support: Double = 0.0,
    val resistance: Double = 0.0,
    val bidWall: Double = 0.0,
    val askWall: Double = 0.0,
    val slReason: String = "",
    val hit: HitRate = HitRate(),
    val why: String = "",
    val nextFundingMs: Long = 0L,
    val netRr: Double = 0.0,
    val grade: String = "C",
    val verdict: String = "",
    val enterOk: Boolean = false,
    val poc: Double = 0.0,
    val divergeType: String = "",
)

data class LaneStats(
    val name: String,
    val connected: Boolean = false,
    val frames: Long = 0,
    val lastError: String = "",
)

data class ConnStats(
    val public: LaneStats = LaneStats("public"),
    val market: LaneStats = LaneStats("market"),
    val coinglass: LaneStats = LaneStats("coinglass"),
    val bybit: LaneStats = LaneStats("bybit"),
    val okx: LaneStats = LaneStats("okx"),
)

data class IntelUiState(
    val symbol: String = "",
    val query: String = "",
    val report: V4Report? = null,
    val conn: ConnStats = ConnStats(),
    val lastPrice: Double = 0.0,
    val lastUpdateMs: Long = 0L,
    val loading: Boolean = false,
    val statusLine: String = "sembol gir",
    val chips: List<String> = emptyList(),
    val candles1m: List<Candle> = emptyList(),
    val candles3m: List<Candle> = emptyList(),
    val candles5m: List<Candle> = emptyList(),
    val candles15m: List<Candle> = emptyList(),
    val chartTf: String = "5m",
    val stale: Boolean = false,
    val inWatchlist: Boolean = false,
    val fresh: SourceFresh = SourceFresh(),
    val hit: HitRate = HitRate(),
    val restErrors: List<String> = emptyList(),
    val liqSeen: Boolean = false,
    val bids: List<Pair<Double, Double>> = emptyList(),
    val asks: List<Pair<Double, Double>> = emptyList(),
)
