package com.coinglass.intel.domain

import kotlin.math.abs

enum class AlarmKind { PRICE, SCORE, FUNDING }

enum class AlarmOp { GTE, LTE }

data class AlarmSpec(
    val id: Long,
    val symbol: String,
    val kind: AlarmKind,
    val op: AlarmOp,
    val threshold: Double,
    val enabled: Boolean = true,
    val label: String = "",
)

data class AlarmQuote(
    val symbol: String,
    val price: Double,
    val score: Double,
    val funding: Double,
)

data class AlarmHit(
    val alarm: AlarmSpec,
    val value: Double,
)

/** User alarms. Pure. No coin list. Dedup is 10dk / alarmId. */
object AlarmEngine {
    const val DEDUP_MS = 10 * 60_000L

    fun kindOf(raw: String): AlarmKind? = when (raw.lowercase().trim()) {
        "price", "fiyat" -> AlarmKind.PRICE
        "score", "skor" -> AlarmKind.SCORE
        "funding", "fund" -> AlarmKind.FUNDING
        else -> null
    }

    fun opOf(raw: String): AlarmOp? = when (raw.lowercase().trim()) {
        "gte", ">=", "ge" -> AlarmOp.GTE
        "lte", "<=", "le" -> AlarmOp.LTE
        else -> null
    }

    fun kindKey(k: AlarmKind): String = when (k) {
        AlarmKind.PRICE -> "price"
        AlarmKind.SCORE -> "score"
        AlarmKind.FUNDING -> "funding"
    }

    fun opKey(o: AlarmOp): String = when (o) {
        AlarmOp.GTE -> "gte"
        AlarmOp.LTE -> "lte"
    }

    fun draft(
        rawSymbol: String,
        kind: AlarmKind,
        op: AlarmOp,
        threshold: Double,
        label: String = "",
    ): AlarmSpec? {
        val s = Symbols.normalize(rawSymbol)
        if (s.isBlank()) return null
        if (!threshold.isFinite()) return null
        return AlarmSpec(id = 0L, symbol = s, kind = kind, op = op, threshold = threshold, enabled = true, label = label.trim())
    }

    fun reading(kind: AlarmKind, q: AlarmQuote): Double = when (kind) {
        AlarmKind.PRICE -> q.price
        AlarmKind.SCORE -> abs(q.score)
        AlarmKind.FUNDING -> abs(q.funding)
    }

    fun crossed(op: AlarmOp, value: Double, threshold: Double): Boolean = when (op) {
        AlarmOp.GTE -> value >= threshold
        AlarmOp.LTE -> value <= threshold
    }

    fun due(prevTs: Long?, now: Long, window: Long = DEDUP_MS): Boolean =
        prevTs == null || now - prevTs >= window

    fun mergeLive(quotes: List<AlarmQuote>, live: AlarmQuote?): List<AlarmQuote> {
        if (live == null || live.symbol.isBlank()) return quotes
        return quotes.filter { it.symbol != live.symbol } + live
    }

    fun check(
        alarms: List<AlarmSpec>,
        quotes: List<AlarmQuote>,
        lastFireMs: Map<Long, Long>,
        now: Long,
    ): List<AlarmHit> {
        val qmap = quotes.associateBy { it.symbol }
        val out = ArrayList<AlarmHit>(alarms.size)
        for (a in alarms) {
            if (!a.enabled || a.id <= 0L) continue
            val q = qmap[a.symbol] ?: continue
            val v = reading(a.kind, q)
            if (!v.isFinite()) continue
            if (a.kind == AlarmKind.PRICE && v <= 0.0) continue
            if (!crossed(a.op, v, a.threshold)) continue
            if (!due(lastFireMs[a.id], now)) continue
            out += AlarmHit(a, v)
        }
        return out
    }

    fun line(hit: AlarmHit): String {
        val kind = when (hit.alarm.kind) {
            AlarmKind.PRICE -> "fiyat"
            AlarmKind.SCORE -> "|skor|"
            AlarmKind.FUNDING -> "|fund|"
        }
        val op = if (hit.alarm.op == AlarmOp.GTE) "≥" else "≤"
        val tag = hit.alarm.label.ifBlank { hit.alarm.symbol }
        return "$tag  $kind $op ${fmtNum(hit.alarm.threshold)}  şimdi ${fmtNum(hit.value)}"
    }

    private fun fmtNum(v: Double): String {
        val a = abs(v)
        return when {
            a >= 1000 -> "%.2f".format(v)
            a >= 1 -> "%.4f".format(v)
            else -> "%.6f".format(v)
        }
    }
}
