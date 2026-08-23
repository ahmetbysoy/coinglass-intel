package com.coinglass.intel.domain

data class SymbolInfo(
    val symbol: String,
    val base: String,
    val quote: String = "USDT",
    val binance: String,
    val bybit: String,
    val okx: String,
)

object Symbols {
    fun resolve(raw: String): SymbolInfo {
        val s = normalize(raw)
        val base = base(s)
        return info(base).copy(symbol = s, binance = s, bybit = s)
    }

    /** Exchange naming convention, not a coin list: 1000PEPE / 1MPEPE style. */
    fun leveragedForms(symbol: String): List<String> {
        val s = normalize(symbol)
        if (s.isBlank()) return emptyList()
        val b = base(s)
        val quote = when {
            s.endsWith("USDT") -> "USDT"
            s.endsWith("USDC") -> "USDC"
            s.endsWith("USD") -> "USD"
            else -> "USDT"
        }
        if (b.startsWith("1000") || b.startsWith("1000000") || b.startsWith("1M")) return listOf(s)
        return listOf(s, "1000$b$quote", "1M$b$quote", "1000000$b$quote").distinct()
    }

    fun candidates(raw: String): List<String> = leveragedForms(raw)

    fun normalize(raw: String): String {
        var s = raw.uppercase()
            .replace("-PERP", "")
            .replace("_PERP", "")
            .replace("-", "")
            .replace("/", "")
            .trim()
        if (s.isEmpty()) return ""
        if (s.endsWith("USDT") || s.endsWith("USDC")) return s
        if (s.endsWith("USD")) return s
        return s + "USDT"
    }

    fun base(raw: String): String {
        val s = normalize(raw)
        return when {
            s.endsWith("USDT") -> s.dropLast(4)
            s.endsWith("USDC") -> s.dropLast(4)
            s.endsWith("USD") -> s.dropLast(3)
            else -> s
        }
    }

    private fun info(base: String): SymbolInfo = SymbolInfo(
        symbol = base + "USDT",
        base = base,
        binance = base + "USDT",
        bybit = base + "USDT",
        okx = "$base-USDT-SWAP",
    )
}
