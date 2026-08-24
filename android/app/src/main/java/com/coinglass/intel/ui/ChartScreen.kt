package com.coinglass.intel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinglass.intel.R
import com.coinglass.intel.domain.ChartContent
import com.coinglass.intel.domain.ChartLevels
import com.coinglass.intel.domain.ChartSignals
import com.coinglass.intel.domain.Divergence
import com.coinglass.intel.domain.Smc
import com.coinglass.intel.domain.overlaySet
import com.coinglass.intel.ui.theme.Space

@Composable
fun ChartScreen(vm: AppViewModel) {
    val state by vm.live.collectAsStateWithLifecycle()
    val cfg by vm.settings.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val r = state.report
    val candles = when (state.chartTf) {
        "1m" -> state.candles1m
        "3m" -> state.candles3m
        "15m" -> state.candles15m
        else -> state.candles5m
    }
    val snap = remember(candles) { candles.toList() }
    val smc = remember(snap) { Smc.analyze(snap) }
    val errText = stringResource(R.string.chart_error)
    val content = remember(snap, state.loading, state.restErrors, state.liqHeat, smc, errText) {
        ChartContent.of(snap, state.loading, state.restErrors, state.liqHeat, smc, errText)
    }
    val levels = ChartLevels(
        entry = r?.price ?: 0.0,
        sl = r?.sl ?: 0.0,
        tp = r?.tp ?: 0.0,
        support = r?.support ?: 0.0,
        resistance = r?.resistance ?: 0.0,
        bidWall = r?.bidWall ?: 0.0,
        askWall = r?.askWall ?: 0.0,
        poc = r?.poc ?: 0.0,
    )
    val signals = ChartSignals(
        spoofScore = r?.spoof ?: 0,
        divergence = Divergence.from(r?.divergeType.orEmpty()),
        grade = r?.grade.orEmpty(),
        verdict = r?.verdict.orEmpty(),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = Space.sm),
    ) {
        Header(state)
        CandleChart(
            content = content,
            levels = levels,
            signals = signals,
            chartTf = state.chartTf,
            onSelectTf = { vm.selectChartTf(it) },
            initialVisible = cfg.chartVisibleBars,
            initialOverlays = overlaySet(cfg.chartOverlays),
            onVisibleChange = { n ->
                if (n != cfg.chartVisibleBars) vm.updateSettings { it.copy(chartVisibleBars = n) }
            },
            onOverlaysChange = { packed ->
                if (packed != cfg.chartOverlays) vm.updateSettings { it.copy(chartOverlays = packed) }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
