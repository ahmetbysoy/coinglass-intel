package com.coinglass.intel.domain

import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

enum class ScanSort(val label: String) {
    ABS_SCORE("skor"),
    RISK("risk"),
    SPOOF("spoof"),
    NET_RR("RR"),
    VOL("hacim"),
}

enum class RadarPane { DISCOVERY, WATCHLIST }

data class RadarRow(
    val symbol: String,
    val price: Double,
    val score: Double,
    val direction: String,
    val coverage: Double,
    val updatedAt: Long,
    val risk: Int?,
    val spoof: Int,
    val netRr: Double,
    val vol24: Double,
    val grade: String,
    val candles: String,
    val discovery: Boolean,
)

data class RadarQuery(
    val text: String = "",
    val maxRisk: Int = 100,
    val maxSpoof: Int = 100,
    val minRr: Double = 0.0,
    val sort: ScanSort = ScanSort.ABS_SCORE,
    val sortDesc: Boolean = true,
)

object Radar {
    const val HOT_ABS_SCORE = 20.0
    const val HOT_SPOOF = 50
    const val HOT_COVERAGE = 40.0
    const val HOT_ABS_RR = 1.0

    fun rank(rows: List<RadarRow>, q: RadarQuery): List<RadarRow> {
        val needle = q.text.trim().uppercase(Locale.US)
        val filtered = rows.filter { r ->
            (r.risk == null || r.risk <= q.maxRisk) &&
                r.spoof <= q.maxSpoof &&
                abs(r.netRr) >= q.minRr &&
                (needle.isBlank() || needle in r.symbol.uppercase(Locale.US))
        }
        val base = when (q.sort) {
            ScanSort.ABS_SCORE -> filtered.sortedBy { abs(it.score) }
            ScanSort.RISK -> filtered.sortedBy { it.risk ?: Int.MIN_VALUE }
            ScanSort.SPOOF -> filtered.sortedBy { it.spoof }
            ScanSort.NET_RR -> filtered.sortedBy { abs(it.netRr) }
            ScanSort.VOL -> filtered.sortedBy { it.vol24 }
        }
        return if (q.sortDesc) base.asReversed() else base
    }

    fun hot(rows: List<RadarRow>, limit: Int = 5): List<RadarRow> =
        rows.filter {
            abs(it.score) >= HOT_ABS_SCORE &&
                it.spoof < HOT_SPOOF &&
                it.coverage >= HOT_COVERAGE &&
                abs(it.netRr) >= HOT_ABS_RR
        }.take(limit)

    fun cycleMinRr(cur: Double): Double = when {
        cur < 1.0 -> 1.0
        cur < 2.0 -> 2.0
        else -> 0.0
    }

    fun nextSort(cur: ScanSort): ScanSort {
        val all = ScanSort.entries
        return all[(cur.ordinal + 1) % all.size]
    }

    fun parseCloses(raw: String): List<Double> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        return runCatching {
            val root = Json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
            root.mapNotNull { row ->
                row.jsonArray.getOrNull(4)?.jsonPrimitive?.doubleOrNull
            }
        }.getOrDefault(emptyList())
    }

    fun fmtSigned(v: Double, digits: Int = 1): String =
        String.format(Locale.US, "%+.${digits}f", v)

    fun fmtFixed(v: Double, digits: Int = 2): String =
        String.format(Locale.US, "%.${digits}f", v)
}
