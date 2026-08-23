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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = Space.sm),
    ) {
        Header(state)
        CandleChart(
            candles = candles,
            smc = remember(candles) { com.coinglass.intel.domain.Smc.analyze(candles) },
            entry = r?.price ?: 0.0,
            sl = r?.sl ?: 0.0,
            tp = r?.tp ?: 0.0,
            chartTf = state.chartTf,
            onSelectTf = { vm.selectChartTf(it) },
            support = r?.support ?: 0.0,
            resistance = r?.resistance ?: 0.0,
            bidWall = r?.bidWall ?: 0.0,
            askWall = r?.askWall ?: 0.0,
            poc = r?.poc ?: 0.0,
            spoof = r?.spoof ?: 0,
            divergeType = r?.divergeType.orEmpty(),
            liqHeat = state.liqHeat,
            initialVisible = cfg.chartVisibleBars,
            onVisibleChange = { n ->
                if (n != cfg.chartVisibleBars) vm.updateSettings { it.copy(chartVisibleBars = n) }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
