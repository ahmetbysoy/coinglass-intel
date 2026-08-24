package com.coinglass.intel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinglass.intel.R
import com.coinglass.intel.domain.SessionClock
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.domain.fmtUsd
import com.coinglass.intel.domain.model.V4Report
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn

@Composable
fun PulseScreen(vm: AppViewModel) {
    val state by vm.live.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val r = state.report
    val sess = SessionClock.info(now)
    val px = r?.price ?: state.lastPrice
    val wOpen = r?.weeklyOpen ?: 0.0
    val mOpen = r?.monthlyOpen ?: 0.0
    val fundHot = (r?.nextFundingMs ?: 0L) - now in 1..(30 * 60_000L)
    val eta = run {
        val n = r?.nextFundingMs ?: 0L
        if (n <= 0L) stringResource(R.string.pulse_funding_none)
        else {
            val m = ((n - now) / 60_000L).coerceAtLeast(0)
            val prefix = if (m < 30) stringResource(R.string.pulse_funding_near) + " " else ""
            prefix + "${m / 60}s ${m % 60}d"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = Space.lg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Space.sm))
        Text("PİYASA NABZI", color = scheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 13.sp)
        Text(state.symbol.ifBlank { stringResource(R.string.pulse_no_symbol) }, color = scheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(Space.md))
        PulseCard(
            stringResource(R.string.pulse_session),
            sess.name + "  ·  Asia " + onOff(sess.asia) + "  Lon " + onOff(sess.london) + "  NY " + onOff(sess.ny),
        )
        PulseCard(
            stringResource(R.string.pulse_week_month),
            openLine(px, wOpen, mOpen),
        )
        PulseCard(stringResource(R.string.pulse_funding), eta, warn = fundHot)
        FundingTable(r)
        PulseCard(
            stringResource(R.string.pulse_liq),
            if (!state.liqSeen) stringResource(R.string.pulse_liq_none)
            else "L ${fmtUsd(r?.liqLong ?: 0.0)}   S ${fmtUsd(r?.liqShort ?: 0.0)}",
        )
        r?.warnings.orEmpty().forEach { Text("! $it", color = Warn, fontSize = 12.sp, modifier = Modifier.padding(top = Space.xs)) }
        Spacer(Modifier.height(Space.md))
        DomBlock(state, r)
        Spacer(Modifier.height(Space.xl))
    }
}

private fun onOff(on: Boolean): String = if (on) "●" else "○"

private fun openLine(price: Double, weekly: Double, monthly: Double): String {
    val w = if (weekly <= 0) "w —" else "w ${fmtPrice(weekly)} (${"%+.2f".format(SessionClock.distPct(price, weekly))}%)"
    val m = if (monthly <= 0) "m —" else "m ${fmtPrice(monthly)} (${"%+.2f".format(SessionClock.distPct(price, monthly))}%)"
    return "$w   $m"
}

@Composable
private fun FundingTable(r: V4Report?) {
    val scheme = MaterialTheme.colorScheme
    val rows = r?.fundingEx.orEmpty()
    if (rows.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.sm)
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
    ) {
        Text(stringResource(R.string.pulse_funding_ex), color = scheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(Space.xs))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.exchange, color = scheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "%+.4f".format(row.rate * 100) + "%",
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PulseCard(title: String, body: String, warn: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.sm)
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, if (warn) Warn else scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
    ) {
        Text(title, color = scheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 0.8.sp)
        Text(body, color = if (warn) Warn else scheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}
