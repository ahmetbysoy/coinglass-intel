package com.coinglass.intel.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.R
import com.coinglass.intel.domain.ChartContent
import com.coinglass.intel.domain.ChartGesture
import com.coinglass.intel.domain.ChartHit
import com.coinglass.intel.domain.ChartLayout
import com.coinglass.intel.domain.ChartLevels
import com.coinglass.intel.domain.ChartRange
import com.coinglass.intel.domain.ChartSeries
import com.coinglass.intel.domain.ChartSignals
import com.coinglass.intel.domain.ChartViewport
import com.coinglass.intel.domain.Divergence
import com.coinglass.intel.domain.EmaCache
import com.coinglass.intel.domain.Overlay
import com.coinglass.intel.domain.Structure
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.domain.model.Candle
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.ChartInk
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun CandleChart(
    content: ChartContent,
    levels: ChartLevels,
    signals: ChartSignals,
    chartTf: String,
    onSelectTf: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialVisible: Int = ChartSeries.VISIBLE_BARS,
    initialOverlays: Set<Overlay> = com.coinglass.intel.domain.DEFAULT_OVERLAYS,
    onVisibleChange: (Int) -> Unit = {},
    onOverlaysChange: (Int) -> Unit = {},
    state: CandleChartState = rememberCandleChartState(initialVisible, initialOverlays),
    chartHeight: Dp = 320.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val measurer = rememberTextMeasurer()
    val labels = remember { AxisLabelCache() }
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val priceGutterPx = with(density) { 62.dp.toPx() }
    val heatGutterPx = with(density) { 24.dp.toPx() }
    val timeHpx = with(density) { 13.sp.toPx() }
    val slop = viewConfig.touchSlop
    val longPressMs = viewConfig.longPressTimeoutMillis
    val onVisibleLatest by rememberUpdatedState(onVisibleChange)
    val onOverlaysLatest by rememberUpdatedState(onOverlaysChange)
    var headerH by remember { mutableStateOf(78.dp) }
    var lastTapAt by remember { mutableStateOf(0L) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    val emaFastCache = remember { EmaCache(20) }
    val emaSlowCache = remember { EmaCache(50) }
    val emptyText = stringResource(R.string.chart_empty)
    val liveText = stringResource(R.string.chart_live)
    val autoText = stringResource(R.string.chart_auto_scale)
    val onDesc = stringResource(R.string.chart_overlay_on)
    val offDesc = stringResource(R.string.chart_overlay_off)

    LaunchedEffect(state) {
        snapshotFlow { state.visible }
            .distinctUntilChanged()
            .debounce(150)
            .collect { onVisibleLatest(it) }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.overlays.pack() }
            .distinctUntilChanged()
            .debounce(150)
            .collect { onOverlaysLatest(it) }
    }
    LaunchedEffect(chartTf) {
        state.jumpToLive()
        state.setCrosshair(null)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = maxOf(chartHeight, 280.dp))
            .clip(RoundedCornerShape(Radii.md))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.md))
            .background(ChartInk.Plot),
    ) {
        when (content) {
            ChartContent.Loading -> ChartPlaceholder(emptyText, pulsing = true)
            is ChartContent.Error -> ChartPlaceholder(content.message, pulsing = false)
            is ChartContent.Ready -> {
                val data = content.data
                val candlesRef = rememberUpdatedState(data.candles)
                SideEffect { state.syncTotal(data.candles.size) }
                val win = ChartViewport.window(data.candles.size, state.visible, state.offsetFromEnd)
                val shown = if (win.endExclusive > win.start) {
                    data.candles.subList(win.start, win.endExclusive).toList()
                } else {
                    emptyList()
                }
                if (shown.size < 2) {
                    ChartPlaceholder(emptyText, pulsing = true)
                } else {
                    val localVa = remember(shown) { Structure.volumeArea(shown) }
                    val emaFast = emaFastCache.update(data.candles)
                    val emaSlow = emaSlowCache.update(data.candles)
                    val hitIdx = ChartHit.indexOfTime(shown, state.crosshairTime)
                    val tip = ChartHit.tip(shown, hitIdx, data.liqHeat)
                    val lastClose = data.candles.last().close
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .pointerInput(Unit) {
                                handleChartTouches(
                                    state = state,
                                    slop = slop,
                                    longPressMs = longPressMs,
                                    priceGutter = priceGutterPx,
                                    heatGutter = heatGutterPx,
                                    timeH = timeHpx,
                                    onCancelFling = {
                                        flingJob?.cancel()
                                        flingJob = scope.launch { state.stopFling() }
                                    },
                                    onFling = { vx, slot ->
                                        flingJob?.cancel()
                                        flingJob = scope.launch { state.fling(vx, slot) }
                                    },
                                    onTap = { x, y ->
                                        applyCrosshair(state, candlesRef.value, x, y, priceGutterPx, heatGutterPx, timeHpx)
                                    },
                                    onDoubleTap = { state.reset() },
                                    onCrosshair = { x, y ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        applyCrosshair(state, candlesRef.value, x, y, priceGutterPx, heatGutterPx, timeHpx)
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
                            showVol = state.has(Overlay.VOL),
                            showHeat = state.has(Overlay.HEAT),
                            priceGutter = priceGutterPx,
                            heatGutter = heatGutterPx,
                            timeH = timeHpx,
                            shiftBars = state.panRemain,
                        )
                        drawChart(
                            shown = shown,
                            startIdx = win.start,
                            levels = levels,
                            signals = signals,
                            heat = data.liqHeat,
                            smc = data.smc,
                            localVa = localVa,
                            emaFast = emaFast,
                            emaSlow = emaSlow,
                            lastClose = lastClose,
                            hitIdx = hitIdx,
                            state = state,
                            accent = accent,
                            measurer = measurer,
                            labels = labels,
                            geo = geo,
                        )
                    }
                    if (tip != null) {
                        Text(
                            tip.line,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = headerH + 4.dp)
                                .padding(horizontal = Space.sm)
                                .clip(RoundedCornerShape(Radii.sm))
                                .background(ChartInk.Plate)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (!state.following) {
                        Text(
                            liveText,
                            color = scheme.onPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 70.dp, bottom = Space.sm)
                                .clip(RoundedCornerShape(Radii.sm))
                                .background(scheme.primary.copy(alpha = 0.85f))
                                .clickable { state.jumpToLive() }
                                .padding(horizontal = Space.sm, vertical = 4.dp)
                                .semantics { role = Role.Button },
                        )
                    }
                    if (state.priceZoomed) {
                        Text(
                            "⟲ $autoText",
                            color = scheme.onPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(Radii.sm))
                                .background(scheme.primary.copy(alpha = 0.85f))
                                .clickable { state.resetPriceZoom() }
                                .padding(horizontal = Space.sm, vertical = 4.dp)
                                .semantics { role = Role.Button },
                        )
                    }
                }
            }
        }
        ChartHeader(
            state = state,
            chartTf = chartTf,
            onSelectTf = onSelectTf,
            shownCount = (content as? ChartContent.Ready)?.let {
                ChartViewport.window(it.data.candles.size, state.visible, state.offsetFromEnd).size
            } ?: 0,
            totalCount = (content as? ChartContent.Ready)?.data?.candles?.size ?: 0,
            levels = levels,
            signals = signals,
            scheme = scheme,
            onDesc = onDesc,
            offDesc = offDesc,
            modifier = Modifier
                .align(Alignment.TopStart)
                .onSizeChanged { headerH = with(density) { it.height.toDp() } },
        )
    }
}

