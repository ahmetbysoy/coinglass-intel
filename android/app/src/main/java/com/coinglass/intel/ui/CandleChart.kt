package com.coinglass.intel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.domain.ChartGesture
import com.coinglass.intel.domain.ChartHit
import com.coinglass.intel.domain.ChartLayout
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private enum class ChartTouch { UNDECIDED, PAN, PINCH, PRICE, CROSS }

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
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val priceGutterPx = with(density) { 62.dp.toPx() }
    val heatGutterPx = with(density) { 24.dp.toPx() }
    val timeHpx = with(density) { 13.sp.toPx() }
    val slop = viewConfig.touchSlop
    val longPressMs = viewConfig.longPressTimeoutMillis
    var hitIdx by remember { mutableIntStateOf(-1) }
    var flingKick by remember { mutableIntStateOf(0) }
    var flingVel by remember { mutableFloatStateOf(0f) }
    var prevTotal by remember { mutableIntStateOf(candles.size) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    val totalState = remember { mutableIntStateOf(candles.size) }
    totalState.intValue = candles.size

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
        state.jumpToLive()
    }
    LaunchedEffect(state.visible) { onVisibleChange(state.visible) }
    LaunchedEffect(flingKick) {
        val start = flingVel
        if (ChartGesture.flingDone(start)) return@LaunchedEffect
        var v = start
        while (!ChartGesture.flingDone(v)) {
            delay(16)
            state.panRemain += v
            val b = state.panRemain.toInt()
            if (b != 0) {
                state.panRemain -= b
                state.pan(b, totalState.intValue)
            }
            v = ChartGesture.decay(v)
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
                        .pointerInput(Unit) {
                            handleChartTouches(
                                state = state,
                                total = { totalState.intValue },
                                slop = slop,
                                longPressMs = longPressMs,
                                priceGutter = priceGutterPx,
                                heatGutter = heatGutterPx,
                                timeH = timeHpx,
                                onCancelFling = {
                                    flingVel = 0f
                                    flingKick++
                                },
                                onFling = { vx, slot ->
                                    val bars = ChartGesture.flingBarsPerFrame(vx, slot)
                                    if (!ChartGesture.flingDone(bars)) {
                                        flingVel = bars
                                        flingKick++
                                    }
                                },
                                onTap = { x, _ ->
                                    val tot = totalState.intValue
                                    val geo = geoOf(state, tot, priceGutterPx, heatGutterPx, timeHpx)
                                    val last = (ChartViewport.window(tot, state.visible, state.offsetFromEnd).size - 1).coerceAtLeast(0)
                                    hitIdx = geo.candleIndex(x, last) ?: -1
                                },
                                onDoubleTap = {
                                    state.reset()
                                    hitIdx = -1
                                },
                                onCrosshair = { x, _ ->
                                    val tot = totalState.intValue
                                    val geo = geoOf(state, tot, priceGutterPx, heatGutterPx, timeHpx)
                                    val last = (ChartViewport.window(tot, state.visible, state.offsetFromEnd).size - 1).coerceAtLeast(0)
                                    hitIdx = geo.candleIndex(x, last) ?: -1
                                },
                                lastTapUptime = { lastTapAt },
                                setLastTap = { lastTapAt = it },
                            )
                        },
                ) {
                    val geo = ChartLayout.geo(
                        width = size.width,
                        height = size.height,
                        shown = shown.size,
                        following = state.following,
                        showVol = state.has(ChartViewState.FLAG_VOL),
                        showHeat = state.has(ChartViewState.FLAG_HEAT),
                        priceGutter = priceGutterPx,
                        heatGutter = heatGutterPx,
                        timeH = timeHpx,
                        shiftBars = state.panRemain,
                    )
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
                        geo = geo,
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
            if (tip != null) {
                Text(
                    tip.line,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 78.dp)
                        .padding(horizontal = Space.sm)
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
                        .padding(end = 70.dp, bottom = Space.sm)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(scheme.primary.copy(alpha = 0.85f))
                        .clickable { state.jumpToLive() }
                        .padding(horizontal = Space.sm, vertical = 4.dp),
                )
            }
        }
    }
}

private fun PointerInputScope.geoOf(
    state: ChartViewState,
    total: Int,
    priceGutter: Float,
    heatGutter: Float,
    timeH: Float,
): ChartLayout.Geo {
    val win = ChartViewport.window(total, state.visible, state.offsetFromEnd)
    return ChartLayout.geo(
        width = size.width.toFloat(),
        height = size.height.toFloat(),
        shown = win.size.coerceAtLeast(1),
        following = state.following,
        showVol = state.has(ChartViewState.FLAG_VOL),
        showHeat = state.has(ChartViewState.FLAG_HEAT),
        priceGutter = priceGutter,
        heatGutter = heatGutter,
        timeH = timeH,
        shiftBars = state.panRemain,
    )
}

