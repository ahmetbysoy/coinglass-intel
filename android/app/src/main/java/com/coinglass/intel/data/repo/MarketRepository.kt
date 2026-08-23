package com.coinglass.intel.data.repo

import com.coinglass.intel.data.rest.ExchangeRest
import com.coinglass.intel.data.ws.BinanceDualWs
import com.coinglass.intel.data.ws.CoinGlassLiqWs
import com.coinglass.intel.domain.Analyzers
import com.coinglass.intel.domain.MarketScorer
import com.coinglass.intel.domain.Symbols
import com.coinglass.intel.domain.model.BookSnap
import com.coinglass.intel.domain.model.Candle
import com.coinglass.intel.domain.model.ConnStats
import com.coinglass.intel.domain.model.IntelUiState
import com.coinglass.intel.domain.model.NamedPrice
import com.coinglass.intel.domain.model.OrderBook
import com.coinglass.intel.domain.model.ScoreInput
import com.coinglass.intel.domain.model.SourceFresh
import com.coinglass.intel.domain.model.StreamEvent
import com.coinglass.intel.domain.model.TradePrint
import com.coinglass.intel.domain.toFloat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

class MarketRepository(
    wsClient: OkHttpClient,
    restClient: OkHttpClient,
    private val externalScope: CoroutineScope,
) {
    private val rest = ExchangeRest(restClient)
    private val binance = BinanceDualWs(wsClient, externalScope)
    private val cg = CoinGlassLiqWs(wsClient, externalScope)

    private val _state = MutableStateFlow(IntelUiState())
    val state: StateFlow<IntelUiState> = _state.asStateFlow()

    private var restSnap: ScoreInput? = null
    private var livePrice = 0.0
    private var markPrice = 0.0
    private var liveFunding = 0.0
    private var liveBook: OrderBook? = null
    private val liveTrades = ArrayDeque<TradePrint>()
    private val k1 = linkedMapOf<Double, Candle>()
    private val k5 = linkedMapOf<Double, Candle>()
    private var liqLong = 0.0
    private var liqShort = 0.0
    private var liqSeen = false
    private var watchJob: Job? = null
    private var symbol = ""
    private var chartTf = "1h"
    private var boost: Map<String, Double> = emptyMap()
    private val bookHist = ArrayDeque<BookSnap>()
    private var priceMs = 0L
    private var oiMs = 0L
    private var fundMs = 0L
    private var obMs = 0L

    fun setBoost(b: Map<String, Double>) {
        boost = b
    }

    init {
        externalScope.launch {
            binance.events.collect { onEvent(it) }
        }
        externalScope.launch {
            cg.events.collect { onEvent(it) }
        }
        externalScope.launch {
            while (isActive) {
                _state.update {
                    it.copy(
                        conn = ConnStats(
                            public = binance.publicStats.value,
                            market = binance.marketStats.value,
                            coinglass = cg.stats.value,
                        ),
                    )
                }
                delay(500)
            }
        }
    }

    fun toggleChartTf() {
        chartTf = if (chartTf == "1h") "4h" else "1h"
        _state.update { it.copy(chartTf = chartTf) }
    }

    fun watch(raw: String) {
        val next = Symbols.normalize(raw)
        if (next.isBlank()) return
        symbol = next
        restSnap = null
        livePrice = 0.0
        markPrice = 0.0
        liveFunding = 0.0
        liveBook = null
        liveTrades.clear()
        k1.clear()
        k5.clear()
        liqLong = 0.0
        liqShort = 0.0
        liqSeen = false
        bookHist.clear()
        priceMs = 0L; oiMs = 0L; fundMs = 0L; obMs = 0L
        _state.update {
            it.copy(
                symbol = next,
                query = next,
                loading = true,
                report = null,
                lastPrice = 0.0,
                statusLine = "$next baglaniyor…",
            )
        }
        binance.start(next)
        cg.start(next)
        watchJob?.cancel()
        watchJob = externalScope.launch(Dispatchers.Default) {
            refreshRest()
            var ticks = 0
            while (isActive) {
                delay(2_000)
                ticks += 1
                if (ticks % 10 == 0) refreshRest()
                publish()
            }
        }
    }

    private suspend fun refreshRest() {
        runCatching {
            restSnap = rest.fetch(symbol)
            publish()
        }.onFailure {
            _state.update { s -> s.copy(statusLine = "REST: ${(it.message ?: "hata").take(80)}") }
        }
    }

    private fun onEvent(ev: StreamEvent) {
        val want = symbol
        if (ev.symbol.isNotBlank() && ev.kind != "liquidation") {
            if (Symbols.normalize(ev.symbol) != want && ev.kind != "other") {
                // still accept depth/trade if stream matches current
            }
        }
        when (ev.kind) {
            "trade" -> {
                if (ev.price > 0) {
                    livePrice = ev.price
                    priceMs = System.currentTimeMillis()
                }
                val qty = if (ev.price > 0) ev.sizeUsd / ev.price else 0.0
                liveTrades.addLast(TradePrint(ev.price, qty, ev.side == "sell"))
                while (liveTrades.size > 200) liveTrades.removeFirst()
            }
            "depth" -> {
                @Suppress("UNCHECKED_CAST")
                val bids = ev.extra["bids"] as? List<Pair<Double, Double>> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val asks = ev.extra["asks"] as? List<Pair<Double, Double>> ?: emptyList()
                liveBook = Analyzers.orderBook(bids, asks)
                obMs = System.currentTimeMillis()
                liveBook?.let { ob ->
                    val medB = ob.bids.map { it.second }.sorted().let { if (it.isEmpty()) 1.0 else it[it.size / 2] }
                    val medA = ob.asks.map { it.second }.sorted().let { if (it.isEmpty()) 1.0 else it[it.size / 2] }
                    bookHist.addLast(
                        BookSnap(
                            ts = obMs,
                            mid = ob.mid,
                            bidWalls = ob.bids.filter { it.second >= medB * 8 }.take(6),
                            askWalls = ob.asks.filter { it.second >= medA * 8 }.take(6),
                        ),
                    )
                    while (bookHist.size > 24) bookHist.removeFirst()
                }
                if (ev.price > 0 && livePrice == 0.0) livePrice = ev.price
            }
            "markPrice" -> {
                if (ev.price > 0) markPrice = ev.price
                liveFunding = toFloat(ev.extra["funding"])
                fundMs = System.currentTimeMillis()
            }
            "kline" -> {
                val interval = ev.extra["i"]?.toString().orEmpty()
                val t = toFloat(ev.extra["t"])
                val c = Candle(
                    openTime = t,
                    open = toFloat(ev.extra["o"]),
                    high = toFloat(ev.extra["h"]),
                    low = toFloat(ev.extra["l"]),
                    close = toFloat(ev.extra["c"]),
                    volume = toFloat(ev.extra["v"]),
                )
                val dest = if (interval == "1m") k1 else if (interval == "5m") k5 else null
                if (dest != null && t > 0) {
                    dest[t] = c
                    while (dest.size > 240) dest.remove(dest.keys.first())
                }
                if (c.close > 0) livePrice = c.close
            }
            "forceOrder", "liquidation" -> {
                liqSeen = true
                val usd = ev.sizeUsd
                when (ev.side) {
                    "long", "sell" -> liqLong += usd
                    "short", "buy" -> liqShort += usd
                    else -> liqShort += usd
                }
            }
        }
        if (ev.price > 0) {
            _state.update { it.copy(lastPrice = ev.price, lastUpdateMs = System.currentTimeMillis()) }
        }
    }

    private fun publish() {
        val base = restSnap
        val prices = mutableListOf<NamedPrice>()
        if (livePrice > 0) prices += NamedPrice("BinanceWS", livePrice)
        else if (markPrice > 0) prices += NamedPrice("Mark", markPrice)
        if (base != null) prices += base.prices

        val books = (base?.orderBooks ?: emptyMap()).toMutableMap()
        liveBook?.let { books["binance"] = it }

        val trades = if (liveTrades.isNotEmpty()) liveTrades.toList() else base?.trades.orEmpty()
        val funding = when {
            liveFunding != 0.0 -> listOf(liveFunding) + (base?.fundingRates ?: emptyList())
            else -> base?.fundingRates.orEmpty()
        }
        val k5m = if (k5.isNotEmpty()) k5.values.toList() else base?.klines5m.orEmpty()

        val input = ScoreInput(
            symbol = symbol,
            prices = prices,
            chg24 = base?.chg24 ?: 0.0,
            vol24 = base?.vol24 ?: 0.0,
            oi = base?.oi ?: 0.0,
            oiHist = base?.oiHist.orEmpty(),
            orderBooks = books,
            trades = trades,
            fundingRates = funding,
            lsRatio = base?.lsRatio,
            takerHist = base?.takerHist.orEmpty(),
            klines5m = k5m,
            klines15m = base?.klines15m.orEmpty(),
            klines1h = base?.klines1h.orEmpty(),
            liveLiqLong = liqLong,
            liveLiqShort = liqShort,
        )
        val report = MarketScorer.score(input)
        val live = binance.publicStats.value.connected || binance.marketStats.value.connected
        _state.update {
            it.copy(
                report = report,
                lastPrice = report.price,
                loading = report.price == 0.0,
                statusLine = if (live) "canli" else "kopuk / REST",
                lastUpdateMs = System.currentTimeMillis(),
                conn = ConnStats(
                    public = binance.publicStats.value,
                    market = binance.marketStats.value,
                    coinglass = cg.stats.value,
                ),
            )
        }
    }

    fun stop() {
        watchJob?.cancel()
        binance.stop()
        cg.stop()
    }

    companion object {
        fun clients(): Pair<OkHttpClient, OkHttpClient> {
            val ws = OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            val rest = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            return ws to rest
        }
    }
}
