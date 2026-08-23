package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.OrderBook
import com.coinglass.intel.domain.model.TradePrint

object Analyzers {
    fun orderBook(bidsIn: List<Pair<Double, Double>>, asksIn: List<Pair<Double, Double>>): OrderBook? {
        if (bidsIn.isEmpty() || asksIn.isEmpty()) return null
        val bids = bidsIn.filter { it.first > 0 && it.second > 0 }
        val asks = asksIn.filter { it.first > 0 && it.second > 0 }
        if (bids.isEmpty() || asks.isEmpty()) return null
        val mid = (bids.first().first + asks.first().first) / 2.0
        val bv = bids.sumOf { it.second }
        val av = asks.sumOf { it.second }
        val tot = bv + av
        return OrderBook(
            bids = bids,
            asks = asks,
            mid = mid,
            bidVol = bv,
            askVol = av,
            imbalance = if (tot == 0.0) 0.0 else (bv - av) / tot * 100.0,
            spreadPct = if (bids.first().first == 0.0) 0.0 else
                (asks.first().first - bids.first().first) / bids.first().first * 100.0,
        )
    }

    fun tradesCvd(trades: List<TradePrint>): Triple<Double, Double, Double>? {
        if (trades.isEmpty()) return null
        val buy = trades.filter { !it.buyerMaker }.sumOf { it.qty }
        val sell = trades.filter { it.buyerMaker }.sumOf { it.qty }
        val tot = buy + sell
        val cvdPct = if (tot == 0.0) 0.0 else (buy - sell) / tot * 100.0
        return Triple(buy, sell, cvdPct)
    }

    fun funding(rates: List<Double>): Triple<Double, Double, String>? {
        if (rates.isEmpty()) return null
        val current = rates.first()
        val avg = rates.average()
        val trend = when {
            current > avg -> "up"
            current < avg -> "down"
            else -> "flat"
        }
        return Triple(current, avg, trend)
    }

    fun oiChange(hist: List<Double>): Double? {
        if (hist.size < 2) return null
        val newest = hist.first()
        val oldest = hist.last()
        if (oldest == 0.0) return null
        return (newest - oldest) / oldest * 100.0
    }
}