private suspend fun PointerInputScope.handleChartTouches(
    state: ChartViewState,
    total: () -> Int,
    slop: Float,
    longPressMs: Long,
    priceGutter: Float,
    heatGutter: Float,
    timeH: Float,
    onCancelFling: () -> Unit,
    onFling: (vx: Float, slot: Float) -> Unit,
    onTap: (x: Float, y: Float) -> Unit,
    onDoubleTap: () -> Unit,
    onCrosshair: (x: Float, y: Float) -> Unit,
    lastTapUptime: () -> Long,
    setLastTap: (Long) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onCancelFling()
        state.clearZoomAcc()
        val vt = VelocityTracker()
        vt.addPosition(down.uptimeMillis, down.position)
        val start = down.position
        var mode = ChartTouch.UNDECIDED
        var last = start
        var pending: PointerEvent? = null
        var wait = longPressMs.coerceAtLeast(1L)

        fun geo() = geoOf(state, total(), priceGutter, heatGutter, timeH)

        fun consume(changes: List<PointerInputChange>) {
            changes.forEach { if (it.pressed || it.positionChanged()) it.consume() }
        }

        fun apply(event: PointerEvent) {
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) return
            if (pressed.size >= 2) {
                mode = ChartTouch.PINCH
                val z = event.calculateZoom()
                val c = event.calculateCentroid()
                val g = geo()
                val focus = if (g.plotWidth <= 0f) 0.5f else ((c.x - g.plotLeft) / g.plotWidth).coerceIn(0f, 1f)
                if (z != 1f) state.zoom(z, focus, total())
                consume(event.changes)
                last = c
                return
            }
            if (mode == ChartTouch.PINCH) {
                consume(event.changes)
                return
            }
            val p = pressed.first()
            vt.addPosition(p.uptimeMillis, p.position)
            val dx = p.position.x - last.x
            val dy = p.position.y - last.y
            when (mode) {
                ChartTouch.PAN -> state.panByPixels(dx, geo().slot, total())
                ChartTouch.PRICE -> state.nudgePriceZoom(dy / size.height.coerceAtLeast(1).toFloat())
                ChartTouch.CROSS -> onCrosshair(p.position.x, p.position.y)
                else -> Unit
            }
            consume(event.changes)
            last = p.position
        }

        while (mode == ChartTouch.UNDECIDED) {
            val event = withTimeoutOrNull(wait) { awaitPointerEvent() }
            if (event == null) {
                mode = ChartTouch.CROSS
                onCrosshair(start.x, start.y)
                break
            }
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                val now = event.changes.firstOrNull()?.uptimeMillis ?: down.uptimeMillis
                if (ChartGesture.isDoubleTap(now, lastTapUptime())) {
                    onDoubleTap()
                    setLastTap(0L)
                } else {
                    onTap(start.x, start.y)
                    setLastTap(now)
                }
                return@awaitEachGesture
            }
            pending = event
            if (pressed.size >= 2) {
                mode = ChartTouch.PINCH
                break
            }
            val p = pressed.first()
            val tdx = p.position.x - start.x
            val tdy = p.position.y - start.y
            if (ChartGesture.pastSlop(tdx, tdy, slop)) {
                val g = geo()
                mode = when (ChartGesture.dragKind(tdx, tdy, start.x, g.priceLeft)) {
                    ChartGesture.Drag.PRICE_ZOOM -> ChartTouch.PRICE
                    ChartGesture.Drag.PAN -> ChartTouch.PAN
                }
                break
            }
            last = p.position
            vt.addPosition(p.uptimeMillis, p.position)
            val elapsed = (p.uptimeMillis - down.uptimeMillis).coerceAtLeast(0L)
            wait = (longPressMs - elapsed).coerceAtLeast(1L)
        }

        pending?.let { apply(it) }

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break
            apply(event)
        }

        if (mode == ChartTouch.PAN) {
            val vx = vt.calculateVelocity().x
            if (ChartGesture.shouldFling(vx)) onFling(vx, geo().slot)
        }
        state.clearZoomAcc()
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
    geo: ChartLayout.Geo,
) {
    val axisStyle = TextStyle(color = Color(0xFFDCE6EB), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    val showVol = state.has(ChartViewState.FLAG_VOL)
    val lo0 = shown.minOf { it.low }
    val hi0 = shown.maxOf { it.high }
    val (locVal, locPoc, locVah) = localVa
    val raw = ChartRange.bounds(lo0, hi0, listOf(entry, sl, tp, locPoc))
    val mid = (raw.first + raw.second) / 2.0
    val half = ((raw.second - raw.first) / 2.0) * state.priceZoom.toDouble()
    val lo = mid - half
    val hi = mid + half
    val span = (hi - lo).let { if (it <= 0) 1.0 else it }
    fun y(p: Double) = (geo.candleH * (1f - ((p - lo) / span).toFloat())).coerceIn(0f, geo.candleH)
    val n = shown.size
    val slot = geo.slot
    val bodyW = geo.bodyW()
    val maxVol = shown.maxOf { it.volume }.let { if (it <= 0) 1.0 else it }
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
    val thin = PathEffect.dashPathEffect(floatArrayOf(4f, 5f), 0f)
    val plotRight = geo.plotLeft + geo.plotWidth
    val gridRight = geo.priceLeft

    if (locVal > 0 && locVah > locVal) {
        val top = y(minOf(locVah, hi))
        val bot = y(maxOf(locVal, lo))
        if (bot > top) {
            drawRect(Color(0x1400E5C3), Offset(geo.plotLeft, top), Size(geo.plotWidth, (bot - top).coerceAtLeast(2f)))
            drawLine(Color(0x6600E5C3), Offset(geo.plotLeft, top), Offset(plotRight, top), 1.4f)
            drawLine(Color(0x6600E5C3), Offset(geo.plotLeft, bot), Offset(plotRight, bot), 1.4f)
        }
    }
    if (ChartRange.inView(locPoc, lo, hi)) {
        drawLine(accent.copy(alpha = 0.55f), Offset(geo.plotLeft, y(locPoc)), Offset(plotRight, y(locPoc)), 1.6f)
    }
    if (ChartRange.inView(support, lo, hi)) {
        drawLine(Bull.copy(alpha = 0.55f), Offset(geo.plotLeft, y(support)), Offset(plotRight, y(support)), 1.4f, pathEffect = thin)
    }
    if (ChartRange.inView(resistance, lo, hi)) {
        drawLine(Bear.copy(alpha = 0.55f), Offset(geo.plotLeft, y(resistance)), Offset(plotRight, y(resistance)), 1.4f, pathEffect = thin)
    }
    val wallCol = if (spoof >= 50) Warn else accent
    val wallDash = if (spoof >= 50) dash else null
    if (ChartRange.inView(bidWall, lo, hi)) {
        drawLine(wallCol.copy(alpha = 0.55f), Offset(geo.plotLeft, y(bidWall)), Offset(plotRight, y(bidWall)), 1.4f, pathEffect = wallDash)
    }
    if (ChartRange.inView(askWall, lo, hi)) {
        drawLine(wallCol.copy(alpha = 0.55f), Offset(geo.plotLeft, y(askWall)), Offset(plotRight, y(askWall)), 1.4f, pathEffect = wallDash)
    }

    fun drawZone(z: Smc.Zone, col: Color) {
        val x0i = z.startIdx - startIdx
        val x1i = (z.touchIdx ?: (startIdx + shown.lastIndex)) - startIdx
        if (x1i < 0 || x0i > shown.lastIndex) return
        val left = geo.plotLeft + geo.shift + slot * x0i.coerceAtLeast(0)
        val right = geo.plotLeft + geo.shift + slot * (x1i.coerceAtMost(shown.lastIndex) + 1)
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
                val x = geo.xCenter(xi)
                val lvl = if (z.side == "bear") z.low else z.high
                drawLine(Warn, Offset(geo.plotLeft, y(lvl)), Offset(plotRight, y(lvl)), 1.2f, pathEffect = thin)
                drawCircle(Warn, radius = 4f, center = Offset(x, y(if (z.side == "bear") z.high else z.low)))
            }
        }
    }

    shown.forEachIndexed { i, c ->
        val x = geo.xCenter(i)
        if (x < geo.plotLeft - slot || x > plotRight + slot) return@forEachIndexed
        val up = c.close >= c.open
        val col = if (up) Bull else Bear
        drawLine(col, Offset(x, y(c.high)), Offset(x, y(c.low)), 1.4f)
        val top = y(maxOf(c.open, c.close))
        val bot = y(minOf(c.open, c.close))
        drawRect(col, Offset(x - bodyW / 2f, top), Size(bodyW, (bot - top).coerceAtLeast(1.2f)))
        if (showVol && geo.volH > 0f) {
            val vh = (geo.volH * (c.volume / maxVol).toFloat()).coerceAtLeast(1f)
            val vTop = geo.volTop + geo.volH - vh
            drawRect(
                Brush.verticalGradient(
                    listOf(col.copy(alpha = 0.55f), col.copy(alpha = 0.15f)),
                    startY = vTop,
                    endY = geo.volTop + geo.volH,
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
                val px = geo.xCenter(i)
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

    if (state.has(ChartViewState.FLAG_HEAT) && !heat.empty && geo.heatW > 2f) {
        val left = geo.heatLeft
        val strip = geo.heatW
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
        val x = geo.xCenter(shown.lastIndex)
        drawCircle(if (divergeType.contains("bear")) Bear else Bull, 5f, Offset(x, y(shown.last().high) - 8f))
    }
    if (entry > 0) drawLine(accent, Offset(geo.plotLeft, y(entry)), Offset(plotRight, y(entry)), 2f)
    if (sl > 0) drawLine(Bear, Offset(geo.plotLeft, y(sl)), Offset(plotRight, y(sl)), 2f, pathEffect = dash)
    if (tp > 0) drawLine(Warn, Offset(geo.plotLeft, y(tp)), Offset(plotRight, y(tp)), 2f, pathEffect = dash)

    val target = ChartRange.tickCount(lo, hi)
    val ticks = ChartRange.niceTicks(lo, hi, target)
    val step = if (ticks.size >= 2) ticks[1] - ticks[0] else (hi - lo) / target.coerceAtLeast(1)

    val lastY = if (state.following && ChartRange.inView(lastClose, lo, hi) && shown.isNotEmpty()) y(lastClose) else null
    if (lastY != null) {
        val up = shown.last().close >= shown.last().open
        val badgeCol = if (up) Bull else Bear
        drawLine(badgeCol.copy(alpha = 0.7f), Offset(geo.plotLeft, lastY), Offset(gridRight, lastY), 1f, pathEffect = thin)
        val layout = measurer.measure(ChartRange.fmtAxis(lastClose, step), axisStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 9.sp))
        val bw = layout.size.width + 8f
        val bh = layout.size.height + 4f
        val bx = (geo.priceLeft + (geo.priceW - bw).coerceAtLeast(0f) / 2f).coerceAtMost(size.width - bw)
        val by = (lastY - bh / 2f).coerceIn(0f, geo.candleH - bh)
        drawRect(badgeCol, Offset(bx, by), Size(bw.coerceAtMost(geo.priceW), bh))
        drawText(layout, topLeft = Offset(bx + 4f, by + 2f))
    }

    if (hitIdx in shown.indices) {
        val hx = geo.xCenter(hitIdx)
        val hc = shown[hitIdx]
        drawLine(accent.copy(alpha = 0.85f), Offset(hx, 0f), Offset(hx, geo.candleH + geo.volH), 1.3f)
        val cy = y(hc.close)
        drawLine(accent.copy(alpha = 0.45f), Offset(geo.plotLeft, cy), Offset(gridRight, cy), 1.2f, pathEffect = thin)
        val tLayout = measurer.measure(ChartHit.formatTime(hc.openTime), axisStyle.copy(fontSize = 9.sp))
        val tx = (hx - tLayout.size.width / 2f).coerceIn(geo.plotLeft, (plotRight - tLayout.size.width).coerceAtLeast(geo.plotLeft))
        drawRect(Color(0xCC08141C), Offset(tx - 3f, geo.timeTop), Size(tLayout.size.width + 6f, tLayout.size.height + 2f))
        drawText(tLayout, topLeft = Offset(tx, geo.timeTop))
    }

    drawLine(Color.White.copy(alpha = 0.12f), Offset(geo.priceLeft, 0f), Offset(geo.priceLeft, geo.candleH + geo.volH), 1f)

    val labelH = 12.sp.toPx()
    val placed = ChartRange.placeAxisLabels(
        ticks = ticks,
        lineY = ticks.map { y(it) },
        labelH = labelH,
        top = 0f,
        bottom = geo.candleH,
        avoidY = lastY,
        avoidGap = labelH,
    )
    for (t in ticks) {
        val yy = y(t)
        drawLine(Color.White.copy(alpha = 0.07f), Offset(geo.plotLeft, yy), Offset(gridRight, yy), 1f)
    }
    for ((px, ly) in placed) {
        val layout = measurer.measure(ChartRange.fmtAxis(px, step), axisStyle.copy(fontSize = 9.sp))
        val tx = (geo.priceLeft + geo.priceW - layout.size.width - 4f).coerceAtLeast(geo.priceLeft + 2f)
        drawRect(
            Color(0xB408141C),
            Offset(tx - 3f, ly),
            Size(layout.size.width + 6f, layout.size.height + 2f),
        )
        drawText(layout, topLeft = Offset(tx, ly + 1f))
    }

    for (i in ChartLayout.timeLabelIndices(n, 4)) {
        val label = ChartHit.formatTime(shown[i].openTime)
        val layout = measurer.measure(label, axisStyle.copy(color = Color(0x99DCE6EB), fontSize = 9.sp))
        val x = (geo.xCenter(i) - layout.size.width / 2f).coerceIn(geo.plotLeft, (plotRight - layout.size.width).coerceAtLeast(geo.plotLeft))
        drawText(layout, topLeft = Offset(x, geo.timeTop))
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
