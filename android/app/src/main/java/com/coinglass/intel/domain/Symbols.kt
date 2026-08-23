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
