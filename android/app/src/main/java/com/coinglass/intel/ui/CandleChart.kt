package com.coinglass.intel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.domain.ChartSeries
import com.coinglass.intel.domain.LiqHeat
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.domain.model.Candle
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn

@Composable
fun CandleChart(
    candles: List<Candle>,
    entry: Double,
    sl: Double,
    tp: Double,
    chartTf: String,
    onSelectTf: (String) -> Unit,
    support: Double = 0.0,
    resistance: Double = 0.0,
    bidWall: Double = 0.0,
    askWall: Double = 0.0,
    poc: Double = 0.0,
    spoof: Int = 0,
    divergeType: String = "",
    liqHeat: LiqHeat.Grid = LiqHeat.Grid(),
) {
    val heat = liqHeat
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    var window by remember { mutableIntStateOf(ChartSeries.VISIBLE_BARS) }
    val shown = ChartSeries.visible(candles, window)
    val scroll = rememberScrollState()
    LaunchedEffect(shown.size, chartTf) { scroll.scrollTo(scroll.maxValue) }
    val density = LocalDensity.current
    val barPx = with(density) { 7.dp.toPx() }
    val canvasW = with(density) { (shown.size * 7).dp }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("GRAFİK", color = scheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
            Text("${shown.size}/${candles.size}", color = scheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartSeries.TFS.forEach { tf ->
                val on = tf == chartTf
                Text(
                    tf,
                    color = if (on) scheme.onPrimary else scheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(if (on) scheme.primary else scheme.surfaceVariant)
                        .clickable { onSelectTf(tf) }
                        .padding(horizontal = Space.md, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            listOf(60, 90, 150).forEach { n ->
                Text(
                    "$n",
                    color = if (window == n) scheme.primary else scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { window = n }
                        .padding(horizontal = Space.xs),
                )
            }
        }
        Text(
            "entry ${fmtPrice(entry)}  sl ${fmtPrice(sl)}  tp ${fmtPrice(tp)}" +
                if (spoof >= 50) "  spoof-skip-wall" else "",
            color = scheme.onSurfaceVariant, fontSize = 11.sp,
            modifier = Modifier.padding(top = Space.sm, bottom = Space.sm),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp)
                .clip(RoundedCornerShape(Radii.sm))
                .background(Color(0xFF08141C))
                .pointerInput(candles.size) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom == 1f) return@detectTransformGestures
                        val next = (window / zoom).toInt().coerceIn(40, 220)
                        if (next != window) window = next
                    }
                }
                .horizontalScroll(scroll),
        ) {
            if (shown.size < 2) {
                Text("mum yok — sembol seç, 600 bar REST geliyor", color = scheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(Space.md))
            } else {
                Canvas(
                    Modifier
                        .width(canvasW)
                        .height(188.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    val volH = size.height * 0.20f
                    val candleH = size.height - volH - 4f
                    val lows = shown.minOf { it.low }
                    val highs = shown.maxOf { it.high }
                    val extra = listOf(entry, sl, tp, support, resistance, bidWall, askWall, poc).filter { it > 0 }
                    val lo = (listOf(lows) + extra).min()
                    val hi = (listOf(highs) + extra).max()
                    val span = (hi - lo).let { if (it <= 0) 1.0 else it }
                    fun y(p: Double) = (candleH * (1f - ((p - lo) / span).toFloat())).coerceIn(0f, candleH)
                    val n = shown.size
                    val slot = if (n == 0) barPx else size.width / n
                    val bodyW = (slot * 0.62f).coerceIn(1.6f, 8f)
                    val maxVol = shown.maxOf { it.volume }.let { if (it <= 0) 1.0 else it }
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    val thin = PathEffect.dashPathEffect(floatArrayOf(4f, 5f), 0f)
                    if (support > 0 && resistance > 0 && resistance > support) {
                        val top = y(resistance)
                        val bot = y(support)
                        drawRect(
                            Color(0x3300E5C3),
                            Offset(0f, top),
                            Size(size.width, (bot - top).coerceAtLeast(2f)),
                        )
                    }
                    if (poc > 0) drawLine(accent.copy(alpha = 0.55f), Offset(0f, y(poc)), Offset(size.width, y(poc)), 1.6f)
                    if (support > 0) drawLine(Bull.copy(alpha = 0.55f), Offset(0f, y(support)), Offset(size.width, y(support)), 1.4f, pathEffect = thin)
                    if (resistance > 0) drawLine(Bear.copy(alpha = 0.55f), Offset(0f, y(resistance)), Offset(size.width, y(resistance)), 1.4f, pathEffect = thin)
                    val wallDash = if (spoof >= 50) dash else null
                    val wallCol = if (spoof >= 50) Warn else accent
                    if (bidWall > 0) drawLine(wallCol.copy(alpha = 0.55f), Offset(0f, y(bidWall)), Offset(size.width, y(bidWall)), 1.4f, pathEffect = wallDash)
                    if (askWall > 0) drawLine(wallCol.copy(alpha = 0.55f), Offset(0f, y(askWall)), Offset(size.width, y(askWall)), 1.4f, pathEffect = wallDash)
                    shown.forEachIndexed { i, c ->
                        val x = slot * i + slot / 2f
                        val up = c.close >= c.open
                        val col = if (up) Bull else Bear
                        drawLine(col, Offset(x, y(c.high)), Offset(x, y(c.low)), strokeWidth = 1.4f)
                        val top = y(maxOf(c.open, c.close))
                        val bot = y(minOf(c.open, c.close))
                        val h = (bot - top).coerceAtLeast(1.2f)
                        drawRect(col, Offset(x - bodyW / 2f, top), Size(bodyW, h))
                        val vh = (volH * (c.volume / maxVol).toFloat()).coerceAtLeast(1f)
                        drawRect(col.copy(alpha = 0.45f), Offset(x - bodyW / 2f, size.height - vh), Size(bodyW, vh))
                    }
                    if (!heat.empty) {
                        val strip = size.width * 0.14f
                        val left = size.width - strip
                        val maxU = heat.maxUsd
                        for (b in heat.bins) {
                            if (b.total <= 0) continue
                            val y1 = y(b.hi)
                            val y2 = y(b.lo)
                            val top = minOf(y1, y2)
                            val hh = kotlin.math.abs(y2 - y1).coerceAtLeast(1.2f)
                            val wL = strip * 0.46f * (b.longUsd / maxU).toFloat()
                            val wS = strip * 0.46f * (b.shortUsd / maxU).toFloat()
                            if (wL > 0) drawRect(Bear.copy(alpha = 0.40f), Offset(left + strip * 0.5f - wL, top), Size(wL, hh))
                            if (wS > 0) drawRect(Bull.copy(alpha = 0.40f), Offset(left + strip * 0.5f, top), Size(wS, hh))
                        }
                    }
                    if (divergeType.isNotBlank()) {
                        val last = shown.last()
                        val x = slot * (shown.lastIndex) + slot / 2f
                        val mark = if (divergeType.contains("bear")) Bear else Bull
                        drawCircle(mark, radius = 5f, center = Offset(x, y(last.high) - 8f))
                    }
                    if (entry > 0) drawLine(accent, Offset(0f, y(entry)), Offset(size.width, y(entry)), 2f)
                    if (sl > 0) drawLine(Bear, Offset(0f, y(sl)), Offset(size.width, y(sl)), 2f, pathEffect = dash)
                    if (tp > 0) drawLine(Warn, Offset(0f, y(tp)), Offset(size.width, y(tp)), 2f, pathEffect = dash)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "pinch zoom · kaydir gecmis · VAL/VAH · kesikli=spoof" +
                if (divergeType.isNotBlank()) " · CVD $divergeType" else "",
            color = scheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}
