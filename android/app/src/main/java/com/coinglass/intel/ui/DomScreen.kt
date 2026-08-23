package com.coinglass.intel.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coinglass.intel.domain.model.IntelUiState
import com.coinglass.intel.domain.model.V4Report
import com.coinglass.intel.ui.theme.Space

/** DOM + heat + metrics. Lives on Nabız, not under the chart. */
@Composable
fun DomBlock(state: IntelUiState, report: V4Report?) {
    ObHeatmap(state.bids, state.asks, report?.spoof ?: 0, report?.bidWall ?: 0.0, report?.askWall ?: 0.0)
    Spacer(Modifier.height(10.dp))
    LiqHeatmap(state.liqHeat, report?.price ?: state.lastPrice)
    Spacer(Modifier.height(10.dp))
    LiqPulse(report, state.liqSeen)
    Spacer(Modifier.height(10.dp))
    MetricsGrid(report, state.liqSeen)
    if (state.restErrors.isNotEmpty()) {
        Spacer(Modifier.height(Space.sm))
        WarnCard(state.restErrors.take(4).map { "REST $it" })
    }
    Spacer(Modifier.height(10.dp))
    TfRow(report?.tfPreds.orEmpty())
    Spacer(Modifier.height(10.dp))
    Components(report)
    if (!report?.strategyWarnings.isNullOrEmpty()) {
        Spacer(Modifier.height(10.dp))
        WarnCard(report!!.strategyWarnings)
    }
    Spacer(Modifier.height(18.dp))
    ConnBar(state)
}
