package com.coinglass.intel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.domain.ChartHit
import com.coinglass.intel.domain.ChartRange
import com.coinglass.intel.domain.ChartSeries
import com.coinglass.intel.domain.LiqHeat
import com.coinglass.intel.domain.Smc
import com.coinglass.intel.domain.Structure
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
    chartHeight: Dp = 320.dp,
    smc: Smc.Report = Smc.Report(),
    modifier: Modifier = Modifier,
) {
    val heat = liqHeat
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    var window by remember { mutableIntStateOf(ChartSeries.VISIBLE_BARS) }
    var showOb by rememberSaveable { mutableStateOf(false) }
    var showFvg by rememberSaveable { mutableStateOf(false) }
    var showSweep by rememberSaveable { mutableStateOf(false) }
    var showHeat by rememberSaveable { mutableStateOf(true) }
    var hitIdx by remember { mutableIntStateOf(-1) }
    val shown = ChartSeries.visible(candles, window)
    val localVa = remember(shown) { Structure.volumeArea(shown) }
    val scroll = rememberScrollState()
    LaunchedEffect(shown.size, chartTf) { scroll.scrollTo(scroll.maxValue) }
    LaunchedEffect(chartTf, candles.size) { hitIdx = -1 }
    val density = LocalDensity.current
    val barPx = with(density) { 7.dp.toPx() }
    val canvasW = with(density) { (shown.size * 7).dp }
    val tip = if (hitIdx >= 0) ChartHit.tip(shown, hitIdx, heat) else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.md)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .heightIn(min = maxOf(chartHeight, 280.dp))
                .background(Color(0xFF08141C))
                .pointerInput(candles.size) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom == 1f) return@detectTransformGestures
                        val next = (window / zoom).toInt().coerceIn(40, 220)
                        if (next != window) window = next
                    }
                },
        ) {
            if (shown.size < 2) {
                Text(
                    "mum yok — sembol seç, 600 bar REST geliyor",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(Space.md).align(Alignment.Center),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(scroll),
                ) {
                    Box(Modifier.width(canvasW).fillMaxSize()) {
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            val volH = size.height * 0.16f
                            val candleH = size.height - volH - 4f
                            val lows = shown.minOf { it.low }
                            val highs = shown.maxOf { it.high }
                            val locVal = localVa.first
                            val locPoc = localVa.second
                            val locVah = localVa.third
                            val extra = listOf(entry, sl, tp, locPoc)
                            val (lo, hi) = ChartRange.bounds(lows, highs, extra)
                            val span = (hi - lo).let { if (it <= 0) 1.0 else it }
                            fun y(p: Double) = (candleH * (1f - ((p - lo) / span).toFloat())).coerceIn(0f, candleH)
                            val n = shown.size
                            val slot = if (n == 0) barPx else size.width / n
                            val bodyW = (slot * 0.62f).coerceIn(1.6f, 8f)
                            val maxVol = shown.maxOf { it.volume }.let { if (it <= 0) 1.0 else it }
                            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            val thin = PathEffect.dashPathEffect(floatArrayOf(4f, 5f), 0f)
                            if (locVal > 0 && locVah > locVal) {
                                val top = y(minOf(locVah, hi))
                                val bot = y(maxOf(locVal, lo))
                                if (bot > top) {
                                    drawRect(
                                        Color(0x1400E5C3),
                                        Offset(0f, top),
                                        Size(size.width, (bot - top).coerceAtLeast(2f)),
                                    )
                                    drawLine(Color(0x6600E5C3), Offset(0f, top), Offset(size.width, top), 1.4f)
                                    drawLine(Color(0x6600E5C3), Offset(0f, bot), Offset(size.width, bot), 1.4f)
                                }
                            }
                            if (ChartRange.inView(locPoc, lo, hi)) {
                                drawLine(accent.copy(alpha = 0.55f), Offset(0f, y(locPoc)), Offset(size.width, y(locPoc)), 1.6f)
                            }
                            if (ChartRange.inView(support, lo, hi)) {
                                drawLine(Bull.copy(alpha = 0.55f), Offset(0f, y(support)), Offset(size.width, y(support)), 1.4f, pathEffect = thin)
                            }
                            if (ChartRange.inView(resistance, lo, hi)) {
                                drawLine(Bear.copy(alpha = 0.55f), Offset(0f, y(resistance)), Offset(size.width, y(resistance)), 1.4f, pathEffect = thin)
                            }
                            val wallDash = if (spoof >= 50) dash else null
                            val wallCol = if (spoof >= 50) Warn else accent
                            if (ChartRange.inView(bidWall, lo, hi)) {
                                drawLine(wallCol.copy(alpha = 0.55f), Offset(0f, y(bidWall)), Offset(size.width, y(bidWall)), 1.4f, pathEffect = wallDash)
                            }
                            if (ChartRange.inView(askWall, lo, hi)) {
                                drawLine(wallCol.copy(alpha = 0.55f), Offset(0f, y(askWall)), Offset(size.width, y(askWall)), 1.4f, pathEffect = wallDash)
                            }
                            val origin = (candles.size - shown.size).coerceAtLeast(0)
                            fun drawZone(z: Smc.Zone, col: Color) {
                                val x0i = z.startIdx - origin
                                val endAbs = z.touchIdx ?: (origin + shown.lastIndex)
                                val x1i = endAbs - origin
                                if (x1i < 0 || x0i > shown.lastIndex) return
                                val left = slot * x0i.coerceAtLeast(0)
                                val right = slot * (x1i.coerceAtMost(shown.lastIndex) + 1)
                                val top = y(z.high)
                                val bot = y(z.low)
                                val hh = kotlin.math.abs(bot - top).coerceAtLeast(2f)
                                drawRect(col, Offset(left, minOf(top, bot)), Size((right - left).coerceAtLeast(2f), hh))
                            }
                            if (showOb) smc.obs.forEach { z ->
                                drawZone(z, (if (z.side == "bull") Bull else Bear).copy(alpha = if (z.touched) 0.08f else 0.20f))
                            }
                            if (showFvg) smc.fvgs.filter { !it.touched }.forEach { z ->
                                drawZone(z, (if (z.side == "bull") Bull else Bear).copy(alpha = 0.14f))
                            }
                            if (showSweep) smc.sweeps.forEach { z ->
                                val xi = z.endIdx - origin
                                if (xi in shown.indices) {
                                    val x = slot * xi + slot / 2f
                                    drawLine(Warn, Offset(0f, y(if (z.side == "bear") z.low else z.high)), Offset(size.width, y(if (z.side == "bear") z.low else z.high)), 1.2f, pathEffect = thin)
                                    drawCircle(Warn, radius = 4f, center = Offset(x, y(if (z.side == "bear") z.high else z.low)))
                                }
                            }
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
                            if (showHeat && !heat.empty) {
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
                            if (hitIdx in shown.indices) {
                                val hx = slot * hitIdx + slot / 2f
                                val hc = shown[hitIdx]
                                drawLine(accent.copy(alpha = 0.85f), Offset(hx, 0f), Offset(hx, candleH), 1.3f)
                                drawLine(accent.copy(alpha = 0.45f), Offset(0f, y(hc.close)), Offset(size.width, y(hc.close)), 1.2f, pathEffect = thin)
                            }
                            val ticks = ChartRange.tickCount(lo, hi)
                            val last = (ticks - 1).coerceAtLeast(1)
                            val labelH = 12.sp.toPx()
                            val axisPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(230, 220, 230, 235)
                                textSize = 10.sp.toPx()
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.RIGHT
                            }
                            val bgPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(180, 8, 20, 28)
                                isAntiAlias = true
                            }
                            val nc = drawContext.canvas.nativeCanvas
                            for (i in 0 until ticks) {
                                val px = hi - (hi - lo) * i / last
                                val yy = y(px).coerceIn(labelH, candleH - 2f)
                                drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, yy), Offset(size.width, yy), 1f)
                                val label = fmtPrice(px)
                                val tw = axisPaint.measureText(label)
                                nc.drawRect(size.width - tw - 8f, yy - labelH, size.width - 2f, yy + 3f, bgPaint)
                                nc.drawText(label, size.width - 4f, yy - 2f, axisPaint)
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(shown.size) {
                                    detectTapGestures { off ->
                                        hitIdx = ChartHit.index(off.x, size.width.toFloat(), shown.size) ?: -1
                                    }
                                }
                                .pointerInput(shown.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { off ->
                                            hitIdx = ChartHit.index(off.x, size.width.toFloat(), shown.size) ?: -1
                                        },
                                        onDrag = { change, _ ->
                                            hitIdx = ChartHit.index(change.position.x, size.width.toFloat(), shown.size) ?: hitIdx
                                            change.consume()
                                        },
                                    )
                                },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .padding(horizontal = Space.sm, vertical = 6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChartSeries.TFS.forEach { tf ->
                        val on = tf == chartTf
                        Text(
                            tf,
                            color = if (on) scheme.onPrimary else Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radii.sm))
                                .background(if (on) scheme.primary else Color.White.copy(alpha = 0.12f))
                                .clickable { onSelectTf(tf) }
                                .padding(horizontal = Space.sm, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${shown.size}/${candles.size}", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    listOf(60, 90, 150).forEach { n ->
                        Text(
                            "$n",
                            color = if (window == n) scheme.primary else Color.White.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            modifier = Modifier.clickable { window = n }.padding(horizontal = 4.dp),
                        )
                    }
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OverlayChip("OB", showOb, scheme) { showOb = !showOb }
                    OverlayChip("FVG", showFvg, scheme) { showFvg = !showFvg }
                    OverlayChip("SWEEP", showSweep, scheme) { showSweep = !showSweep }
                    OverlayChip("HEAT", showHeat, scheme) { showHeat = !showHeat }
                }
                Text(
                    "e ${fmtPrice(entry)}  sl ${fmtPrice(sl)}  tp ${fmtPrice(tp)}" +
                        if (spoof >= 50) "  spoof-skip" else "",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (tip != null) {
            Text(
                tip.line,
                color = scheme.onSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = Space.sm, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun OverlayChip(label: String, on: Boolean, scheme: androidx.compose.material3.ColorScheme, click: () -> Unit) {
    Text(
        label,
        color = if (on) scheme.onPrimary else Color.White.copy(alpha = 0.75f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(if (on) scheme.primary else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = click)
            .padding(horizontal = Space.sm, vertical = 3.dp),
    )
}
