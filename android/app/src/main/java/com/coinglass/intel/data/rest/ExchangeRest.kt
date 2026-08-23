package com.coinglass.intel.data.rest

import com.coinglass.intel.domain.JsonX
import com.coinglass.intel.domain.Symbols
import com.coinglass.intel.domain.asArr
import com.coinglass.intel.domain.asDouble
import com.coinglass.intel.domain.asObj
import com.coinglass.intel.domain.asString
import com.coinglass.intel.domain.model.Candle
import com.coinglass.intel.domain.model.NamedPrice
import com.coinglass.intel.domain.model.OrderBook
import com.coinglass.intel.domain.model.ScoreInput
import com.coinglass.intel.domain.model.TakerBar
import com.coinglass.intel.domain.model.TradePrint
import com.coinglass.intel.domain.Analyzers
import com.coinglass.intel.domain.num
import com.coinglass.intel.domain.path
import com.coinglass.intel.domain.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

class ExchangeRest(private val client: OkHttpClient) {
    companion object {
        private const val BN = "https://fapi.binance.com"
        private const val BY = "https://api.bybit.com"
        private const val OK = "https://www.okx.com"
        private const val UA = "cg-intel/4.3"
    }

    private val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
    fun drainErrors(): List<String> = synchronized(errors) {
        val out = errors.toList()
        errors.clear()
        out
    }

    suspend fun fetch(raw: String): ScoreInput = withContext(Dispatchers.IO) {
        val info = Symbols.resolve(raw)
        val pair = info.binance
        val coin = info.base
        coroutineScope {
            val bn = async { binance(pair) }
            val by = async { bybit(pair) }
            val ok = async { okx(info.okx) }
            val btc = async {
                if (pair == "BTCUSDT") 0.0
                else get("$BN/fapi/v1/ticker/24hr?symbol=BTCUSDT").asObj()?.num("priceChangePercent") ?: 0.0
            }
            merge(pair, bn.await(), by.await(), ok.await(), btc.await())
        }
    }

    private data class Bundle(
        val prices: List<NamedPrice> = emptyList(),
        val chg24: Double = 0.0,
        val vol24: Double = 0.0,
        val oi: Double = 0.0,
        val oiHist: List<Double> = emptyList(),
        val books: Map<String, OrderBook> = emptyMap(),
        val trades: List<TradePrint> = emptyList(),
        val funding: List<Double> = emptyList(),
        val ls: Double? = null,
        val taker: List<TakerBar> = emptyList(),
        val k1: List<Candle> = emptyList(),
        val k3: List<Candle> = emptyList(),
        val k5: List<Candle> = emptyList(),
        val k15: List<Candle> = emptyList(),
        val k1h: List<Candle> = emptyList(),
        val btcChg: Double = 0.0,
        val nextFundingMs: Long = 0L,
    )

    private fun merge(pair: String, bn: Bundle, by: Bundle, ok: Bundle, btcChg: Double): ScoreInput {
        val nextMs = bn.nextFundingMs
        val mins = if (nextMs > 0) (nextMs - System.currentTimeMillis()) / 60_000.0 else 999.0
        return ScoreInput(
            symbol = pair,
            prices = bn.prices + by.prices + ok.prices,
            chg24 = bn.chg24,
            vol24 = bn.vol24,
            oi = if (bn.oi > 0) bn.oi else if (by.oi > 0) by.oi else ok.oi,
            oiHist = bn.oiHist.ifEmpty { by.oiHist },
            orderBooks = bn.books + by.books + ok.books,
            trades = bn.trades.ifEmpty { by.trades.ifEmpty { ok.trades } },
            fundingRates = bn.funding.ifEmpty { by.funding.ifEmpty { ok.funding } },
            lsRatio = bn.ls ?: by.ls ?: ok.ls,
            takerHist = bn.taker,
            klines5m = bn.k5,
            klines15m = bn.k15,
            klines1h = bn.k1h,
            restErrors = drainErrors(),
            klines1m = bn.k1,
            klines3m = bn.k3,
            btcChg24 = if (pair == "BTCUSDT") bn.chg24 else btcChg,
            nextFundingMs = nextMs,
            minutesToFunding = mins,
        )
    }

