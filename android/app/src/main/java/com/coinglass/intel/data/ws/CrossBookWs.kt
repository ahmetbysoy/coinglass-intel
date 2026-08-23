package com.coinglass.intel.data.ws

import com.coinglass.intel.domain.JsonX
import com.coinglass.intel.domain.Symbols
import com.coinglass.intel.domain.asArr
import com.coinglass.intel.domain.asDouble
import com.coinglass.intel.domain.asObj
import com.coinglass.intel.domain.model.LaneStats
import com.coinglass.intel.domain.model.StreamEvent
import com.coinglass.intel.domain.num
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * B6: lightweight best-bid/ask only.
 * Bybit linear orderbook.1 + OKX bbo-tbt. No public proxy farm.
 */
class CrossBookWs(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<StreamEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<StreamEvent> = _events.asSharedFlow()

    private val _bybit = MutableStateFlow(LaneStats("bybit"))
    private val _okx = MutableStateFlow(LaneStats("okx"))
    val bybitStats: StateFlow<LaneStats> = _bybit.asStateFlow()
    val okxStats: StateFlow<LaneStats> = _okx.asStateFlow()

    private var bybit: Lane? = null
    private var okx: Lane? = null

    fun start(symbol: String) {
        stop()
        val pair = Symbols.normalize(symbol)
        val okxInst = Symbols.resolve(pair).okx
        bybit = Lane(
            name = "bybit",
            url = "wss://stream.bybit.com/v5/public/linear",
            hello = """{"op":"subscribe","args":["orderbook.1.$pair"]}""",
            stats = _bybit,
            parse = { text -> parseBybit(text, pair) },
        ).also { it.connect() }
        okx = Lane(
            name = "okx",
            url = "wss://ws.okx.com:8443/ws/v5/public",
            hello = """{"op":"subscribe","args":[{"channel":"bbo-tbt","instId":"$okxInst"}]}""",
            stats = _okx,
            parse = { text -> parseOkx(text, pair) },
        ).also { it.connect() }
    }

    fun stop() {
        bybit?.close()
        okx?.close()
        bybit = null
        okx = null
    }

    private inner class Lane(
        val name: String,
        val url: String,
        val hello: String,
        val stats: MutableStateFlow<LaneStats>,
        val parse: (String) -> StreamEvent?,
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
                        while (isActive && running.get() && socket != null) delay(400)
                    } catch (t: Throwable) {
                        stats.value = stats.value.copy(connected = false, lastError = (t.message ?: "").take(80))
                    }
                    if (!running.get()) break
                    attempt += 1
                    delay((2_000L * attempt).coerceAtMost(20_000L))
                }
            }
        }

        private fun open() {
            socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    attempt = 0
                    webSocket.send(hello)
                    stats.value = stats.value.copy(connected = true, lastError = "")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("pong") && !text.contains("data")) return
                    val ev = parse(text) ?: return
                    val n = frames.incrementAndGet()
                    stats.value = stats.value.copy(connected = true, frames = n)
                    _events.tryEmit(ev)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    stats.value = stats.value.copy(connected = false)
                    socket = null
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    stats.value = stats.value.copy(connected = false, lastError = (t.message ?: "fail").take(80))
                    socket = null
                }
            })
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

internal fun parseBybit(text: String, want: String): StreamEvent? {
    val root = runCatching { JsonX.parseToJsonElement(text).asObj() }.getOrNull() ?: return null
    val data = root["data"].asObj() ?: return null
    val bids = levels(data, "b")
    val asks = levels(data, "a")
    if (bids.isEmpty() && asks.isEmpty()) return null
    val mid = if (bids.isNotEmpty() && asks.isNotEmpty()) (bids.first().first + asks.first().first) / 2.0 else 0.0
    return StreamEvent(
        channel = root.str("topic"),
        kind = "depth",
        symbol = want,
        exchange = "Bybit",
        timestamp = System.currentTimeMillis() / 1000.0,
        price = mid,
        extra = mapOf("bids" to bids, "asks" to asks, "src" to "bybit"),
    )
}

internal fun parseOkx(text: String, want: String): StreamEvent? {
    val root = runCatching { JsonX.parseToJsonElement(text).asObj() }.getOrNull() ?: return null
    val row = root["data"].asArr()?.firstOrNull()?.asObj() ?: return null
    val bid = row.num("bidPx")
    val ask = row.num("askPx")
    val bq = row.num("bidSz")
    val aq = row.num("askSz")
    if (bid <= 0 || ask <= 0) return null
    return StreamEvent(
        channel = "bbo-tbt",
        kind = "depth",
        symbol = want,
        exchange = "OKX",
        timestamp = System.currentTimeMillis() / 1000.0,
        price = (bid + ask) / 2.0,
        extra = mapOf(
            "bids" to listOf(bid to bq),
            "asks" to listOf(ask to aq),
            "src" to "okx",
        ),
    )
}

private fun levels(data: kotlinx.serialization.json.JsonObject, key: String): List<Pair<Double, Double>> {
    val arr = data[key].asArr() ?: return emptyList()
    return arr.mapNotNull { row ->
        val a = row.asArr() ?: return@mapNotNull null
        val p = a.getOrNull(0).asDouble()
        val q = a.getOrNull(1).asDouble()
        if (p > 0) p to q else null
    }.take(8)
}
