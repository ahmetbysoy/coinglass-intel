package com.coinglass.intel.domain

/** Pure bits the ViewModel used to inline — testable without Android. */

object StaleClock {
    fun isStale(symbol: String, lastUpdateMs: Long, nowMs: Long, staleSeconds: Int): Boolean {
        if (symbol.isBlank() || lastUpdateMs <= 0L) return false
        val lim = staleSeconds.toLong().coerceAtLeast(0L) * 1_000L
        return nowMs - lastUpdateMs > lim
    }
}

object CompareRing {
    const val LIMIT = 2

    fun toggle(cur: List<String>, symbol: String, limit: Int = LIMIT): List<String> {
        if (symbol.isBlank()) return cur
        if (symbol in cur) return cur.filter { it != symbol }
        val trimmed = if (cur.size >= limit) cur.drop(cur.size - limit + 1) else cur
        return trimmed + symbol
    }
}

object WatchCycle {
    fun pick(list: List<String>, current: String, delta: Int): String? {
        if (list.isEmpty()) return null
        val i = list.indexOf(current).coerceAtLeast(0)
        return list[(i + delta).mod(list.size)]
    }
}

object AlarmSig {
    fun of(symbol: String, price: Double, score: Double, funding: Double): String =
        "$symbol|${price}|${score}|${funding}"
}
