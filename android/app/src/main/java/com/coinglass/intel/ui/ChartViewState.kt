package com.coinglass.intel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.coinglass.intel.domain.ChartSeries
import com.coinglass.intel.domain.ChartViewport

@Stable
class ChartViewState(
    visible: Int = ChartSeries.VISIBLE_BARS,
    offsetFromEnd: Int = 0,
    flags: Int = FLAG_HEAT or FLAG_VOL,
) {
    var visible by mutableIntStateOf(visible)
    var offsetFromEnd by mutableIntStateOf(offsetFromEnd)
    var flags by mutableIntStateOf(flags)

    val following: Boolean get() = offsetFromEnd == 0

    fun toggle(f: Int) { flags = flags xor f }
    fun has(f: Int): Boolean = flags and f != 0

    fun zoom(factor: Float, focus01: Float, total: Int) {
        val (v, o) = ChartViewport.zoom(visible, factor, focus01, offsetFromEnd, total)
        visible = v
        offsetFromEnd = o
    }

    fun pan(deltaBars: Int, total: Int) {
        offsetFromEnd = ChartViewport.pan(offsetFromEnd, deltaBars, visible, total)
    }

    fun onGrow(prevTotal: Int, newTotal: Int) {
        offsetFromEnd = ChartViewport.holdOnAppend(offsetFromEnd, following, newTotal - prevTotal)
    }

    fun reset() {
        visible = ChartSeries.VISIBLE_BARS
        offsetFromEnd = 0
    }

    fun jumpToLive() { offsetFromEnd = 0 }

    companion object {
        const val FLAG_OB = 1
        const val FLAG_FVG = 2
        const val FLAG_SWEEP = 4
        const val FLAG_HEAT = 8
        const val FLAG_EMA = 16
        const val FLAG_VOL = 32

        val Saver = Saver<ChartViewState, List<Int>>(
            save = { listOf(it.visible, it.offsetFromEnd, it.flags) },
            restore = { ChartViewState(it[0], it[1], it[2]) },
        )
    }
}

@Composable
fun rememberChartViewState(): ChartViewState =
    rememberSaveable(saver = ChartViewState.Saver) { ChartViewState() }
