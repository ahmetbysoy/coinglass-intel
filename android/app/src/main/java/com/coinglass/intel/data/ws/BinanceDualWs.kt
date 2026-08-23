package com.coinglass.intel.data.ws

import com.coinglass.intel.domain.JsonX
import com.coinglass.intel.domain.Symbols
import com.coinglass.intel.domain.asArr
import com.coinglass.intel.domain.asDouble
import com.coinglass.intel.domain.asObj
import com.coinglass.intel.domain.asString
import com.coinglass.intel.domain.model.LaneStats
import com.coinglass.intel.domain.model.StreamEvent
import com.coinglass.intel.domain.num
import com.coinglass.intel.domain.opt
import com.coinglass.intel.domain.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Binance USD-M 2026 split:
 *   /public  → @trade + @depth20@100ms
 *   /market  → @kline_1m @kline_5m @markPrice@1s @forceOrder
 *
 * Official excerpt omits @trade; live test (2026-08-23) shows @trade only on /public.
 */
class BinanceDualWs(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    companion object {
        const val PUBLIC_BASE = "wss://fstream.binance.com/public"
        const val MARKET_BASE = "wss://fstream.binance.com/market"
    }

    private val _events = MutableSharedFlow<StreamEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<StreamEvent> = _events.asSharedFlow()

    private val _public = MutableStateFlow(LaneStats("public"))
    private val _market = MutableStateFlow(LaneStats("market"))
    val publicStats: StateFlow<LaneStats> = _public.asStateFlow()
    val marketStats: StateFlow<LaneStats> = _market.asStateFlow()

    private var publicLane: Lane? = null
    private var marketLane: Lane? = null

    fun start(symbol: String) {
        stop()
        val s = Symbols.normalize(symbol).lowercase()
        publicLane = Lane(
            name = "public",
            url = combined(PUBLIC_BASE, listOf("$s@trade", "$s@depth20@100ms")),
            stats = _public,
        ).also { it.connect() }
        marketLane = Lane(
            name = "market",
            url = combined(
                MARKET_BASE,
                listOf("$s@kline_1m", "$s@kline_3m", "$s@kline_5m", "$s@kline_15m", "$s@markPrice@1s", "$s@forceOrder"),
            ),
            stats = _market,
        ).also { it.connect() }
    }

    fun stop() {
        publicLane?.close()
        marketLane?.close()
        publicLane = null
        marketLane = null
    }

    private fun combined(base: String, streams: List<String>): String {
        val joined = streams.joinToString("/")
        return "$base/stream?streams=$joined"
    }

    private inner class Lane(
        val name: String,
        val url: String,
        val stats: MutableStateFlow<LaneStats>,
    ) {
        private val running = AtomicBoolean(true)
        private val frames = AtomicLong(0)
        private var socket: WebSocket? = null
        private var loop: Job? = null
        private var attempt = 0

        fun connect() {
            loop = scope.launch {
                while (isActive && running.get()) {
                    try {
                        open()
                        // park until closed
                        while (isActive && running.get() && socket != null) delay(400)
                    } catch (t: Throwable) {
                        pushErr(t.message ?: t.toString())
                    }
                    if (!running.get()) break
                    attempt += 1
                    delay((2_000L * attempt).coerceAtMost(20_000L))
                }
            }
        }

        private fun open() {
            val req = Request.Builder().url(url).build()
            socket = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    attempt = 0
                    stats.value = stats.value.copy(connected = true, lastError = "")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handle(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    stats.value = stats.value.copy(connected = false)
                    socket = null
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    pushErr(t.message ?: "ws fail")
                    stats.value = stats.value.copy(connected = false)
                    socket = null
                }
            })
        }

        private fun handle(text: String) {
            val root = runCatching { JsonX.parseToJsonElement(text).asObj() }.getOrNull() ?: return
            val stream = root.str("stream")
            val data = root.opt("data")?.asObj() ?: root
            val ev = parse(stream, data) ?: return
            val n = frames.incrementAndGet()
            stats.value = stats.value.copy(connected = true, frames = n)
            _events.tryEmit(ev)
        }

        private fun pushErr(msg: String) {
            stats.value = stats.value.copy(connected = false, lastError = msg.take(120))
        }

        fun close() {
            running.set(false)
            loop?.cancel()
            socket?.close(1000, "stop")
            socket = null
            stats.value = LaneStats(name)
        }
    }
}