    private fun binance(pair: String): Bundle {
        val ticker = get("$BN/fapi/v1/ticker/24hr?symbol=$pair")
        val depth = get("$BN/fapi/v1/depth?symbol=$pair&limit=100")
        val trades = get("$BN/fapi/v1/trades?symbol=$pair&limit=100")
        val funding = get("$BN/fapi/v1/fundingRate?symbol=$pair&limit=30")
        val oi = get("$BN/fapi/v1/openInterest?symbol=$pair")
        val oiH = get("$BN/futures/data/openInterestHist?symbol=$pair&period=5m&limit=50")
        val ls = get("$BN/futures/data/globalLongShortAccountRatio?symbol=$pair&period=5m&limit=30")
        val taker = get("$BN/futures/data/takerlongshortRatio?symbol=$pair&period=5m&limit=30")
        val prem = get("$BN/fapi/v1/premiumIndex?symbol=$pair")
        val k1 = get("$BN/fapi/v1/klines?symbol=$pair&interval=1m&limit=600")
        val k3 = get("$BN/fapi/v1/klines?symbol=$pair&interval=3m&limit=600")
        val k5 = get("$BN/fapi/v1/klines?symbol=$pair&interval=5m&limit=600")
        val k15 = get("$BN/fapi/v1/klines?symbol=$pair&interval=15m&limit=600")
        val k1h = get("$BN/fapi/v1/klines?symbol=$pair&interval=1h&limit=200")

        val tObj = ticker.asObj()
        val prices = mutableListOf<NamedPrice>()
        val last = tObj?.num("lastPrice") ?: 0.0
        if (last > 0) prices += NamedPrice("Binance", last)

        return Bundle(
            prices = prices,
            chg24 = tObj?.num("priceChangePercent") ?: 0.0,
            vol24 = tObj?.num("quoteVolume") ?: 0.0,
            oi = oi.asObj()?.num("openInterest") ?: 0.0,
            oiHist = oiH.asArr()?.map { it.asObj()?.num("sumOpenInterest") ?: 0.0 } ?: emptyList(),
            books = parseBook("binance", depth),
            trades = trades.asArr()?.mapNotNull { row ->
                val o = row.asObj() ?: return@mapNotNull null
                TradePrint(o.num("price"), o.num("qty"), o.str("isBuyerMaker") == "true" || o["isBuyerMaker"].toString().contains("true"))
            } ?: emptyList(),
            funding = funding.asArr()?.map { it.asObj()?.num("fundingRate") ?: 0.0 } ?: emptyList(),
            ls = ls.asArr()?.firstOrNull()?.asObj()?.num("longShortRatio")?.takeIf { it > 0 },
            taker = taker.asArr()?.mapNotNull { row ->
                val o = row.asObj() ?: return@mapNotNull null
                TakerBar(
                    timestamp = o.num("timestamp").toLong(),
                    buyVol = o.num("buyVol", "sumTakerBuyVolume"),
                    sellVol = o.num("sellVol", "sumTakerSellVolume"),
                )
            } ?: emptyList(),
            k1 = parseKlines(k1),
            k3 = parseKlines(k3),
            k5 = parseKlines(k5),
            k15 = parseKlines(k15),
            k1h = parseKlines(k1h),
            nextFundingMs = prem.asObj()?.num("nextFundingTime")?.toLong() ?: 0L,
        )
    }

    private fun bybit(pair: String): Bundle {
        val tick = get("$BY/v5/market/tickers?category=linear&symbol=$pair")
        val ob = get("$BY/v5/market/orderbook?category=linear&symbol=$pair&limit=50")
        val trades = get("$BY/v5/market/recent-trade?category=linear&symbol=$pair&limit=100")
        val oi = get("$BY/v5/market/open-interest?category=linear&symbol=$pair&intervalTime=5min&limit=50")
        val fund = get("$BY/v5/market/funding/history?category=linear&symbol=$pair&limit=30")
        val ls = get("$BY/v5/market/account-ratio?category=linear&symbol=$pair&period=5min&limit=50")

        val row = tick.path("result", "list", 0).asObj()
        val prices = mutableListOf<NamedPrice>()
        val last = row?.num("lastPrice") ?: 0.0
        if (last > 0) prices += NamedPrice("Bybit", last)

        val oiList = oi.path("result", "list").asArr()
        val oiNewest = oiList?.firstOrNull()?.asObj()?.num("openInterest") ?: 0.0
        val oiHist = oiList?.map { it.asObj()?.num("openInterest") ?: 0.0 } ?: emptyList()

        val lsRow = ls.path("result", "list", 0).asObj()
        val buy = lsRow?.num("buyRatio") ?: 0.0
        val sell = lsRow?.num("sellRatio") ?: 0.0
        val lsVal = if (sell > 0) buy / sell else null

        val tList = trades.path("result", "list").asArr()
        return Bundle(
            prices = prices,
            oi = oiNewest,
            oiHist = oiHist,
            books = parseBybitBook(ob),
            trades = tList?.mapNotNull { r ->
                val o = r.asObj() ?: return@mapNotNull null
                val side = o.str("side").lowercase()
                TradePrint(o.num("price"), o.num("size", "qty"), buyerMaker = side == "sell")
            } ?: emptyList(),
            funding = fund.path("result", "list").asArr()?.map { it.asObj()?.num("fundingRate") ?: 0.0 } ?: emptyList(),
            ls = lsVal,
        )
    }

