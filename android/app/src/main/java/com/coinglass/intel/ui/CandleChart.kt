package com.coinglass.intel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.domain.ChartHit
import com.coinglass.intel.domain.ChartRange
import com.coinglass.intel.domain.ChartSeries
import com.coinglass.intel.domain.ChartViewport
import com.coinglass.intel.domain.Ema
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
import kotlin.math.abs
import kotlin.math.roundToInt

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
    initialVisible: Int = ChartSeries.VISIBLE_BARS,
    onVisibleChange: (Int) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val state = rememberChartViewState(initialVisible)
    val measurer = rememberTextMeasurer()
    var hitIdx by remember { mutableIntStateOf(-1) }
    var hitFrac by remember { mutableFloatStateOf(-1f) }
    var flingKick by remember { mutableIntStateOf(0) }
    var flingVel by remember { mutableFloatStateOf(0f) }
    var prevTotal by remember { mutableIntStateOf(candles.size) }

    val total = candles.size
    LaunchedEffect(total) {
        state.onGrow(prevTotal, total)
        prevTotal = total
    }
    val win = ChartViewport.window(total, state.visible, state.offsetFromEnd)
    val shown = remember(candles, win.start, win.endExclusive) {
        if (win.endExclusive > win.start) candles.subList(win.start, win.endExclusive) else emptyList()
    }
    val localVa = remember(shown) { Structure.volumeArea(shown) }
    val emaFast = remember(candles) { Ema.of(candles.map { it.close }, 20) }
    val emaSlow = remember(candles) { Ema.of(candles.map { it.close }, 50) }
    LaunchedEffect(chartTf) {
        hitIdx = -1
        hitFrac = -1f
        state.jumpToLive()
    }
    LaunchedEffect(state.visible) { onVisibleChange(state.visible) }
    LaunchedEffect(flingKick, total) {
        if (flingKick == 0) return@LaunchedEffect
        var v = flingVel
        while (kotlin.math.abs(v) > 0.2f) {
            delay(16)
            val step = v.roundToInt()
            if (step != 0) state.pan(step, total)
            v *= 0.88f
        }
    }
    val tip = if (hitIdx in shown.indices) ChartHit.tip(shown, hitIdx, liqHeat) else null
    val lastClose = candles.lastOrNull()?.close ?: 0.0

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
                .heightIn(min = maxOf(chartHeight, 280.dp))
                .weight(1f, fill = true)
                .background(Color(0xFF08141C)),
        ) {
            if (shown.size < 2) {
                Text(
                    "mum yok — sembol seç, 600 bar REST geliyor",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center).padding(Space.md),
                )
            } else {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .pointerInput(total, state.visible) {
                            detectTransformGestures { centroid, panDelta, zoom, _ ->
                                val fx = (centroid.x / size.width).coerceIn(0f, 1f)
                                if (zoom != 1f) state.zoom(zoom, fx, total)
                                if (fx > 0.88f && panDelta.y != 0f) {
                                    state.nudgePriceZoom(panDelta.y / size.height)
                                } else if (panDelta.x != 0f) {
                                    val slots = ChartViewport.slotCount(state.visible, state.following).coerceAtLeast(1)
                                    val slot = size.width.toFloat() / slots
                                    val bars = (panDelta.x / slot).roundToInt()
                                    if (bars != 0) {
                                        state.pan(bars, total)
                                        flingVel = bars.toFloat()
                                        flingKick++
                                    }
                                }
                            }
                        }
                        .pointerInput(shown.size) {
                            detectTapGestures(
                                onTap = { off ->
                                    val slots = ChartViewport.slotCount(shown.size, state.following)
                                    hitIdx = ChartHit.index(off.x, size.width.toFloat(), slots) ?: -1
                                    if (hitIdx >= shown.size) hitIdx = shown.lastIndex
                                    hitFrac = (off.x / size.width).coerceIn(0f, 1f)
                                },
                                onDoubleTap = {
                                    state.reset()
                                    state.priceZoom = 1f
                                    hitIdx = -1
                                    hitFrac = -1f
                                },
                            )
                        }
                        .pointerInput(shown.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { off ->
                                    val slots = ChartViewport.slotCount(shown.size, state.following)
                                    hitIdx = ChartHit.index(off.x, size.width.toFloat(), slots) ?: -1
                                    if (hitIdx >= shown.size) hitIdx = shown.lastIndex
                                    hitFrac = (off.x / size.width).coerceIn(0f, 1f)
                                },
                                onDrag = { change, _ ->
                                    val slots = ChartViewport.slotCount(shown.size, state.following)
                                    hitIdx = ChartHit.index(change.position.x, size.width.toFloat(), slots) ?: hitIdx
                                    if (hitIdx >= shown.size) hitIdx = shown.lastIndex
                                    hitFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                                    change.consume()
                                },
                            )
                        },
                ) {
                    drawChart(
                        shown = shown,
                        startIdx = win.start,
                        entry = entry,
                        sl = sl,
                        tp = tp,
                        support = support,
                        resistance = resistance,
                        bidWall = bidWall,
                        askWall = askWall,
                        spoof = spoof,
                        divergeType = divergeType,
                        heat = liqHeat,
                        smc = smc,
                        localVa = localVa,
                        emaFast = emaFast,
                        emaSlow = emaSlow,
                        lastClose = lastClose,
                        hitIdx = hitIdx,
                        state = state,
                        accent = accent,
                        measurer = measurer,
                    )
                }
            }
            ChartHeader(
                state = state,
                chartTf = chartTf,
                onSelectTf = onSelectTf,
                shownCount = shown.size,
                totalCount = total,
                entry = entry,
                sl = sl,
                tp = tp,
                spoof = spoof,
                scheme = scheme,
                modifier = Modifier.align(Alignment.TopStart),
            )
            if (tip != null && hitFrac >= 0f) {
                Text(
                    tip.line,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 78.dp)
                        .padding(start = (8 + hitFrac * 40).dp)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(Color(0xCC08141C))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (!state.following) {
                Text(
                    "CANLI",
                    color = scheme.onPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Space.sm)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(scheme.primary.copy(alpha = 0.85f))
                        .clickable { state.jumpToLive() }
                        .padding(horizontal = Space.sm, vertical = 4.dp),
                )
            }
        }
    }
}

