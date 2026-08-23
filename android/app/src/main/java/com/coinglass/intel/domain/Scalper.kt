package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.TakerBar
import kotlin.math.abs
import kotlin.math.max

object Scalper {
    fun wilderRsi(closes: List<Double>, period: Int = 14): Double {
        if (closes.size < period + 1) return 50.0
        val deltas = (1 until closes.size).map { closes[it] - closes[it - 1] }
        val gains = deltas.map { max(it, 0.0) }.toMutableList()
        val losses = deltas.map { max(-it, 0.0) }.toMutableList()
        var avgGain = gains.take(period).average()
        var avgLoss = losses.take(period).average()
        for (i in period until gains.size) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period
        }
        if (avgGain == 0.0 && avgLoss == 0.0) return 50.0
        if (avgLoss == 0.0) return 100.0
        if (avgGain == 0.0) return 0.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun linearSlope(xs: List<Double>): Double {
        val n = xs.size
        if (n < 2) return 0.0
        val xMean = xs.average()
        val yMean = (n - 1) / 2.0
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            num += (xs[i] - xMean) * i
            den += (i - yMean) * (i - yMean)
        }
        return if (den == 0.0) 0.0 else num / den
    }

    fun volWeightedSlope(xs: List<Double>, vols: List<Double>): Double {
        val n = minOf(xs.size, vols.size)
        if (n < 3) return 0.0
        val x = xs.takeLast(n)
        val w = vols.takeLast(n).map { max(it, 1e-9) }
        val wSum = w.sum()
        val xMean = x.zip(w).sumOf { it.first * it.second } / wSum
        var iMean = 0.0
        for (i in 0 until n) iMean += i * w[i]
        iMean /= wSum
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            num += w[i] * (x[i] - xMean) * (i - iMean)
            den += w[i] * (i - iMean) * (i - iMean)
        }
        return if (den == 0.0) 0.0 else num / den
    }

    fun detectCvdDivergence(
        priceSeries: List<Double>,
        cvdSeries: List<Double>,
        volumes: List<Double> = emptyList(),
        lookback: Int = 20,
    ): Map<String, Any?> {
        if (priceSeries.size < lookback || cvdSeries.size < lookback) {
            return mapOf("divergence" to false, "type" to null, "strength" to 0.0)
        }
        val px = priceSeries.takeLast(lookback)
        val cv = cvdSeries.takeLast(lookback)
        val vs = if (volumes.size >= lookback) volumes.takeLast(lookback) else List(lookback) { 1.0 }
        val lastVol = vs.takeLast(5).average()
        val medVol = vs.sorted()[vs.size / 2]
        if (medVol > 0 && lastVol < medVol * 0.55) {
            return mapOf("divergence" to false, "type" to null, "strength" to 0.0, "filtered" to "low_volume")
        }
        val ps = volWeightedSlope(px, vs)
        val cs = volWeightedSlope(cv, vs)
        return when {
            ps > 0 && cs < 0 -> mapOf("divergence" to true, "type" to "bearish", "strength" to abs(ps) + abs(cs))
            ps < 0 && cs > 0 -> mapOf("divergence" to true, "type" to "bullish", "strength" to abs(ps) + abs(cs))
            else -> mapOf("divergence" to false, "type" to null, "strength" to 0.0)
        }
    }

    fun buildCvdSeries(takerHist: List<TakerBar>, currentPrice: Double = 1.0): List<Double> {
        if (takerHist.isEmpty()) return emptyList()
        val hist = takerHist.sortedBy { it.timestamp }
        var running = 0.0
        return hist.map { bar ->
            running += (bar.buyVol - bar.sellVol) * currentPrice
            running
        }
    }

    fun evAndBand(momentum: Map<String, Map<String, Double>>, price: Double): Pair<Map<String, Double>, Map<String, Pair<Double, Double>>> {
        val biases = mutableMapOf<String, Double>()
        val bands = mutableMapOf<String, Pair<Double, Double>>()
        if (price <= 0) return biases to bands
        for ((tf, mm) in momentum) {
            val rsi = mm["rsi"] ?: 50.0
            val ret3 = mm["ret_3"] ?: 0.0
            val bias = when {
                rsi < 30 -> ret3 + 0.5
                rsi > 70 -> ret3 - 0.5
                else -> ret3
            }
            val atrPct = max(mm["atr_pct"] ?: 0.0, 0.0)
            val half = (atrPct / 100.0) * 1.5
            biases[tf] = bias
            bands[tf] = (price * (1.0 - half)) to (price * (1.0 + half))
        }
        return biases to bands
    }

    fun depth25bps(
        bids: List<Pair<Double, Double>>,
        asks: List<Pair<Double, Double>>,
        mid: Double,
        bps: Double = 25.0,
    ): Triple<Double, Double, Double> {
        if (mid == 0.0 || bids.isEmpty() || asks.isEmpty()) return Triple(0.0, 0.0, 0.0)
        val lim = bps / 10_000.0
        val lo = mid * (1.0 - lim)
        val hi = mid * (1.0 + lim)
        val bidD = bids.filter { it.first >= lo }.sumOf { it.second }
        val askD = asks.filter { it.first <= hi }.sumOf { it.second }
        val tot = bidD + askD
        val imb = if (tot == 0.0) 0.0 else (bidD - askD) / tot * 100.0
        return Triple(bidD, askD, imb)
    }

    fun qualityWeights(quality: Map<String, Double>): MutableMap<String, Double> {
        val w = mutableMapOf(
            "OB" to 20.0, "TF" to 20.0, "OI" to 15.0, "Funding" to 10.0,
            "Liq" to 15.0, "Vol" to 10.0, "Mom" to 10.0,
        )
        val mapping = mapOf(
            "order_book_quality" to "OB",
            "trade_flow_quality" to "TF",
            "oi_quality" to "OI",
            "funding_quality" to "Funding",
            "ls_ratio_quality" to "Liq",
            "volume_quality" to "Vol",
            "momentum_quality" to "Mom",
        )
        for ((qk, fk) in mapping) {
            val q = (quality[qk] ?: 50.0) / 100.0
            w[fk] = w.getValue(fk) * (0.5 + 0.5 * q)
        }
        val tot = w.values.sum().let { if (it == 0.0) 1.0 else it }
        for (k in w.keys.toList()) w[k] = w.getValue(k) / tot * 100.0
        return w
    }
}