    private fun okx(inst: String): Bundle {
        val tick = get("$OK/api/v5/market/ticker?instId=$inst")
        val books = get("$OK/api/v5/market/books?instId=$inst&sz=50")
        val trades = get("$OK/api/v5/market/trades?instId=$inst&limit=100")
        val fund = get("$OK/api/v5/public/funding-rate-history?instId=$inst&limit=30")
        val oi = get("$OK/api/v5/public/open-interest?instId=$inst")

        val t = tick.path("data", 0).asObj()
        val prices = mutableListOf<NamedPrice>()
        val last = t?.num("last") ?: 0.0
        if (last > 0) prices += NamedPrice("OKX", last)

        val bookRow = books.path("data", 0).asObj()
        val bids = bookRow?.get("bids").asArr()?.mapNotNull { lv ->
            val a = lv.asArr() ?: return@mapNotNull null
            a.getOrNull(0).asDouble() to a.getOrNull(1).asDouble()
        } ?: emptyList()
        val asks = bookRow?.get("asks").asArr()?.mapNotNull { lv ->
            val a = lv.asArr() ?: return@mapNotNull null
            a.getOrNull(0).asDouble() to a.getOrNull(1).asDouble()
        } ?: emptyList()

        return Bundle(
            prices = prices,
            oi = oi.path("data", 0).asObj()?.num("oi") ?: 0.0,
            books = Analyzers.orderBook(bids, asks)?.let { mapOf("okx" to it) } ?: emptyMap(),
            trades = trades.path("data").asArr()?.mapNotNull { r ->
                val o = r.asObj() ?: return@mapNotNull null
                TradePrint(o.num("px"), o.num("sz"), buyerMaker = o.str("side") == "sell")
            } ?: emptyList(),
            funding = fund.path("data").asArr()?.map { it.asObj()?.num("fundingRate") ?: 0.0 } ?: emptyList(),
        )
    }

    private fun parseBook(name: String, el: JsonElement?): Map<String, OrderBook> {
        val o = el.asObj() ?: return emptyMap()
        val bids = o["bids"].asArr()?.mapNotNull { row ->
            val a = row.asArr() ?: return@mapNotNull null
            a.getOrNull(0).asDouble() to a.getOrNull(1).asDouble()
        } ?: return emptyMap()
        val asks = o["asks"].asArr()?.mapNotNull { row ->
            val a = row.asArr() ?: return@mapNotNull null
            a.getOrNull(0).asDouble() to a.getOrNull(1).asDouble()
        } ?: return emptyMap()
        val book = Analyzers.orderBook(bids, asks) ?: return emptyMap()
        return mapOf(name to book)
    }

    private fun parseBybitBook(el: JsonElement?): Map<String, OrderBook> {
        val res = el.path("result").asObj() ?: return emptyMap()
        val bids = res["b"].asArr()?.mapNotNull { row ->
            val a = row.asArr() ?: return@mapNotNull null
            a.getOrNull(0).asDouble() to a.getOrNull(1).asDouble()
        } ?: return emptyMap()
        val asks = res["a"].asArr()?.mapNotNull { row ->
            val a = row.asArr() ?: return@mapNotNull null
            a.getOrNull(0).asDouble() to a.getOrNull(1).asDouble()
        } ?: return emptyMap()
        val book = Analyzers.orderBook(bids, asks) ?: return emptyMap()
        return mapOf("bybit" to book)
    }

    private fun parseKlines(el: JsonElement?): List<Candle> {
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { row ->
            val a = row as? JsonArray ?: return@mapNotNull null
            if (a.size < 6) return@mapNotNull null
            Candle(
                openTime = a[0].asDouble(),
                open = a[1].asDouble(),
                high = a[2].asDouble(),
                low = a[3].asDouble(),
                close = a[4].asDouble(),
                volume = a[5].asDouble(),
            )
        }
    }

    private fun get(url: String): JsonElement? {
        val host = try { java.net.URI(url).host ?: url } catch (_: Exception) { url }
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    errors += "$host HTTP ${resp.code}"
                    android.util.Log.w("exfeed", "$host ${resp.code} $url")
                    return null
                }
                val body = resp.body?.string() ?: return null
                JsonX.parseToJsonElement(body)
            }
        } catch (e: Exception) {
            errors += "$host ${e.javaClass.simpleName}"
            android.util.Log.w("exfeed", "fail $url", e)
            null
        }
    }
}
