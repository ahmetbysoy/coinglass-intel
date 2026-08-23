package com.coinglass.intel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.data.db.OutcomeEntity
import com.coinglass.intel.domain.DailyRisk
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class Horizon { M5, M15, H1 }

@Composable
fun PerformanceScreen(rows: List<OutcomeEntity>) {
    val scheme = MaterialTheme.colorScheme
    var hz by remember { mutableStateOf(Horizon.M15) }
    val settled = rows.filter {
        when (hz) {
            Horizon.M5 -> it.settled5
            Horizon.M15 -> it.settled15
            Horizon.H1 -> it.settled1h
        }
    }
    fun winOf(o: OutcomeEntity) = when (hz) {
        Horizon.M5 -> o.win5
        Horizon.M15 -> o.win15
        Horizon.H1 -> o.win1h
    }
    fun pxOf(o: OutcomeEntity) = when (hz) {
        Horizon.M5 -> o.px5
        Horizon.M15 -> o.px15
        Horizon.H1 -> o.px1h
    }
    val wins = settled.count { winOf(it) == true }
    val wr = if (settled.isEmpty()) 0.0 else wins.toDouble() / settled.size
    val rets = settled.mapNotNull { o ->
        val px = pxOf(o) ?: return@mapNotNull null
        if (o.price == 0.0) null else {
            val raw = (px - o.price) / o.price * 100.0
            val side = if ("BEAR" in o.direction) -1.0 else 1.0
            if ("BULL" !in o.direction && "BEAR" !in o.direction) abs(raw) * 0.0 else raw * side
        }
    }
    val eq = rets.runningFold(0.0) { acc, v -> acc + v }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = Space.lg),
    ) {
        Text("İSABET", color = scheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 13.sp)
        val day = DailyRisk.of(rows)
        Text(
            day.line,
            color = if (day.hot) Bear else scheme.onSurfaceVariant,
            fontWeight = if (day.hot) FontWeight.Black else FontWeight.Normal,
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(vertical = Space.sm)) {
            Horizon.entries.forEach { h ->
                val on = h == hz
                Text(
                    when (h) {
                        Horizon.M5 -> "5m"
                        Horizon.M15 -> "15m"
                        Horizon.H1 -> "1h"
                    },
                    color = if (on) scheme.onPrimary else scheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(if (on) scheme.primary else scheme.surfaceVariant)
                        .clickable { hz = h }
                        .padding(horizontal = Space.md, vertical = 6.dp),
                )
            }
        }
        val winsR = settled.filter { winOf(it) == true }.mapNotNull { o ->
            pxOf(o)?.let { (it - o.price) / o.price * 100 }
        }
        val lossR = settled.filter { winOf(it) == false }.mapNotNull { o ->
            pxOf(o)?.let { abs((it - o.price) / o.price * 100) }
        }
        val avgW = if (winsR.isEmpty()) 0.0 else winsR.average()
        val avgL = if (lossR.isEmpty()) 0.0 else lossR.average()
        val exp = wr * avgW - (1 - wr) * avgL
        Text(
            if (settled.isEmpty()) "henuz settle yok — seçili ufuk dolunca gelir"
            else "WR %${(wr * 100).toInt()}  ($wins/${settled.size})  exp ${"%+.2f".format(exp)}%  R ${if (avgL == 0.0) "—" else "%.2f".format(avgW / avgL)}",
            color = scheme.onSurfaceVariant, fontSize = 12.sp,
        )
        if (eq.size >= 2) {
            Spacer(Modifier.height(Space.sm))
            val line = if (eq.last() >= 0) Bull else Bear
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(scheme.surface)
                    .padding(Space.sm),
            ) {
                val lo = eq.min()
                val hi = eq.max()
                val span = (hi - lo).let { if (it == 0.0) 1.0 else it }
                val path = Path()
                eq.forEachIndexed { i, v ->
                    val x = size.width * i / (eq.size - 1).toFloat()
                    val y = size.height * (1f - ((v - lo) / span).toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val zeroY = size.height * (1f - ((0.0 - lo) / span).toFloat())
                drawLine(Warn.copy(alpha = 0.35f), Offset(0f, zeroY), Offset(size.width, zeroY), 1f)
                drawPath(path, line, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
            Text("equity (kümülatif yönlü %)", color = scheme.onSurfaceVariant, fontSize = 10.sp)
        }
        Attribution(settled, ::winOf)
        Spacer(Modifier.height(10.dp))
        LazyColumn {
            items(rows.take(80), key = { it.id }) { o ->
                val mark = when {
                    winOf(o) == true -> "OK" to Bull
                    winOf(o) == false -> "X" to Bear
                    else -> "…" to scheme.onSurfaceVariant
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(mark.first, color = mark.second, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = Space.sm))
                    Column(Modifier.weight(1f)) {
                        Text("${o.symbol}  ${o.direction}  ${"%+.1f".format(o.score)}", color = scheme.primary, fontSize = 13.sp)
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(o.ts)) +
                                "  px ${"%.4f".format(o.price)}" +
                                (pxOf(o)?.let { " → ${"%.4f".format(it)}" } ?: ""),
                            color = scheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Attribution(settled: List<OutcomeEntity>, winOf: (OutcomeEntity) -> Boolean?) {
    if (settled.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val keys = listOf("OB" to { o: OutcomeEntity -> o.ob }, "TF" to { o: OutcomeEntity -> o.tf }, "OI" to { o: OutcomeEntity -> o.oi }, "FUND" to { o: OutcomeEntity -> o.funding }, "LIQ" to { o: OutcomeEntity -> o.liq }, "VOL" to { o: OutcomeEntity -> o.vol }, "MOM" to { o: OutcomeEntity -> o.mom })
    Text("BİLEŞEN", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = Space.sm))
    keys.forEach { (name, get) ->
        val xs = settled.filter { abs(get(it)) >= 1.0 }
        if (xs.isEmpty()) return@forEach
        val wr = xs.count { winOf(it) == true }.toDouble() / xs.size
        Text(
            "$name  n=${xs.size}  wr %${(wr * 100).toInt()}",
            color = if (wr >= 0.5) Bull else Bear,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