private fun DrawScope.drawChart(
    shown: List<Candle>,
    startIdx: Int,
    entry: Double,
    sl: Double,
    tp: Double,
    support: Double,
    resistance: Double,
    bidWall: Double,
    askWall: Double,
    spoof: Int,
    divergeType: String,
    heat: LiqHeat.Grid,
    smc: Smc.Report,
    localVa: Triple<Double, Double, Double>,
    emaFast: List<Double>,
    emaSlow: List<Double>,
    lastClose: Double,
    hitIdx: Int,
    state: ChartViewState,
    accent: Color,
    measurer: TextMeasurer,
) {
    val axisStyle = TextStyle(color = Color(0xFFDCE6EB), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    val showVol = state.has(ChartViewState.FLAG_VOL)
    val volH = if (showVol) size.height * 0.14f else 0f
    val timeAxisH = 14.sp.toPx()
    val candleH = size.height - volH - timeAxisH - 4f
    val lo0 = shown.minOf { it.low }
    val hi0 = shown.maxOf { it.high }
    val (locVal, locPoc, locVah) = localVa
    val raw = ChartRange.bounds(lo0, hi0, listOf(entry, sl, tp, locPoc))
    val mid = (raw.first + raw.second) / 2.0
    val half = ((raw.second - raw.first) / 2.0) * state.priceZoom.toDouble()
    val lo = mid - half
    val hi = mid + half
    val span = (hi - lo).let { if (it <= 0) 1.0 else it }
    fun y(p: Double) = (candleH * (1f - ((p - lo) / span).toFloat())).coerceIn(0f, candleH)
    val n = shown.size
    val slot = size.width / n
    val bodyW = (slot * 0.62f).coerceIn(1.6f, 12f)
    val maxVol = shown.maxOf { it.volume }.let { if (it <= 0) 1.0 else it }
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
    val thin = PathEffect.dashPathEffect(floatArrayOf(4f, 5f), 0f)

    if (locVal > 0 && locVah > locVal) {
        val top = y(minOf(locVah, hi))
        val bot = y(maxOf(locVal, lo))
        if (bot > top) {
            drawRect(Color(0x1400E5C3), Offset(0f, top), Size(size.width, (bot - top).coerceAtLeast(2f)))
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
    val wallCol = if (spoof >= 50) Warn else accent
    val wallDash = if (spoof >= 50) dash else null
    if (ChartRange.inView(bidWall, lo, hi)) {
        drawLine(wallCol.copy(alpha = 0.55f), Offset(0f, y(bidWall)), Offset(size.width, y(bidWall)), 1.4f, pathEffect = wallDash)
    }
    if (ChartRange.inView(askWall, lo, hi)) {
        drawLine(wallCol.copy(alpha = 0.55f), Offset(0f, y(askWall)), Offset(size.width, y(askWall)), 1.4f, pathEffect = wallDash)
    }

    fun drawZone(z: Smc.Zone, col: Color) {
        val x0i = z.startIdx - startIdx
        val x1i = (z.touchIdx ?: (startIdx + shown.lastIndex)) - startIdx
        if (x1i < 0 || x0i > shown.lastIndex) return
        val left = slot * x0i.coerceAtLeast(0)
        val right = slot * (x1i.coerceAtMost(shown.lastIndex) + 1)
        val top = y(z.high)
        val bot = y(z.low)
        drawRect(col, Offset(left, minOf(top, bot)), Size((right - left).coerceAtLeast(2f), abs(bot - top).coerceAtLeast(2f)))
    }
    if (state.has(ChartViewState.FLAG_OB)) {
        smc.obs.forEach { z ->
            drawZone(z, (if (z.side == "bull") Bull else Bear).copy(alpha = if (z.touched) 0.08f else 0.20f))
        }
    }
    if (state.has(ChartViewState.FLAG_FVG)) {
        smc.fvgs.filter { !it.touched }.forEach { z ->
            drawZone(z, (if (z.side == "bull") Bull else Bear).copy(alpha = 0.14f))
        }
    }
    if (state.has(ChartViewState.FLAG_SWEEP)) {
        smc.sweeps.forEach { z ->
            val xi = z.endIdx - startIdx
            if (xi in shown.indices) {
                val x = slot * xi + slot / 2f
                val lvl = if (z.side == "bear") z.low else z.high
                drawLine(Warn, Offset(0f, y(lvl)), Offset(size.width, y(lvl)), 1.2f, pathEffect = thin)
                drawCircle(Warn, radius = 4f, center = Offset(x, y(if (z.side == "bear") z.high else z.low)))
            }
        }
    }

    shown.forEachIndexed { i, c ->
        val x = slot * i + slot / 2f
        val up = c.close >= c.open
        val col = if (up) Bull else Bear
        drawLine(col, Offset(x, y(c.high)), Offset(x, y(c.low)), 1.4f)
        val top = y(maxOf(c.open, c.close))
        val bot = y(minOf(c.open, c.close))
        drawRect(col, Offset(x - bodyW / 2f, top), Size(bodyW, (bot - top).coerceAtLeast(1.2f)))
        if (showVol) {
            val vh = (volH * (c.volume / maxVol).toFloat()).coerceAtLeast(1f)
            val vTop = size.height - timeAxisH - vh
            drawRect(
                Brush.verticalGradient(
                    listOf(col.copy(alpha = 0.55f), col.copy(alpha = 0.15f)),
                    startY = vTop,
                    endY = size.height - timeAxisH,
                ),
                Offset(x - bodyW / 2f, vTop),
                Size(bodyW, vh),
            )
        }
    }

    if (state.has(ChartViewState.FLAG_EMA)) {
        fun drawEma(series: List<Double>, col: Color) {
            if (series.isEmpty()) return
            val path = Path()
            var started = false
            shown.forEachIndexed { i, _ ->
                val v = series.getOrNull(startIdx + i) ?: return@forEachIndexed
                if (v.isNaN()) return@forEachIndexed
                val px = slot * i + slot / 2f
                val py = y(v)
                if (!started) {
                    path.moveTo(px, py)
                    started = true
                } else {
                    path.lineTo(px, py)
                }
            }
            if (started) drawPath(path, col, style = Stroke(width = 1.6f))
        }
        drawEma(emaFast, Color(0xFF64B5F6))
        drawEma(emaSlow, Color(0xFFFFB74D))
    }

    if (state.has(ChartViewState.FLAG_HEAT) && !heat.empty) {
        val strip = size.width * 0.14f
        val left = size.width - strip
        for (b in heat.bins) {
            if (b.total <= 0) continue
            val top = minOf(y(b.hi), y(b.lo))
            val hh = abs(y(b.lo) - y(b.hi)).coerceAtLeast(1.2f)
            val wL = strip * 0.46f * (b.longUsd / heat.maxUsd).toFloat()
            val wS = strip * 0.46f * (b.shortUsd / heat.maxUsd).toFloat()
            if (wL > 0) drawRect(Bear.copy(alpha = 0.40f), Offset(left + strip * 0.5f - wL, top), Size(wL, hh))
            if (wS > 0) drawRect(Bull.copy(alpha = 0.40f), Offset(left + strip * 0.5f, top), Size(wS, hh))
        }
    }

    if (divergeType.isNotBlank()) {
        val x = slot * shown.lastIndex + slot / 2f
        drawCircle(if (divergeType.contains("bear")) Bear else Bull, 5f, Offset(x, y(shown.last().high) - 8f))
    }
    if (entry > 0) drawLine(accent, Offset(0f, y(entry)), Offset(size.width, y(entry)), 2f)
    if (sl > 0) drawLine(Bear, Offset(0f, y(sl)), Offset(size.width, y(sl)), 2f, pathEffect = dash)
    if (tp > 0) drawLine(Warn, Offset(0f, y(tp)), Offset(size.width, y(tp)), 2f, pathEffect = dash)

    if (state.following && ChartRange.inView(lastClose, lo, hi) && shown.isNotEmpty()) {
        val ly = y(lastClose)
        val up = shown.last().close >= shown.last().open
        val badgeCol = if (up) Bull else Bear
        drawLine(badgeCol.copy(alpha = 0.7f), Offset(0f, ly), Offset(size.width, ly), 1f, pathEffect = thin)
        val layout = measurer.measure(fmtPrice(lastClose), axisStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold))
        val bw = layout.size.width + 10f
        val bh = layout.size.height + 4f
        drawRect(badgeCol, Offset(size.width - bw, ly - bh / 2f), Size(bw, bh))
        drawText(layout, topLeft = Offset(size.width - bw + 5f, ly - bh / 2f + 2f))
    }

    if (hitIdx in shown.indices) {
        val hx = slot * hitIdx + slot / 2f
        val hc = shown[hitIdx]
        drawLine(accent.copy(alpha = 0.85f), Offset(hx, 0f), Offset(hx, candleH), 1.3f)
        val cy = y(hc.close)
        drawLine(accent.copy(alpha = 0.45f), Offset(0f, cy), Offset(size.width, cy), 1.2f, pathEffect = thin)
        val layout = measurer.measure(fmtPrice(hc.close), axisStyle)
        drawRect(
            Color(0xB408141C),
            Offset(0f, cy - layout.size.height - 2f),
            Size(layout.size.width + 8f, layout.size.height + 4f),
        )
        drawText(layout, topLeft = Offset(4f, cy - layout.size.height))
    }

    val ticks = ChartRange.tickCount(lo, hi)
    val lastT = (ticks - 1).coerceAtLeast(1)
    for (i in 0 until ticks) {
        val px = hi - (hi - lo) * i / lastT
        val yy = y(px)
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, yy), Offset(size.width, yy), 1f)
        val layout = measurer.measure(fmtPrice(px), axisStyle)
        val tx = size.width - layout.size.width - 4f
        drawRect(
            Color(0xB408141C),
            Offset(tx - 4f, yy - layout.size.height - 2f),
            Size(layout.size.width + 8f, layout.size.height + 4f),
        )
        drawText(layout, topLeft = Offset(tx, yy - layout.size.height))
    }

    val step = (n / 5).coerceAtLeast(1)
    var i = 0
    while (i < n) {
        val label = ChartHit.formatTime(shown[i].openTime)
        val layout = measurer.measure(label, axisStyle.copy(color = Color(0x99DCE6EB)))
        val x = (slot * i + slot / 2f - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(x, size.height - timeAxisH))
        i += step
    }
}

