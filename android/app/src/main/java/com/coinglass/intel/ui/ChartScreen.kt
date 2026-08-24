package com.coinglass.intel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinglass.intel.R
import com.coinglass.intel.data.settings.UserSettings
import com.coinglass.intel.domain.ChartContent
import com.coinglass.intel.domain.ChartTf
import com.coinglass.intel.domain.Smc
import com.coinglass.intel.domain.overlaySet
import com.coinglass.intel.domain.toChartLevels
import com.coinglass.intel.domain.toChartSignals
import com.coinglass.intel.domain.model.IntelUiState
import com.coinglass.intel.ui.theme.CoinGlassTheme
import com.coinglass.intel.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stateful shell: collect ViewModel, hoist callbacks as method refs.
 * Lambdas stay stable across recomposition — CandleChart does not redraw for free.
 */
@Composable
fun ChartScreen(vm: AppViewModel) {
    val state by vm.live.collectAsStateWithLifecycle()
    val cfg by vm.settings.collectAsStateWithLifecycle()

    ChartScreenContent(
        state = state,
        cfg = cfg,
        onSelectTf = vm::selectChartTf,
        onVisibleChange = vm::onChartVisibleBarsChanged,
        onOverlaysChange = vm::onChartOverlaysChanged,
    )
}

/** Stateless content: previewable, ViewModel-free. */
@Composable
internal fun ChartScreenContent(
    state: IntelUiState,
    cfg: UserSettings,
    onSelectTf: (String) -> Unit,
    onVisibleChange: (Int) -> Unit,
    onOverlaysChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val r = state.report
    val tf = ChartTf.from(state.chartTf)

    // Only the selected TF list is snapshotted — 1m ticks do not rebuild a 15m chart.
    val raw = when (tf) {
        ChartTf.M1 -> state.candles1m
        ChartTf.M3 -> state.candles3m
        ChartTf.M15 -> state.candles15m
        ChartTf.M5 -> state.candles5m
    }
    val last = raw.lastOrNull()
    val snap = remember(raw.size, last?.openTime, last?.close, last?.high, last?.low, last?.volume) {
        raw.toList()
    }

    // SMC off the main thread. UI stays fluid; result drops in when ready.
    val smc by produceState(initialValue = Smc.EMPTY, snap) {
        value = withContext(Dispatchers.Default) { Smc.analyze(snap) }
    }

    val errText = stringResource(R.string.chart_error)
    val content = remember(snap, state.loading, state.restErrors, state.liqHeat, smc, errText) {
        ChartContent.of(snap, state.loading, state.restErrors, state.liqHeat, smc, errText)
    }

    val levels = remember(r) { r.toChartLevels() }
    val signals = remember(r) { r.toChartSignals() }
    val overlays = remember(cfg.chartOverlays) { overlaySet(cfg.chartOverlays) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = Space.sm),
    ) {
        Header(state)
        CandleChart(
            content = content,
            levels = levels,
            signals = signals,
            chartTf = tf.label,
            onSelectTf = onSelectTf,
            initialVisible = cfg.chartVisibleBars,
            initialOverlays = overlays,
            onVisibleChange = onVisibleChange,
            onOverlaysChange = onOverlaysChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, name = "chart empty")
@Composable
private fun ChartScreenContentPreview() {
    CoinGlassTheme(dark = true) {
        ChartScreenContent(
            state = IntelUiState(),
            cfg = UserSettings(),
            onSelectTf = {},
            onVisibleChange = {},
            onOverlaysChange = {},
        )
    }
}
