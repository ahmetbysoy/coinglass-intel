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
        Indicators.candleMetrics(feed.klines1m, "1m")?.let { mom["1m"] = it }
        Indicators.candleMetrics(feed.klines3m, "3m")?.let { mom["3m"] = it }
        Indicators.candleMetrics(feed.klines5m, "5m")?.let { mom["5m"] = it }
        Indicators.candleMetrics(feed.klines15m, "15m")?.let { mom["15m"] = it }
        Indicators.candleMetrics(feed.klines1h, "1h")?.let { mom["1h"] = it }

        val tfW = mapOf("1m" to 0.15, "3m" to 0.20, "5m" to 0.30, "15m" to 0.35)
        var wv = 0.0
        var tw = 0.0
        for ((name, w) in tfW) {
            val mm = mom[name] ?: continue
            val atr = max(mm["atr_pct"] ?: 0.15, 0.15)
            val mag = kotlin.math.tanh((mm["ret"] ?: 0.0) / atr)
            wv += w * mag
            tw += w
        }
        val confluence = if (tw == 0.0) 0.0 else (wv / tw) * 10.0
        val atrPct = mom["15m"]?.get("atr_pct") ?: mom["5m"]?.get("atr_pct") ?: mom["1h"]?.get("atr_pct") ?: 0.0
        val atrHist = feed.klines15m.mapNotNull {
            val tr = it.high - it.low
            if (it.close > 0) tr / it.close * 100.0 else null
        }

        val quality = mapOf(
            "order_book_quality" to if (hasOb) 80.0 else 20.0,
            "trade_flow_quality" to if (tf != null) 70.0 else 10.0,
            "oi_quality" to if (oiBn > 0) 60.0 else 10.0,
            "funding_quality" to if (fund != null) 50.0 else 10.0,
            "ls_ratio_quality" to if (hasLs) 40.0 else 10.0,
            "volume_quality" to if (vol24 > 1_000_000) 90.0 else 30.0,
            "momentum_quality" to if (mom["5m"] != null || mom["1m"] != null) 75.0 else 10.0,
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
        WeightCalibrator.apply(weights, feed.weightBoost)
        val tws = weights.values.sum().let { if (it == 0.0) 1.0 else it }
        for (k in weights.keys.toList()) weights[k] = weights.getValue(k) / tws * 100.0

        val closes5 = feed.klines5m.sortedBy { it.openTime }.map { it.close }
        val vols5 = feed.klines5m.sortedBy { it.openTime }.map { it.volume }
        val cvdSeries = Scalper.buildCvdSeries(feed.takerHist, if (price > 0) price else 1.0)
        val divergence = if (closes5.isNotEmpty() && cvdSeries.isNotEmpty()) {
            Scalper.detectCvdDivergence(closes5, cvdSeries, vols5)
        } else mapOf("divergence" to false, "type" to null, "strength" to 0.0)
        val struct = Structure.from(
            feed.klines15m.ifEmpty { feed.klines5m },
            feed.orderBooks["binance"] ?: feed.orderBooks.values.firstOrNull(),
        )

        val oiScore = Curves.oiScore(oiChgPct, chg24)
        val fundingScore = max(min(-fundingAvg * 10_000.0, 100.0), -100.0)
        val liqScore = Curves.lsScore(lsAvg)

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
        for (tfName in listOf("1m", "3m", "5m", "15m")) {
            val mm = mom[tfName] ?: continue
            val rsiSig = Curves.rsiSignal(mm["rsi"] ?: 50.0)
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

        val risk = Curves.riskScore(atrPct, fundingAvg, lsAvg, vol24, atrHist)
        val snapSpoof = analyzeSpoof(feed, lsAvg, aggImb)
        val histSpoof = Structure.spoofFromHistory(feed.bookHistory)
        val spoof = min(100, max(snapSpoof / 2, histSpoof))
        if (feed.symbol != "BTCUSDT" && abs(feed.btcChg24) > 1.2) {
            if ("BULL" in direction && feed.btcChg24 < -1.2) {
                warnings += "BTC ${"%+.2f".format(feed.btcChg24)}% duserken alt long — capraz risk"
            }
            if ("BEAR" in direction && feed.btcChg24 > 1.2) {
                warnings += "BTC ${"%+.2f".format(feed.btcChg24)}% yukselirken alt short — capraz risk"
            }
        }
        val strat = generateStrategy(
            price, direction, atrPct, fundingAvg, lsAvg, aggImb, total, struct, spoof, feed.minutesToFunding,
        )
        val why = whyLine(aggImb, cvdPct, oiScore, fundingScore)
        val verdict = Verdict.evaluate(
            direction = direction,
            score = total,
            coverage = coverage,
            confluence = confluence,
            spoof = spoof,
            risk = risk,
            netRr = strat.netRr,
            why = why,
        )

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
            "momentum" to sig(momScore),
        )
        val tfPreds = ensembleTf(signals, WeightCalibrator.toEnsemble(weights))

        val lines = mutableListOf(
            "$pair  ${fmtPrice(price)}  24h ${"%+.2f".format(chg24)}%  vol ${fmtUsd(vol24)}",
            "YON: $direction   skor ${"%+.1f".format(total)}/100   confluence ${"%+.1f".format(confluence)}",
            "OI ${"%,.0f".format(oiBn)} (${"%+.2f".format(oiChgPct)}%)  fund ${"%+.4f".format(fundingAvg * 100)}%  L/S ${"%.3f".format(lsAvg)}  OB imb ${"%+.1f".format(aggImb)}%",
            "CVD ${"%+.2f".format(cvdPct)}%  ATR% ${"%.2f".format(atrPct)}  risk $risk/100  spoof $spoof/100",
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
            risk = risk,
            spoof = spoof,
            strategy = strat.strategy,
            strategyWarnings = strat.warnings,
            forecasts = mapOf(
                "1m" to momScore * 0.03,
                "3m" to momScore * 0.04,
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
            rsi5m = mom["5m"]?.get("rsi") ?: mom["1m"]?.get("rsi") ?: 50.0,
            rsiTf = listOf("1m", "3m", "5m", "15m").mapNotNull { tf ->
                mom[tf]?.get("rsi")?.let { tf to it }
            }.toMap(),
            liqSeen = feed.liqSeen,
            restErrors = feed.restErrors,
            support = struct.support,
            resistance = struct.resistance,
            bidWall = struct.bidWall,
            askWall = struct.askWall,
            slReason = strat.reason,
            why = why,
            nextFundingMs = feed.nextFundingMs,
            netRr = strat.netRr,
            grade = verdict.grade,
            verdict = verdict.line,
            enterOk = verdict.enterOk,
            poc = struct.poc,
            divergeType = (divergence["type"] as? String).orEmpty(),
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

    private data class Strat(
        val strategy: String,
        val warnings: List<String>,
        val sl: Double,
        val tp: Double,
        val reason: String,
        val netRr: Double,
    )

    private fun generateStrategy(
        price: Double,
        direction: String,
        atrPct: Double,
        funding: Double,
        lsAvg: Double,
        imb: Double,
        totalScore: Double,
        structure: StructureLevels,
        spoofScore: Int,
        minutesToFunding: Double,
    ): Strat {
        val lv = Curves.slTp(price, direction, atrPct, totalScore, structure, spoofScore, funding, minutesToFunding)
        val sl = lv.sl
        val tp = lv.tp
        val slPct = lv.slPct
        val tpPct = lv.tpPct
        val rr = lv.netRr
        val strategy = when {
            "BULL" in direction ->
                "LONG entry ~${fmtPrice(price)}  SL ${fmtPrice(sl)} (-${"%.2f".format(slPct)}%)  TP ${fmtPrice(tp)} (+${"%.2f".format(tpPct)}%)  netRR ${"%.2f".format(rr)}"
            "BEAR" in direction ->
                "SHORT entry ~${fmtPrice(price)}  SL ${fmtPrice(sl)} (+${"%.2f".format(slPct)}%)  TP ${fmtPrice(tp)} (-${"%.2f".format(tpPct)}%)  netRR ${"%.2f".format(rr)}"
            else ->
                "NEUTRAL — range. Destek ${fmtPrice(sl)} / Direnc ${fmtPrice(tp)}"
        }
        val warns = mutableListOf<String>()
        if (funding < -0.0005) warns += "Funding negatif — short squeeze potansiyeli"
        if (funding > 0.0005) warns += "Funding pozitif — long crowded, squeeze riski"
        if (lsAvg > 2) warns += "L/S ${"%.2f".format(lsAvg)} yuksek — long cascade riski"
        if (imb > 30) warns += "OB bid agirligi +${"%.1f".format(imb)}%"
        if (imb < -30) warns += "OB ask agirligi ${"%.1f".format(imb)}%"
        if (lv.reason.isNotBlank()) warns += "SL/TP kaynak: ${lv.reason}"
        return Strat(strategy, warns, sl, tp, lv.reason)
    }

    private val tfMods = mapOf(
        "1m" to mapOf(
            "oi_momentum" to 0.6, "funding_signal" to 0.3, "liq_pressure" to 0.8,
            "ob_imbalance" to 1.5, "volume_signal" to 1.2, "whale_flow" to 1.4,
            "momentum" to 1.3,
        ),
        "3m" to mapOf(
            "oi_momentum" to 0.8, "funding_signal" to 0.5, "liq_pressure" to 1.0,
            "ob_imbalance" to 1.3, "volume_signal" to 1.1, "whale_flow" to 1.2,
            "momentum" to 1.15,
        ),
        "5m" to mapOf(
            "oi_momentum" to 1.0, "funding_signal" to 0.7, "liq_pressure" to 1.2,
            "ob_imbalance" to 1.0, "volume_signal" to 1.0, "whale_flow" to 1.0,
            "momentum" to 1.0,
        ),
        "15m" to mapOf(
            "oi_momentum" to 1.4, "funding_signal" to 1.3, "liq_pressure" to 1.0,
            "ob_imbalance" to 0.6, "volume_signal" to 0.9, "whale_flow" to 0.7,
            "momentum" to 0.85,
        ),
    )

    private fun whyLine(ob: Double, tf: Double, oi: Double, fund: Double): String {
        data class Bit(val name: String, val v: Double, val txt: String)
        return listOf(
            Bit("OB", ob, if (ob > 0) "bid agir" else "ask agir"),
            Bit("CVD", tf, if (tf > 0) "alim baskisi" else "satim baskisi"),
            Bit("OI", oi, if (oi > 0) "pozisyon aciliyor" else "pozisyon kapaniyor"),
            Bit("FUND", fund, if (fund > 0) "short crowded" else "long crowded"),
        ).sortedByDescending { abs(it.v) }.take(2).joinToString(" + ") { "${it.name} ${it.txt}" }
    }

    private fun ensembleTf(signals: Map<String, SimpleSignal>, weights: Map<String, Double>): List<TfPred> {
        val base = if (weights.isEmpty()) mapOf(
            "oi_momentum" to 0.18,
            "funding_signal" to 0.12,
            "liq_pressure" to 0.18,
            "ob_imbalance" to 0.14,
            "volume_signal" to 0.13,
            "whale_flow" to 0.13,
            "momentum" to 0.12,
        ) else weights
        return listOf("1m", "3m", "5m", "15m").map { tf ->
            val mods = tfMods[tf] ?: emptyMap()
            var ws = 0.0
            var wt = 0.0
            for ((k, bw) in base) {
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
                "3m" -> 0.82
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
                "3m" -> 0.09
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
