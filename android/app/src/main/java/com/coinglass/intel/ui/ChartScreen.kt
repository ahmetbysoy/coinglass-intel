package com.coinglass.intel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinglass.intel.ui.theme.Space

@Composable
fun ChartScreen(vm: AppViewModel) {
    val state by vm.live.collectAsStateWithLifecycle()
    val cfg by vm.settings.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
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
            .padding(horizontal = Space.lg)
            .verticalScroll(scroll),
    ) {
        Header(state)
        Spacer(Modifier.height(Space.sm))
        SourceStale(state, cfg.staleSeconds)
        Spacer(Modifier.height(Space.sm))
        CandleChart(
            candles = candles,
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
            chartHeight = 320.dp,
        )
        Spacer(Modifier.height(10.dp))
        ObHeatmap(state.bids, state.asks, r?.spoof ?: 0, r?.bidWall ?: 0.0, r?.askWall ?: 0.0)
        Spacer(Modifier.height(10.dp))
        LiqHeatmap(state.liqHeat, r?.price ?: state.lastPrice)
        Spacer(Modifier.height(10.dp))
        LiqPulse(r, state.liqSeen)
        Spacer(Modifier.height(10.dp))
        MetricsGrid(r, state.liqSeen)
        if (state.restErrors.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            WarnCard(state.restErrors.take(4).map { "REST $it" })
        }
        Spacer(Modifier.height(10.dp))
        TfRow(r?.tfPreds.orEmpty())
        Spacer(Modifier.height(10.dp))
        Components(r)
        if (!r?.strategyWarnings.isNullOrEmpty()) {
            Spacer(Modifier.height(10.dp))
            WarnCard(r!!.strategyWarnings)
        }
        Spacer(Modifier.height(18.dp))
        ConnBar(state)
        Spacer(Modifier.height(Space.xl))
    }
}
