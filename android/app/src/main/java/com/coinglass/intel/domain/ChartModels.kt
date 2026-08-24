package com.coinglass.intel.domain

import com.coinglass.intel.domain.model.Candle
import com.coinglass.intel.domain.model.V4Report

enum class Divergence {
    NONE, BULL, BEAR;

    companion object {
        fun from(raw: String): Divergence {
            val s = raw.lowercase()
            return when {
                s.contains("bear") -> BEAR
                s.contains("bull") -> BULL
                else -> NONE
            }
        }
    }
}

enum class Overlay { OB, FVG, SWEEP, HEAT, EMA, VOL }

fun Overlay.bit(): Int = 1 shl ordinal

fun Set<Overlay>.pack(): Int = fold(0) { acc, o -> acc or o.bit() }

fun overlaySet(flags: Int): Set<Overlay> =
    Overlay.entries.filter { flags and it.bit() != 0 }.toSet()

val DEFAULT_OVERLAYS: Set<Overlay> = setOf(Overlay.HEAT, Overlay.VOL)

data class ChartLevels(
    val entry: Double = 0.0,
    val sl: Double = 0.0,
    val tp: Double = 0.0,
    val support: Double = 0.0,
    val resistance: Double = 0.0,
    val bidWall: Double = 0.0,
    val askWall: Double = 0.0,
    val poc: Double = 0.0,
) {
    companion object {
        val EMPTY = ChartLevels()
    }
}

data class ChartSignals(
    val spoofScore: Int = 0,
    val divergence: Divergence = Divergence.NONE,
    val grade: String = "",
    val verdict: String = "",
) {
    val spoofSkip: Boolean get() = spoofScore >= SPOOF_THRESHOLD

    companion object {
        const val SPOOF_THRESHOLD = 50
        val EMPTY = ChartSignals()
    }
}

/** Null report → empty levels. Chart skips 0.0 sentinels (entry/sl/tp > 0). */
fun V4Report?.toChartLevels(): ChartLevels {
    if (this == null) return ChartLevels.EMPTY
    return ChartLevels(
        entry = price,
        sl = sl,
        tp = tp,
        support = support,
        resistance = resistance,
        bidWall = bidWall,
        askWall = askWall,
        poc = poc,
    )
}

fun V4Report?.toChartSignals(): ChartSignals {
    if (this == null) return ChartSignals.EMPTY
    return ChartSignals(
        spoofScore = spoof,
        divergence = Divergence.from(divergeType),
        grade = grade,
        verdict = verdict,
    )
}

data class ChartData(
    val candles: List<Candle>,
    val liqHeat: LiqHeat.Grid = LiqHeat.Grid(),
    val smc: Smc.Report = Smc.EMPTY,
)

sealed interface ChartContent {
    data object Loading : ChartContent
    data class Error(val message: String) : ChartContent
    data class Ready(val data: ChartData) : ChartContent

    companion object {
        fun of(
            candles: List<Candle>,
            loading: Boolean,
            restErrors: List<String>,
            liqHeat: LiqHeat.Grid,
            smc: Smc.Report,
            errorText: String,
        ): ChartContent {
            val snap = if (candles.isEmpty()) emptyList() else candles.toList()
            if (snap.size >= 2) return Ready(ChartData(snap, liqHeat, smc))
            if (restErrors.isNotEmpty() && !loading) return Error(errorText)
            return Loading
        }
    }
}