@Composable
private fun ChartPlaceholder(text: String, pulsing: Boolean) {
    val pulse = if (pulsing) {
        val t = rememberInfiniteTransition(label = "chart-load")
        val a by t.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.38f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "chart-load-a",
        )
        a
    } else {
        0.22f
    }
    Box(Modifier.fillMaxSize().background(ChartInk.Plot.copy(alpha = 1f))) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(Color.White.copy(alpha = pulse)),
        )
        Text(
            text,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private fun PointerInputScope.applyCrosshair(
    state: CandleChartState,
    candles: List<Candle>,
    x: Float,
    y: Float,
    priceGutter: Float,
    heatGutter: Float,
    timeH: Float,
) {
    val geo = geoOf(state, priceGutter, heatGutter, timeH)
    val win = ChartViewport.window(state.total, state.visible, state.offsetFromEnd)
    val last = (win.size - 1).coerceAtLeast(0)
    val idx = geo.candleIndex(x, last)
    if (idx == null) {
        state.setCrosshair(null)
        return
    }
    val c = candles.getOrNull(win.start + idx)
    if (c == null) {
        state.setCrosshair(null)
        return
    }
    val slice = if (win.endExclusive > win.start) candles.subList(win.start, win.endExclusive) else emptyList()
    val lo0 = slice.minOfOrNull { it.low } ?: c.low
    val hi0 = slice.maxOfOrNull { it.high } ?: c.high
    val raw = ChartRange.bounds(lo0, hi0, emptyList())
    val mid = (raw.first + raw.second) / 2.0
    val half = ((raw.second - raw.first) / 2.0) * state.priceZoom.toDouble()
    val target = ChartHit.priceAtY(y, geo.candleH, mid - half, mid + half)
    state.setCrosshair(c.openTime, ChartHit.magnet(c, target))
}

private fun PointerInputScope.geoOf(
    state: CandleChartState,
    priceGutter: Float,
    heatGutter: Float,
    timeH: Float,
): ChartLayout.Geo {
    val win = ChartViewport.window(state.total, state.visible, state.offsetFromEnd)
    return ChartLayout.geo(
        width = size.width.toFloat(),
        height = size.height.toFloat(),
        shown = win.size.coerceAtLeast(1),
        following = state.following,
        showVol = state.has(Overlay.VOL),
        showHeat = state.has(Overlay.HEAT),
        priceGutter = priceGutter,
        heatGutter = heatGutter,
        timeH = timeH,
        shiftBars = state.panRemain,
    )
}

private suspend fun PointerInputScope.handleChartTouches(
    state: CandleChartState,
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
        var mode = ChartGesture.Mode.UNDECIDED
        var last = start
        var pending: PointerEvent? = null
        var wait = longPressMs.coerceAtLeast(1L)
        var hapticOnce = false

        fun geo() = geoOf(state, priceGutter, heatGutter, timeH)

        fun consume(changes: List<PointerInputChange>) {
            changes.forEach { if (it.pressed || it.positionChanged()) it.consume() }
        }

        fun apply(event: PointerEvent) {
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) return
            mode = ChartGesture.afterMove(
                mode = mode,
                pointerCount = pressed.size,
                dxFromStart = (pressed.first().position.x - start.x),
                dyFromStart = (pressed.first().position.y - start.y),
                startX = start.x,
                slop = slop,
                scaleLeft = geo().priceLeft,
            )
            if (pressed.size >= 2 || mode == ChartGesture.Mode.PINCH) {
                mode = ChartGesture.Mode.PINCH
                val z = event.calculateZoom()
                val c = event.calculateCentroid()
                val g = geo()
                val focus = if (g.plotWidth <= 0f) 0.5f else ((c.x - g.plotLeft) / g.plotWidth).coerceIn(0f, 1f)
                if (z != 1f) state.zoom(z, focus)
                consume(event.changes)
                last = c
                return
            }
            val p = pressed.first()
            vt.addPosition(p.uptimeMillis, p.position)
            val dx = p.position.x - last.x
            val dy = p.position.y - last.y
            when (mode) {
                ChartGesture.Mode.PAN -> state.panByPixels(dx, geo().slot)
                ChartGesture.Mode.PRICE -> state.nudgePriceZoom(dy / size.height.coerceAtLeast(1).toFloat())
                ChartGesture.Mode.CROSS -> {
                    if (!hapticOnce) {
                        hapticOnce = true
                        onCrosshair(p.position.x, p.position.y)
                    } else {
                        onTap(p.position.x, p.position.y)
                    }
                }
                else -> Unit
            }
            consume(event.changes)
            last = p.position
        }

        while (mode == ChartGesture.Mode.UNDECIDED) {
            val event = withTimeoutOrNull(wait) { awaitPointerEvent() }
            if (event == null) {
                mode = ChartGesture.afterTimeout(mode)
                onCrosshair(start.x, start.y)
                hapticOnce = true
                break
            }
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                val now = event.changes.firstOrNull()?.uptimeMillis ?: down.uptimeMillis
                if (ChartGesture.tapKind(now, lastTapUptime()) == ChartGesture.Tap.DOUBLE) {
                    onDoubleTap()
                    setLastTap(0L)
                } else {
                    onTap(start.x, start.y)
                    setLastTap(now)
                }
                return@awaitEachGesture
            }
            pending = event
            mode = ChartGesture.afterMove(
                mode, pressed.size,
                pressed.first().position.x - start.x,
                pressed.first().position.y - start.y,
                start.x, slop, geo().priceLeft,
            )
            if (mode != ChartGesture.Mode.UNDECIDED) break
            last = pressed.first().position
            vt.addPosition(pressed.first().uptimeMillis, pressed.first().position)
            val elapsed = (pressed.first().uptimeMillis - down.uptimeMillis).coerceAtLeast(0L)
            wait = (longPressMs - elapsed).coerceAtLeast(1L)
        }

        pending?.let { apply(it) }

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break
            apply(event)
        }

        if (mode == ChartGesture.Mode.PAN) {
            val vx = vt.calculateVelocity().x
            if (ChartGesture.shouldFling(vx)) onFling(vx, geo().slot)
        }
        state.clearZoomAcc()
    }
}

