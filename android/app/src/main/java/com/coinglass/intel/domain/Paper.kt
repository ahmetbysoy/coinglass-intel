package com.coinglass.intel.domain

/** Paper book rules. Pure — no Room, no orders. */
object PaperEngine {
    const val DEDUP_MS = 120_000L
    const val TIMEOUT_MS = 15 * 60_000L

    data class Close(
        val px: Double,
        val win: Boolean,
        val reason: String,
    )

    fun sideOf(direction: String): String? = when {
        "BULL" in direction -> "LONG"
        "BEAR" in direction -> "SHORT"
        else -> null
    }

    fun canOpen(
        enterOk: Boolean,
        grade: String,
        symbol: String,
        lastOpenAt: Long?,
        now: Long,
        hasOpen: Boolean = false,
    ): Boolean {
        if (!enterOk) return false
        if (grade != "A" && grade != "B") return false
        if (symbol.isBlank()) return false
        if (hasOpen) return false
        if (lastOpenAt != null && now - lastOpenAt < DEDUP_MS) return false
        return true
    }

    fun checkExit(
        side: String,
        entry: Double,
        sl: Double,
        tp: Double,
        px: Double,
        openedAt: Long,
        now: Long,
    ): Close? {
        if (px <= 0.0 || entry <= 0.0) return null
        when (side) {
            "LONG" -> {
                if (sl > 0.0 && px <= sl) return Close(px, false, "sl")
                if (tp > 0.0 && px >= tp) return Close(px, true, "tp")
            }
            "SHORT" -> {
                if (sl > 0.0 && px >= sl) return Close(px, false, "sl")
                if (tp > 0.0 && px <= tp) return Close(px, true, "tp")
            }
            else -> return null
        }
        if (now - openedAt >= TIMEOUT_MS) {
            val win = if (side == "LONG") px > entry else px < entry
            return Close(px, win, "timeout")
        }
        return null
    }
}
