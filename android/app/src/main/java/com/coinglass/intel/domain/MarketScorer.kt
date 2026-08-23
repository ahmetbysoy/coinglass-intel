package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.ScoreInput
import com.coinglass.intel.domain.model.SimpleSignal
import com.coinglass.intel.domain.model.TfPred
import com.coinglass.intel.domain.model.V4Report
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object MarketScorer {
    fun score(feed: ScoreInput): V4Report {
        val warnings = mutableListOf<String>()
        val pair = feed.symbol
        val prices = feed.prices.filter { it.price > 0 }
        val price = if (prices.isEmpty()) 0.0 else {
            val sorted = prices.map { it.price }.sorted()
            val mid = sorted.size / 2
            if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
        }
        val chg24 = feed.chg24
        val vol24 = feed.vol24
        val oiBn = feed.oi

        var bid = 0.0
        var ask = 0.0
        for (m in feed.orderBooks.values) {
            if (m.bids.isNotEmpty() && m.asks.isNotEmpty() && m.mid > 0) {
                val (b25, a25, _) = Scalper.depth25bps(m.bids, m.asks, m.mid)
                bid += b25
                ask += a25
            } else {
                bid += m.bidVol
                ask += m.askVol
            }
        }
        val aggImb = if (bid + ask > 0) (bid - ask) / (bid + ask) * 100.0 else 0.0
        val hasOb = feed.orderBooks.isNotEmpty()

        val tf = Analyzers.tradesCvd(feed.trades)
        val cvdPct = tf?.third ?: 0.0
        val fund = Analyzers.funding(feed.fundingRates)
        val fundingAvg = fund?.first ?: 0.0
        val lsAvg = feed.lsRatio ?: 1.0
        val hasLs = feed.lsRatio != null
        val oiChgPct = Analyzers.oiChange(feed.oiHist) ?: 0.0
        val hasOiChg = Analyzers.oiChange(feed.oiHist) != null

        val mom = mutableMapOf<String, Map<String, Double>>()
        Indicators.candleMetrics(feed.klines5m, "5m")?.let { mom["5m"] = it }
        Indicators.candleMetrics(feed.klines15m, "15m")?.let { mom["15m"] = it }
        Indicators.candleMetrics(feed.klines1h, "1h")?.let { mom["1h"] = it }

        val tfW = mapOf("5m" to 0.25, "15m" to 0.35, "1h" to 0.40)
        var wv = 0.0
        var tw = 0.0
        for ((name, w) in tfW) {
            val mm = mom[name] ?: continue
            val vote = when {
                (mm["ret"] ?: 0.0) > 0 -> 1.0
                (mm["ret"] ?: 0.0) < 0 -> -1.0
                else -> 0.0
            }
            wv += w * vote
            tw += w
        }
        val confluence = if (tw == 0.0) 0.0 else (wv / tw) * 10.0
        val atrPct = mom["1h"]?.get("atr_pct") ?: 0.0

        val quality = mapOf(
            "order_book_quality" to if (hasOb) 80.0 else 20.0,
            "trade_flow_quality" to if (tf != null) 70.0 else 10.0,
            "oi_quality" to if (oiBn > 0) 60.0 else 10.0,
            "funding_quality" to if (fund != null) 50.0 else 10.0,
            "ls_ratio_quality" to if (hasLs) 40.0 else 10.0,
            "volume_quality" to if (vol24 > 1_000_000) 90.0 else 30.0,
            "momentum_quality" to if (mom["5m"] != null) 75.0 else 10.0,
        )
        val weights = Scalper.qualityWeights(quality)
        if (vol24 < 10_000_000) {
            weights["OB"] = weights.getValue("OB") + 3
            weights["TF"] = weights.getValue("TF") + 3
        }
        if (atrPct > 4) {
            weights["Vol"] = weights.getValue("Vol") + 3
            weights["Mom"] = weights.getValue("Mom") + 3
        }
        val tws = weights.values.sum().let { if (it == 0.0) 1.0 else it }
        for (k in weights.keys.toList()) weights[k] = weights.getValue(k) / tws * 100.0

        val closes5 = feed.klines5m.sortedBy { it.openTime }.map { it.close }
        val cvdSeries = Scalper.buildCvdSeries(feed.takerHist, if (price > 0) price else 1.0)
        val divergence = if (closes5.isNotEmpty() && cvdSeries.isNotEmpty()) {
            Scalper.detectCvdDivergence(closes5, cvdSeries)
        } else mapOf("divergence" to false, "type" to null, "strength" to 0.0)

        val oiScore = when {
            oiChgPct > 0 && chg24 > 0 -> 60.0
            oiChgPct > 0 && chg24 < 0 -> -40.0
            oiChgPct < 0 && chg24 > 0 -> 30.0
            oiChgPct < 0 && chg24 < 0 -> -60.0
            else -> 0.0
        }
        val fundingScore = max(min(-fundingAvg * 10_000.0, 100.0), -100.0)
        val liqScore = when {
            lsAvg > 2 -> -40.0
            lsAvg > 1.5 -> -20.0
            lsAvg < 0.5 -> 40.0
            lsAvg < 0.7 -> 20.0
            else -> 0.0
        }

        val m5 = mom["5m"]
        var volScore = 0.0
        if (m5 != null && (m5["vol_med"] ?: 0.0) > 0) {
            val vr = (m5["vol_last"] ?: 0.0) / (m5["vol_med"] ?: 1.0)
            volScore = if ((m5["ret_3"] ?: 0.0) > 0) min(vr * 15, 100.0) else -min(vr * 15, 100.0)
        } else if (m5 != null && mom["1h"] != null && (mom["1h"]?.get("vol_total") ?: 0.0) > 0) {
            val vr = (m5["vol_total"] ?: 0.0) / ((mom["1h"]?.get("vol_total") ?: 1.0) / 12.0)
            volScore = if ((m5["ret_3"] ?: 0.0) > 0) min(vr * 15, 100.0) else -min(vr * 15, 100.0)
        }

        val momParts = mutableListOf<Double>()
        for (tfName in listOf("5m", "15m", "1h")) {
            val mm = mom[tfName] ?: continue
            val rsi = mm["rsi"] ?: 50.0
            val rsiSig = when {
                rsi > 70 -> -30.0
                rsi > 60 -> 10.0
                rsi < 30 -> 30.0
                rsi < 40 -> -10.0
                else -> 0.0
            }
            momParts += (rsiSig + max(min((mm["ret_3"] ?: 0.0) * 10, 50.0), -50.0)) / 2.0
        }
        val momScore = (if (momParts.isEmpty()) 0.0 else momParts.average()) + confluence * 0.5

        data class Comp(val w: Double, val s: Double?)
        val scores = mapOf(
            "OB" to Comp(weights.getValue("OB"), if (hasOb) aggImb else null),
            "TF" to Comp(weights.getValue("TF"), if (tf != null) cvdPct else null),
            "OI" to Comp(weights.getValue("OI"), if (oiBn > 0 || hasOiChg) oiScore else null),
            "Funding" to Comp(weights.getValue("Funding"), if (fund != null) fundingScore else null),
            "Liq" to Comp(weights.getValue("Liq"), if (hasLs) liqScore else null),
            "Vol" to Comp(weights.getValue("Vol"), if (m5 != null) volScore else null),
            "Mom" to Comp(weights.getValue("Mom"), if (mom.isNotEmpty()) momScore else null),
        )
        val available = scores.values.mapNotNull { c -> c.s?.let { c.w to it } }
        var total: Double
        var coverage: Double
        if (available.isEmpty()) {
            total = 0.0
            coverage = 0.0
        } else {
            val sw = available.sumOf { it.first }
            total = available.sumOf { it.first * it.second } / sw
            coverage = sw
        }
        total = max(min(total, 100.0), -100.0)
        val direction = when {
            total > 30 -> "BULLISH"
            total > 10 -> "HAFIF BULLISH"
            total < -30 -> "BEARISH"
            total < -10 -> "HAFIF BEARISH"
            else -> "NEUTRAL"
        }

        var risk = 0
        if (atrPct > 4) risk += 20 else if (atrPct > 2) risk += 10
        if (abs(fundingAvg) > 0.01) risk += 10
        if (lsAvg > 2 || lsAvg < 0.5) risk += 15
        if (vol24 < 1_000_000) risk += 20 else if (vol24 < 10_000_000) risk += 10
        val spoof = analyzeSpoof(feed, lsAvg, aggImb)
        val strat = generateStrategy(price, direction, atrPct, fundingAvg, lsAvg, aggImb)

        fun sig(raw: Double) = SimpleSignal(
            directionalScore = max(min(raw / 100.0, 1.0), -1.0),
            signalStrength = min(abs(raw / 100.0) * 1.4, 1.0),
            currentPrice = price,
        )
        val signals = mapOf(
            "oi_momentum" to sig(oiScore),
            "funding_signal" to sig(fundingScore),
            "liq_pressure" to sig(liqScore),
            "ob_imbalance" to sig(aggImb),
            "volume_signal" to sig(volScore),
            "whale_flow" to sig(cvdPct),
        )
        val tfPreds = ensembleTf(signals, price)

        val lines = mutableListOf(
            "$pair  ${fmtPrice(price)}  24h ${"%+.2f".format(chg24)}%  vol ${fmtUsd(vol24)}",
            "YON: $direction   skor ${"%+.1f".format(total)}/100   confluence ${"%+.1f".format(confluence)}",
            "OI ${"%,.0f".format(oiBn)} (${"%+.2f".format(oiChgPct)}%)  fund ${"%+.4f".format(fundingAvg * 100)}%  L/S ${"%.3f".format(lsAvg)}  OB imb ${"%+.1f".format(aggImb)}%",
            "CVD ${"%+.2f".format(cvdPct)}%  ATR% ${"%.2f".format(atrPct)}  risk ${min(risk, 100)}/100  spoof $spoof/100",
        )
        mom["5m"]?.let { m5r ->
            lines += "5m WilderRSI ${"%.1f".format(m5r["rsi"])}  StochRSI ${"%.1f".format(m5r["stoch_rsi"])}  MACD ${"%+.5f".format(m5r["histogram"])}"
        }
        if (divergence["divergence"] == true) {
            lines += "CVD DIVERGENCE ${divergence["type"].toString().uppercase()}  str=${"%.4f".format(toFloat(divergence["strength"]))}"
        }
        lines += "coverage ${"%.0f".format(coverage)}%"
        lines += strat.strategy
        strat.warnings.forEach { lines += "  ! $it" }
        if (feed.liveLiqLong + feed.liveLiqShort > 0) {
            lines += "CG liq L ${fmtUsd(feed.liveLiqLong)} / S ${fmtUsd(feed.liveLiqShort)}"
        }

        return V4Report(
            symbol = pair,
            price = price,
            chg24 = chg24,
            vol24 = vol24,
            direction = direction,
            totalScore = total,
            confluence = confluence,
            risk = min(risk, 100),
            spoof = spoof,
            strategy = strat.strategy,
            strategyWarnings = strat.warnings,
            forecasts = mapOf(
                "1m" to momScore * 0.03,
                "5m" to momScore * 0.05,
                "15m" to momScore * 0.1 + volScore * 0.05,
            ),
            component = mapOf(
                "ob" to aggImb, "tf" to cvdPct, "oi" to oiScore, "funding" to fundingScore,
                "liq" to liqScore, "vol" to volScore, "mom" to momScore, "confluence" to confluence,
            ),
            signals = signals,
            text = lines.joinToString("\n"),
            warnings = warnings,
            coverage = coverage,
            divergence = divergence,
            tfPreds = tfPreds,
            sl = strat.sl,
            tp = strat.tp,
            oi = oiBn,
            funding = fundingAvg,
            ls = lsAvg,
            cvdPct = cvdPct,
            atrPct = atrPct,
            liqLong = feed.liveLiqLong,
            liqShort = feed.liveLiqShort,
            rsi5m = mom["5m"]?.get("rsi") ?: 50.0,
        )
    }

    private fun analyzeSpoof(feed: ScoreInput, lsAvg: Double, aggImbalance: Double): Int {
        var spoof = 0
        val bob = feed.orderBooks["binance"] ?: feed.orderBooks.values.firstOrNull()
        if (bob != null && bob.bids.isNotEmpty() && bob.asks.isNotEmpty()) {
            val medB = median(bob.bids.map { it.second }).let { if (it == 0.0) 1.0 else it }
            val medA = median(bob.asks.map { it.second }).let { if (it == 0.0) 1.0 else it }
            val mid = bob.mid
            for ((p, q) in bob.bids) {
                if (mid > 0 && (mid - p) / mid * 100 > 0.5 && q > medB * 10) {
                    spoof += 25
                    break
                }
            }
            for ((p, q) in bob.asks) {
                if (mid > 0 && (p - mid) / mid * 100 > 0.5 && q > medA * 10) {
                    spoof += 25
                    break
                }
            }
            if (abs(aggImbalance) > 30) spoof += 20
        }
        if (lsAvg > 2.5) spoof += 15
        return min(spoof, 100)
    }

    private data class Strat(val strategy: String, val warnings: List<String>, val sl: Double, val tp: Double)

    private fun generateStrategy(
        price: Double,
        direction: String,
        atrPct: Double,
        funding: Double,
        lsAvg: Double,
        imb: Double,
    ): Strat {
        val slPct = if (atrPct > 0) max(0.5, atrPct * 1.5) else 1.0
        val tpPct = slPct * 2
        val sl: Double
        val tp: Double
        val strategy = when {
            "BULL" in direction -> {
                sl = price * (1 - slPct / 100)
                tp = price * (1 + tpPct / 100)
                "LONG entry ~${fmtPrice(price)}  SL ${fmtPrice(sl)} (-${"%.2f".format(slPct)}%)  TP ${fmtPrice(tp)} (+${"%.2f".format(tpPct)}%)"
            }
            "BEAR" in direction -> {
                sl = price * (1 + slPct / 100)
                tp = price * (1 - tpPct / 100)
                "SHORT entry ~${fmtPrice(price)}  SL ${fmtPrice(sl)} (+${"%.2f".format(slPct)}%)  TP ${fmtPrice(tp)} (-${"%.2f".format(tpPct)}%)"
            }
            else -> {
                sl = price * 0.99
                tp = price * 1.01
                "NEUTRAL — range. Destek ${fmtPrice(sl)} / Direnc ${fmtPrice(tp)}"
            }
        }
        val warns = mutableListOf<String>()
        if (funding < -0.0005) warns += "Funding negatif — short squeeze potansiyeli"
        if (funding > 0.0005) warns += "Funding pozitif — long crowded, squeeze riski"
        if (lsAvg > 2) warns += "L/S ${"%.2f".format(lsAvg)} yuksek — long cascade riski"
        if (imb > 30) warns += "OB bid agirligi +${"%.1f".format(imb)}%"
        if (imb < -30) warns += "OB ask agirligi ${"%.1f".format(imb)}%"
        return Strat(strategy, warns, sl, tp)
    }

    private val ensembleWeights = mapOf(
        "oi_momentum" to 0.20,
        "funding_signal" to 0.15,
        "liq_pressure" to 0.20,
        "ob_imbalance" to 0.15,
        "volume_signal" to 0.15,
        "whale_flow" to 0.15,
    )
    private val tfMods = mapOf(
        "1m" to mapOf(
            "oi_momentum" to 0.6, "funding_signal" to 0.3, "liq_pressure" to 0.8,
            "ob_imbalance" to 1.5, "volume_signal" to 1.2, "whale_flow" to 1.4,
        ),
        "5m" to mapOf(
            "oi_momentum" to 1.0, "funding_signal" to 0.7, "liq_pressure" to 1.2,
            "ob_imbalance" to 1.0, "volume_signal" to 1.0, "whale_flow" to 1.0,
        ),
        "15m" to mapOf(
            "oi_momentum" to 1.4, "funding_signal" to 1.3, "liq_pressure" to 1.0,
            "ob_imbalance" to 0.6, "volume_signal" to 0.9, "whale_flow" to 0.7,
        ),
    )

    private fun ensembleTf(signals: Map<String, SimpleSignal>, price: Double): List<TfPred> {
        return listOf("1m", "5m", "15m").map { tf ->
            val mods = tfMods[tf] ?: emptyMap()
            var ws = 0.0
            var wt = 0.0
            for ((k, bw) in ensembleWeights) {
                val sig = signals[k]
                val sc = sig?.directionalScore ?: 0.0
                val st = max(sig?.signalStrength ?: 0.0, 0.1)
                val ew = bw * (mods[k] ?: 1.0) * st
                ws += sc * ew
                wt += ew
            }
            val fs = if (wt > 0) ws / wt else 0.0
            val dir = when {
                fs > 0.08 -> "UP"
                fs < -0.08 -> "DOWN"
                else -> "FLAT"
            }
            val confMul = when (tf) {
                "1m" -> 0.75
                "5m" -> 0.90
                else -> 1.0
            }
            val active = signals.filter { abs(it.value.directionalScore) > 0.05 }
            val agr = if (active.isEmpty()) 0.1 else {
                val signs = active.values.map { if (it.directionalScore >= 0) 1 else -1 }
                val maj = signs.groupingBy { it }.eachCount().maxBy { it.value }.key
                signs.count { it == maj }.toDouble() / signs.size
            }
            val conf = min(agr * confMul, 0.95)
            val emMul = when (tf) {
                "1m" -> 0.05
                "5m" -> 0.15
                else -> 0.35
            }
            TfPred(
                timeframe = tf,
                direction = dir,
                confidence = conf,
                weightedScore = fs,
                expectedMovePct = abs(fs) * emMul * 100.0,
                risk = when {
                    conf > 0.7 -> "low"
                    conf > 0.5 -> "medium"
                    else -> "high"
                },
            )
        }
    }

    private fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2.0 else s[m]
    }
}