private fun DrawScope.drawChart(
    shown: List<Candle>,
    startIdx: Int,
    levels: ChartLevels,
    signals: ChartSignals,
    heat: com.coinglass.intel.domain.LiqHeat.Grid,
    smc: com.coinglass.intel.domain.Smc.Report,
    localVa: Triple<Double, Double, Double>,
    emaFast: List<Double>,
    emaSlow: List<Double>,
    lastClose: Double,
    hitIdx: Int,
    state: CandleChartState,
    accent: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
    labels: AxisLabelCache,
    geo: ChartLayout.Geo,
) {
    val axisStyle = TextStyle(color = ChartInk.Axis, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    val showVol = state.has(Overlay.VOL)
    val lo0 = shown.minOf { it.low }
    val hi0 = shown.maxOf { it.high }
    val (locVal, locPoc, locVah) = localVa
    val raw = ChartRange.bounds(lo0, hi0, listOf(levels.entry, levels.sl, levels.tp, locPoc))
    val mid = (raw.first + raw.second) / 2.0
    val half = ((raw.second - raw.first) / 2.0) * state.priceZoom.toDouble()
    val lo = mid - half
    val hi = mid + half
    val span = (hi - lo).let { if (it <= 0) 1.0 else it }
    fun yRaw(p: Double) = geo.candleH * (1f - ((p - lo) / span).toFloat())
    fun y(p: Double) = yRaw(p).coerceIn(0f, geo.candleH)
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
            drawRect(ChartInk.VaFill, Offset(geo.plotLeft, top), Size(geo.plotWidth, (bot - top).coerceAtLeast(2f)))
            drawLine(ChartInk.VaLine, Offset(geo.plotLeft, top), Offset(plotRight, top), 1.4f)
            drawLine(ChartInk.VaLine, Offset(geo.plotLeft, bot), Offset(plotRight, bot), 1.4f)
        }
    }
    fun levelLine(px: Double, col: Color, width: Float, effect: PathEffect? = null) {
        if (!ChartRange.inView(px, lo, hi)) return
        drawLine(col, Offset(geo.plotLeft, y(px)), Offset(plotRight, y(px)), width, pathEffect = effect)
    }
    levelLine(locPoc, accent.copy(alpha = 0.55f), 1.6f)
    levelLine(levels.support, Bull.copy(alpha = 0.55f), 1.4f, thin)
    levelLine(levels.resistance, Bear.copy(alpha = 0.55f), 1.4f, thin)
    val wallCol = if (signals.spoofSkip) Warn else accent
    val wallDash = if (signals.spoofSkip) dash else null
    levelLine(levels.bidWall, wallCol.copy(alpha = 0.55f), 1.4f, wallDash)
    levelLine(levels.askWall, wallCol.copy(alpha = 0.55f), 1.4f, wallDash)

    fun drawZone(z: com.coinglass.intel.domain.Smc.Zone, col: Color) {
        val x0i = z.startIdx - startIdx
        val x1i = (z.touchIdx ?: (startIdx + shown.lastIndex)) - startIdx
        if (x1i < 0 || x0i > shown.lastIndex) return
        val left = geo.plotLeft + geo.shift + slot * x0i.coerceAtLeast(0)
        val right = geo.plotLeft + geo.shift + slot * (x1i.coerceAtMost(shown.lastIndex) + 1)
        val top = y(z.high)
        val bot = y(z.low)
        drawRect(col, Offset(left, minOf(top, bot)), Size((right - left).coerceAtLeast(2f), abs(bot - top).coerceAtLeast(2f)))
    }
    if (state.has(Overlay.OB)) {
        smc.obs.forEach { z ->
            drawZone(z, (if (z.side == "bull") Bull else Bear).copy(alpha = if (z.touched) 0.08f else 0.20f))
        }
    }
    if (state.has(Overlay.FVG)) {
        smc.fvgs.filter { !it.touched }.forEach { z ->
            drawZone(z, (if (z.side == "bull") Bull else Bear).copy(alpha = 0.14f))
        }
    }
    if (state.has(Overlay.SWEEP)) {
        smc.sweeps.forEach { z ->
            val xi = z.endIdx - startIdx
            if (xi in shown.indices) {
                val x = geo.xCenter(xi)
                val lvl = if (z.side == "bear") z.low else z.high
                if (ChartRange.inView(lvl, lo, hi)) {
                    drawLine(Warn, Offset(geo.plotLeft, y(lvl)), Offset(plotRight, y(lvl)), 1.2f, pathEffect = thin)
                }
                drawCircle(Warn, radius = 4f, center = Offset(x, y(if (z.side == "bear") z.high else z.low)))
            }
        }
    }

    val volUp = Bull.copy(alpha = 0.35f)
    val volDn = Bear.copy(alpha = 0.35f)
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
            drawRect(if (up) volUp else volDn, Offset(x - bodyW / 2f, vTop), Size(bodyW, vh))
        }
    }

    if (state.has(Overlay.EMA)) {
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
        drawEma(emaFast, ChartInk.EmaFast)
        drawEma(emaSlow, ChartInk.EmaSlow)
    }

    if (state.has(Overlay.HEAT) && !heat.empty && geo.heatW > 2f) {
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

    if (signals.divergence != Divergence.NONE) {
        val x = geo.xCenter(shown.lastIndex)
        drawCircle(if (signals.divergence == Divergence.BEAR) Bear else Bull, 5f, Offset(x, y(shown.last().high) - 8f))
    }
    if (levels.entry > 0) levelLine(levels.entry, accent, 2f)
    if (levels.sl > 0) levelLine(levels.sl, Bear, 2f, dash)
    if (levels.tp > 0) levelLine(levels.tp, Warn, 2f, dash)

    val target = ChartRange.tickCount(lo, hi)
    val ticks = ChartRange.niceTicks(lo, hi, target)
    val step = if (ticks.size >= 2) ticks[1] - ticks[0] else (hi - lo) / target.coerceAtLeast(1)

    val lastY = if (state.following && ChartRange.inView(lastClose, lo, hi) && shown.isNotEmpty()) y(lastClose) else null
    if (lastY != null) {
        val up = shown.last().close >= shown.last().open
        val badgeCol = if (up) Bull else Bear
        drawLine(badgeCol.copy(alpha = 0.7f), Offset(geo.plotLeft, lastY), Offset(gridRight, lastY), 1f, pathEffect = thin)
        val layout = labels.measure(measurer, ChartRange.fmtAxis(lastClose, step), axisStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 9.sp))
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
        val hitPx = state.crosshairPrice?.takeIf { it > 0.0 } ?: hc.close
        val cy = y(hitPx)
        drawLine(accent.copy(alpha = 0.45f), Offset(geo.plotLeft, cy), Offset(gridRight, cy), 1.2f, pathEffect = thin)
        val tLayout = labels.measure(measurer, ChartHit.formatTime(hc.openTime), axisStyle.copy(fontSize = 9.sp))
        val tx = (hx - tLayout.size.width / 2f).coerceIn(geo.plotLeft, (plotRight - tLayout.size.width).coerceAtLeast(geo.plotLeft))
        drawRect(ChartInk.Plate, Offset(tx - 3f, geo.timeTop), Size(tLayout.size.width + 6f, tLayout.size.height + 2f))
        drawText(tLayout, topLeft = Offset(tx, geo.timeTop))
    }

    if (state.following) {
        drawRect(ChartInk.Edge, Offset(plotRight - 6f, 0f), Size(6f, geo.candleH))
    } else if (state.offsetFromEnd >= ChartViewport.maxOffset(state.total, state.visible)) {
        drawRect(ChartInk.Edge, Offset(geo.plotLeft, 0f), Size(6f, geo.candleH))
    }

    drawLine(ChartInk.Divider, Offset(geo.priceLeft, 0f), Offset(geo.priceLeft, geo.candleH + geo.volH), 1f)

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
        drawLine(ChartInk.Grid, Offset(geo.plotLeft, yy), Offset(gridRight, yy), 1f)
    }
    val tickStyle = axisStyle.copy(fontSize = 9.sp)
    for ((px, ly) in placed) {
        val layout = labels.measure(measurer, ChartRange.fmtAxis(px, step), tickStyle)
        val tx = (geo.priceLeft + geo.priceW - layout.size.width - 4f).coerceAtLeast(geo.priceLeft + 2f)
        drawRect(ChartInk.PlateSoft, Offset(tx - 3f, ly), Size(layout.size.width + 6f, layout.size.height + 2f))
        drawText(layout, topLeft = Offset(tx, ly + 1f))
    }

    val timeStyle = axisStyle.copy(color = ChartInk.AxisMute, fontSize = 9.sp)
    for (i in ChartLayout.timeLabelIndices(n, 4)) {
        val layout = labels.measure(measurer, ChartHit.formatTime(shown[i].openTime), timeStyle)
        val x = (geo.xCenter(i) - layout.size.width / 2f).coerceIn(geo.plotLeft, (plotRight - layout.size.width).coerceAtLeast(geo.plotLeft))
        drawText(layout, topLeft = Offset(x, geo.timeTop))
    }
}