internal fun parse(stream: String, data: JsonObject): StreamEvent? {
    val kind = kindOf(stream.ifBlank { data.str("e") })
    var symbol = Symbols.normalize(data.str("s").ifBlank { stream.substringBefore("@") })
    val tsMs = data.num("E", "T")
    val ts = if (tsMs > 1e12) tsMs / 1000.0 else System.currentTimeMillis() / 1000.0
    var price = 0.0
    var size = 0.0
    var side = ""
    val extra = mutableMapOf<String, Any?>("stream" to stream, "lane" to routeLane(kind))

    when (kind) {
        "trade", "aggTrade" -> {
            price = data.num("p")
            size = data.num("q")
            val maker = data.opt("m")?.asString() == "true" ||
                data["m"]?.toString()?.contains("true") == true
            side = if (maker) "sell" else "buy"
            extra["trade_id"] = data.num("t", "a")
        }
        "depth" -> {
            val bids = levels(data, "b", "bids")
            val asks = levels(data, "a", "asks")
            extra["bids"] = bids
            extra["asks"] = asks
            if (bids.isNotEmpty() && asks.isNotEmpty()) {
                price = (bids.first().first + asks.first().first) / 2.0
            }
        }
        "kline" -> {
            val k = data.opt("k").asObj()
            if (k != null) {
                price = k.num("c")
                extra["o"] = k.num("o")
                extra["h"] = k.num("h")
                extra["l"] = k.num("l")
                extra["c"] = k.num("c")
                extra["v"] = k.num("v")
                extra["i"] = k.str("i")
                extra["t"] = k.num("t")
                extra["closed"] = k.str("x") == "true" || k["x"]?.toString()?.contains("true") == true
            }
        }
        "markPrice" -> {
            price = data.num("p")
            extra["funding"] = data.num("r")
            extra["index"] = data.num("i")
        }
        "forceOrder" -> {
            val o = data.opt("o").asObj() ?: data
            symbol = Symbols.normalize(o.str("s").ifBlank { symbol })
            price = o.num("ap", "p")
            size = o.num("q")
            side = o.str("S").lowercase()
            extra["status"] = o.str("X")
        }
        "ticker", "miniTicker", "bookTicker" -> {
            price = data.num("c", "b", "a")
        }
        else -> return null
    }

    return StreamEvent(
        channel = stream,
        kind = kind,
        symbol = symbol,
        exchange = "Binance",
        timestamp = ts,
        price = price,
        sizeUsd = if (size > 0 && price > 0) size * price else size,
        side = side,
        extra = extra,
    )
}

private fun kindOf(stream: String): String {
    val s = stream.lowercase()
    return when {
        "@trade" in s && "@aggtrade" !in s -> "trade"
        "@aggtrade" in s -> "aggTrade"
        "@depth" in s -> "depth"
        "@bookticker" in s -> "bookTicker"
        "@kline" in s -> "kline"
        "@markprice" in s -> "markPrice"
        "@forceorder" in s -> "forceOrder"
        "@miniticker" in s -> "miniTicker"
        "@ticker" in s -> "ticker"
        else -> "other"
    }
}

private fun routeLane(kind: String): String =
    if (kind == "trade" || kind == "depth" || kind == "bookTicker") "public" else "market"

private fun levels(data: JsonObject, vararg keys: String): List<Pair<Double, Double>> {
    for (k in keys) {
        val arr = data[k].asArr() ?: continue
        return arr.mapNotNull { row ->
            val a = row.asArr() ?: return@mapNotNull null
            val p = a.getOrNull(0).asDouble()
            val q = a.getOrNull(1).asDouble()
            if (p > 0) p to q else null
        }.take(20)
    }
    return emptyList()
}
