package com.coinglass.intel.data.ws

import com.coinglass.intel.domain.JsonX
import com.coinglass.intel.domain.Symbols
import com.coinglass.intel.domain.asArr
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
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream

/** Site public WSS: wss://wss.coinglass.com/ws — gzip JSON, channel=liq. */
class CoinGlassLiqWs(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    companion object {
        const val URL = "wss://wss.coinglass.com/ws"
    }

    private val _events = MutableSharedFlow<StreamEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<StreamEvent> = _events.asSharedFlow()

    private val _stats = MutableStateFlow(LaneStats("coinglass"))
    val stats: StateFlow<LaneStats> = _stats.asStateFlow()

    private val running = AtomicBoolean(false)
    private val frames = AtomicLong(0)
    private var socket: WebSocket? = null
    private var loop: Job? = null
    private var symbol: String = "BTC"

    fun start(rawSymbol: String) {
        stop()
        symbol = Symbols.base(rawSymbol)
        running.set(true)
        frames.set(0)
        loop = scope.launch { runLoop() }
    }

    fun stop() {
        running.set(false)
        loop?.cancel()
        socket?.close(1000, "stop")
        socket = null
        _stats.value = LaneStats("coinglass")
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive && running.get()) {
            try {
                open()
                while (scope.isActive && running.get() && socket != null) delay(400)
            } catch (t: Throwable) {
                _stats.value = _stats.value.copy(connected = false, lastError = (t.message ?: "").take(120))
            }
            if (!running.get()) break
            attempt += 1
            delay((2_000L * attempt).coerceAtMost(20_000L))
        }
    }

    private fun open() {
        val req = Request.Builder()
            .url(URL)
            .header("Origin", "https://www.coinglass.com")
            .header("User-Agent", "Mozilla/5.0 CoinGlassIntel/1.0")
            .build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val sub =
                    """{"method":"subscribe","params":[{"channel":"liq","type":"-1"},{"channel":"liq","type":"$symbol"}]}"""
                webSocket.send(sub)
                _stats.value = _stats.value.copy(connected = true, lastError = "")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "pong" || text == "ping") {
                    if (text == "ping") webSocket.send("pong")
                    return
                }
                handleJson(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val decoded = decode(bytes.toByteArray()) ?: return
                if (decoded == "pong" || decoded == "ping") return
                handleJson(decoded)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _stats.value = _stats.value.copy(connected = false)
                socket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _stats.value = _stats.value.copy(connected = false, lastError = (t.message ?: "fail").take(120))
                socket = null
            }
        })
        scope.launch {
            while (running.get() && scope.isActive) {
                delay(20_000)
                socket?.send("ping")
            }
        }
    }

    private fun decode(raw: ByteArray): String? {
        return try {
            val bytes = if (raw.size >= 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
                GZIPInputStream(ByteArrayInputStream(raw)).readBytes()
            } else raw
            bytes.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun handleJson(text: String) {
        val root = runCatching { JsonX.parseToJsonElement(text).asObj() }.getOrNull() ?: return
        val channel = root.str("channel", "ch", "topic")
        val dataEl = root["data"] ?: root["params"] ?: root["d"]
        val rows = dataEl.asArr() ?: dataEl.asObj()?.let { listOf(it) } ?: return
        val kind = when {
            channel == "liq" || channel.startsWith("liquidation") -> "liquidation"
            channel.startsWith("futures_trades") -> "trade"
            channel.startsWith("futures_ticker") -> "ticker"
            else -> "other"
        }
        val n = frames.incrementAndGet()
        _stats.value = _stats.value.copy(connected = true, frames = n)
        for (rowEl in rows) {
            val row = (rowEl as? JsonObject) ?: continue
            val ev = StreamEvent(
                channel = channel.ifBlank { "liq" },
                kind = kind,
                symbol = Symbols.normalize(row.str("symbol", "base_asset", "baseAsset", "coin")),
                exchange = row.str("exchange", "exchangeName", "exName", "ex_name", "ex"),
                timestamp = run {
                    val ts = row.num("time", "createTime", "turnoverTime", "update_time", "ts")
                    if (ts > 1e12) ts / 1000.0 else if (ts > 0) ts else System.currentTimeMillis() / 1000.0
                },
                price = row.num("price", "p"),
                sizeUsd = row.num("volume_usd", "volUsd", "vol_usd", "usd", "value"),
                side = sideName(kind, row.str("side")),
                extra = mapOf(
                    "oi" to row.num("open_interest", "openInterest", "oi"),
                    "funding" to row.num("funding_rate", "fundingRate"),
                ),
            )
            _events.tryEmit(ev)
        }
    }

    private fun sideName(kind: String, raw: String): String {
        val n = raw.toIntOrNull()
        return when {
            n == null -> raw
            kind == "liquidation" -> when (n) {
                1 -> "long"
                2 -> "short"
                else -> raw
            }
            else -> when (n) {
                1 -> "sell"
                2 -> "buy"
                else -> raw
            }
        }
    }
}