@Composable
private fun ChartHeader(
    state: CandleChartState,
    chartTf: String,
    onSelectTf: (String) -> Unit,
    shownCount: Int,
    totalCount: Int,
    levels: ChartLevels,
    signals: ChartSignals,
    scheme: ColorScheme,
    onDesc: String,
    offDesc: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ChartInk.HeaderScrim)
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
                        .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(if (on) scheme.primary else Color.White.copy(alpha = 0.12f))
                        .clickable { onSelectTf(tf) }
                        .padding(horizontal = Space.sm, vertical = 4.dp)
                        .semantics { role = Role.Button },
                )
            }
            Spacer(Modifier.weight(1f))
            Text("$shownCount/$totalCount", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Overlay.entries.take(3).forEach { o ->
                OverlayChip(o.name, state.has(o), onDesc, offDesc) { state.toggle(o) }
            }
        }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Overlay.entries.drop(3).forEach { o ->
                OverlayChip(o.name, state.has(o), onDesc, offDesc) { state.toggle(o) }
            }
        }
        Text(
            listOfNotNull(
                signals.grade.takeIf { it.isNotBlank() }?.let { "KARAR $it" },
                signals.verdict.takeIf { it.isNotBlank() },
            ).joinToString("  ").ifBlank { "" }.let { head ->
                val tail = "e ${fmtPrice(levels.entry)}  sl ${fmtPrice(levels.sl)}  tp ${fmtPrice(levels.tp)}" +
                    if (signals.spoofSkip) "  spoof-skip" else ""
                if (head.isBlank()) tail else "$head  ·  $tail"
            },
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun OverlayChip(label: String, on: Boolean, onDesc: String, offDesc: String, onToggle: () -> Unit) {
    FilterChip(
        selected = on,
        onClick = onToggle,
        label = { Text(label, fontSize = 11.sp) },
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
            .semantics {
                role = Role.Checkbox
                stateDescription = if (on) onDesc else offDesc
            },
    )
}
