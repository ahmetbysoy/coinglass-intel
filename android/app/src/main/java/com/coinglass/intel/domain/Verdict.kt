package com.coinglass.intel.domain

import kotlin.math.abs

/** Single-line decision + letter grade. Pure — lockstep-friendly. */
object Verdict {
    data class Result(
        val grade: String,
        val line: String,
        val enterOk: Boolean,
        val reasons: List<String>,
        val stars: Int,
    )

    fun evaluate(
        direction: String,
        score: Double,
        coverage: Double,
        confluence: Double,
        spoof: Int,
        risk: Int,
        netRr: Double,
        why: String,
    ): Result {
        var pts = 0
        if (coverage >= 70) pts += 2 else if (coverage >= 40) pts += 1
        if (abs(confluence) >= 3) pts += 2 else if (abs(confluence) >= 1) pts += 1
        if (spoof < 30) pts += 2 else if (spoof < 50) pts += 1
        if (risk < 40) pts += 2 else if (risk < 70) pts += 1
        if (netRr >= 1.5) pts += 1
        val grade = when {
            pts >= 8 -> "A"
            pts >= 6 -> "B"
            pts >= 4 -> "C"
            else -> "D"
        }
        val stars = (pts / 2).coerceIn(0, 5)
        val reasons = mutableListOf<String>()
        val directional = "BULL" in direction || "BEAR" in direction
        if (spoof >= 50) reasons += "spoof $spoof — duvar SL olmaz"
        if (coverage < 40) reasons += "coverage ${coverage.toInt()}% — veri eksik"
        if (directional && netRr < 1.0) reasons += "netRR ${"%.2f".format(netRr)} < 1 — fee yiyor"
        if (risk >= 70) reasons += "risk $risk yüksek"
        val enterOk = reasons.isEmpty() && directional
        val side = when {
            "BULL" in direction -> "LONG"
            "BEAR" in direction -> "SHORT"
            else -> "BEKLE"
        }
        val confWord = when (grade) {
            "A" -> "yüksek güven"
            "B" -> "orta güven"
            "C" -> "düşük güven"
            else -> "zayıf"
        }
        val whyBit = why.ifBlank { "bileşen dağınık" }
        val line = if (!enterOk && reasons.isNotEmpty()) {
            "GİRME • ${reasons.first()}"
        } else if (!directional) {
            "BEKLE • yön yok • $confWord"
        } else {
            "$side • $confWord • $whyBit • spoof $spoof"
        }
        return Result(grade, line, enterOk, reasons, stars)
    }
}
