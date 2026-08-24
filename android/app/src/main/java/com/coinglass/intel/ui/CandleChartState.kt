package com.coinglass.intel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.coinglass.intel.domain.ChartGesture
import com.coinglass.intel.domain.ChartSeries
import com.coinglass.intel.domain.ChartViewport
import com.coinglass.intel.domain.DEFAULT_OVERLAYS
import com.coinglass.intel.domain.Overlay
import com.coinglass.intel.domain.overlaySet
import com.coinglass.intel.domain.pack

@Stable
class CandleChartState(
    visible: Int = ChartSeries.VISIBLE_BARS,
    offsetFromEnd: Int = 0,
    overlays: Set<Overlay> = DEFAULT_OVERLAYS,
    priceZoom: Float = 1f,
    yAxisAuto: Boolean = true,
) {
    var visible by mutableIntStateOf(visible)
    var offsetFromEnd by mutableIntStateOf(offsetFromEnd)
    var overlays by mutableStateOf(overlays)
    var priceZoom by mutableFloatStateOf(priceZoom.coerceIn(PRICE_MIN, PRICE_MAX))
    var yAxisAuto by mutableStateOf(yAxisAuto)
    var panRemain by mutableFloatStateOf(0f)
    var zoomRemain by mutableFloatStateOf(1f)
    var crosshairTime by mutableStateOf<Double?>(null)
    var crosshairPrice by mutableStateOf<Double?>(null)
    var total by mutableIntStateOf(0)
        internal set

    internal val flingAnim = Animatable(0f)

    val following: Boolean get() = offsetFromEnd == 0
    val priceZoomed: Boolean get() = !yAxisAuto || kotlin.math.abs(priceZoom - 1f) > 0.02f

    fun has(o: Overlay): Boolean = o in overlays
    fun toggle(o: Overlay) {
        overlays = if (o in overlays) overlays - o else overlays + o
    }

    fun zoom(factor: Float, focus01: Float) {
        val z = ChartViewport.zoomAccum(visible, zoomRemain, factor, focus01, offsetFromEnd, total)
        visible = z.visible
        offsetFromEnd = z.offset
        zoomRemain = z.remain
    }

    fun pan(deltaBars: Int) {
        offsetFromEnd = ChartViewport.pan(offsetFromEnd, deltaBars, visible, total)
        if (offsetFromEnd == 0 && deltaBars < 0) panRemain = 0f
    }

    fun panByPixels(dx: Float, slot: Float) {
        val acc = ChartViewport.pixelsToBars(dx, slot, panRemain)
        panRemain = acc.remain
        if (acc.bars != 0) pan(acc.bars)
        if (offsetFromEnd == 0 && panRemain < 0f) panRemain = 0f
    }

    fun syncTotal(newTotal: Int) {
        if (newTotal == total) return
        offsetFromEnd = ChartViewport.holdOnAppend(offsetFromEnd, following, newTotal - total)
        total = newTotal
    }

    fun reset() {
        visible = ChartSeries.VISIBLE_BARS
        offsetFromEnd = 0
        priceZoom = 1f
        yAxisAuto = true
        panRemain = 0f
        zoomRemain = 1f
        crosshairTime = null
        crosshairPrice = null
    }

    fun jumpToLive() {
        offsetFromEnd = 0
        panRemain = 0f
    }

    fun resetPriceZoom() {
        priceZoom = 1f
        yAxisAuto = true
    }

    fun clearZoomAcc() { zoomRemain = 1f }

    fun nudgePriceZoom(dy01: Float) {
        if (!dy01.isFinite()) return
        yAxisAuto = false
        priceZoom = (priceZoom * (1f + dy01 * 2.2f)).coerceIn(PRICE_MIN, PRICE_MAX)
    }

    fun setCrosshair(openTime: Double?, price: Double? = null) {
        crosshairTime = openTime
        crosshairPrice = if (openTime == null) null else price
    }

    suspend fun fling(velocityPxPerSec: Float, slotPx: Float) {
        if (!ChartGesture.shouldFling(velocityPxPerSec) || slotPx <= 0f) return
        var last = 0f
        flingAnim.snapTo(0f)
        flingAnim.animateDecay(velocityPxPerSec, exponentialDecay()) {
            val delta = value - last
            last = value
            val atLive = offsetFromEnd == 0 && panRemain <= 0f && delta < 0f
            val atOld = offsetFromEnd >= ChartViewport.maxOffset(total, visible) && delta > 0f
            if (!atLive && !atOld) panByPixels(delta, slotPx)
        }
    }

    suspend fun stopFling() {
        if (flingAnim.isRunning) flingAnim.stop()
    }

    companion object {
        const val PRICE_MIN = 0.4f
        const val PRICE_MAX = 3f

        val Saver = Saver<CandleChartState, List<Int>>(
            save = { listOf(it.visible, it.offsetFromEnd, it.overlays.pack()) },
            restore = { CandleChartState(it[0], it[1], overlaySet(it[2])) },
        )
    }
}

@Composable
fun rememberCandleChartState(initialVisible: Int = ChartSeries.VISIBLE_BARS): CandleChartState =
    rememberSaveable(saver = CandleChartState.Saver) {
        CandleChartState(visible = initialVisible.coerceIn(ChartViewport.MIN_BARS, ChartViewport.MAX_BARS))
    }