@Composable
private fun ChartHeader(
    state: ChartViewState,
    chartTf: String,
    onSelectTf: (String) -> Unit,
    shownCount: Int,
    totalCount: Int,
    entry: Double,
    sl: Double,
    tp: Double,
    spoof: Int,
    scheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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
            Text("$shownCount/$totalCount", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OverlayChip("OB", state.has(ChartViewState.FLAG_OB), scheme) { state.toggle(ChartViewState.FLAG_OB) }
            OverlayChip("FVG", state.has(ChartViewState.FLAG_FVG), scheme) { state.toggle(ChartViewState.FLAG_FVG) }
            OverlayChip("SWEEP", state.has(ChartViewState.FLAG_SWEEP), scheme) { state.toggle(ChartViewState.FLAG_SWEEP) }
            OverlayChip("HEAT", state.has(ChartViewState.FLAG_HEAT), scheme) { state.toggle(ChartViewState.FLAG_HEAT) }
            OverlayChip("EMA", state.has(ChartViewState.FLAG_EMA), scheme) { state.toggle(ChartViewState.FLAG_EMA) }
            OverlayChip("VOL", state.has(ChartViewState.FLAG_VOL), scheme) { state.toggle(ChartViewState.FLAG_VOL) }
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

@Composable
private fun OverlayChip(label: String, on: Boolean, scheme: ColorScheme, click: () -> Unit) {
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
