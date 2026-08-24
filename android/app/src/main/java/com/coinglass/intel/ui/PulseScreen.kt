package com.coinglass.intel.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinglass.intel.R
import com.coinglass.intel.domain.PulseMath
import com.coinglass.intel.domain.SessionClock
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.domain.fmtUsd
import com.coinglass.intel.domain.model.V4Report
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
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
    val remaining = PulseMath.remainingMs(r?.nextFundingMs ?: 0L, now)
    val fundHot = PulseMath.isHot(remaining)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = Space.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(alive = r != null && px > 0.0)
            Spacer(Modifier.width(Space.sm))
            Column {
                Text(
                    stringResource(R.string.pulse_title),
                    color = scheme.primary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    fontSize = 13.sp,
                )
                Text(
                    state.symbol.ifBlank { stringResource(R.string.pulse_no_symbol) },
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        PulseCard(title = stringResource(R.string.pulse_session)) {
            Text(
                sess.name,
                color = scheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(Space.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                SessionChip(stringResource(R.string.session_asia), sess.asia)
                SessionChip(stringResource(R.string.session_london), sess.london)
                SessionChip(stringResource(R.string.session_ny), sess.ny)
            }
        }

        PulseCard(title = stringResource(R.string.pulse_week_month)) {
            OpenRow(stringResource(R.string.pulse_weekly_open), px, r?.weeklyOpen ?: 0.0)
            OpenRow(stringResource(R.string.pulse_monthly_open), px, r?.monthlyOpen ?: 0.0)
        }

        PulseCard(title = stringResource(R.string.pulse_funding), warn = fundHot) {
            if (remaining <= 0L) {
                Text(
                    stringResource(R.string.pulse_funding_none),
                    color = scheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                val near = if (fundHot) stringResource(R.string.pulse_funding_near) + "  " else ""
                Text(
                    near + stringResource(
                        R.string.pulse_eta,
                        PulseMath.etaHours(remaining),
                        PulseMath.etaMinutes(remaining),
                    ),
                    color = if (fundHot) Warn else scheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(Space.xs))
                LinearProgressIndicator(
                    progress = PulseMath.fundingProgress(remaining),
                    modifier = Modifier.fillMaxWidth(),
                    color = if (fundHot) Warn else scheme.primary,
                )
            }
        }

        FundingTable(r)

        PulseCard(title = stringResource(R.string.pulse_liq)) {
            if (!state.liqSeen) {
                Text(
                    stringResource(R.string.pulse_liq_none),
                    color = scheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            } else {
                LiqBar(longUsd = r?.liqLong ?: 0.0, shortUsd = r?.liqShort ?: 0.0)
            }
        }

        r?.warnings.orEmpty().forEach { w ->
            Text(
                "! $w",
                color = Warn,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(Warn.copy(alpha = 0.12f))
                    .padding(horizontal = Space.md, vertical = Space.xs),
            )
        }

        Spacer(Modifier.height(Space.xs))
        DomBlock(state, r)
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun LiveDot(alive: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "pulse")
    val pulseA by t.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val desc = stringResource(if (alive) R.string.pulse_live else R.string.pulse_offline)
    Box(
        Modifier
            .size(10.dp)
            .alpha(if (alive) pulseA else 1f)
            .clip(CircleShape)
            .background(if (alive) Bull else scheme.outline)
            .semantics { contentDescription = desc },
    )
}

@Composable
private fun PulseCard(
    title: String,
    warn: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, if (warn) Warn else scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
    ) {
        Text(
            title,
            color = if (warn) Warn else scheme.onSurfaceVariant,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(Space.xs))
        content()
    }
}

@Composable
private fun SessionChip(name: String, open: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val stateDesc = stringResource(if (open) R.string.session_open else R.string.session_closed)
    Row(
        Modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(if (open) Bull.copy(alpha = 0.15f) else scheme.surfaceVariant)
            .padding(horizontal = Space.sm, vertical = 4.dp)
            .semantics { contentDescription = "$name $stateDesc" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (open) Bull else scheme.outline),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            color = if (open) Bull else scheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun OpenRow(label: String, price: Double, open: Double) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = scheme.onSurfaceVariant, fontSize = 12.sp)
        if (open <= 0) {
            Text("—", color = scheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        } else {
            val d = SessionClock.distPct(price, open)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fmtPrice(open),
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    PulseMath.fmtSigned(d, 2) + "%",
                    color = if (d >= 0) Bull else Bear,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun LiqBar(longUsd: Double, shortUsd: Double) {
    val total = longUsd + shortUsd
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "L ${fmtUsd(longUsd)}",
            color = Bear,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
        )
        Text(
            "S ${fmtUsd(shortUsd)}",
            color = Bull,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
        )
    }
    if (total > 0) {
        Spacer(Modifier.height(Space.xs))
        val lw = (longUsd / total).toFloat().coerceIn(0.02f, 0.98f)
        Row(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        ) {
            Box(Modifier.weight(lw).height(6.dp).background(Bear))
            Box(Modifier.weight(1f - lw).height(6.dp).background(Bull))
        }
    }
}

@Composable
private fun FundingTable(r: V4Report?) {
    val scheme = MaterialTheme.colorScheme
    val rows = r?.fundingEx.orEmpty()
    if (rows.isEmpty()) return
    PulseCard(title = stringResource(R.string.pulse_funding_ex)) {
        rows.forEach { row ->
            val ratePct = row.rate * 100.0
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(row.exchange, color = scheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    PulseMath.fmtSigned(ratePct, 4) + "%",
                    color = if (ratePct >= 0) Bull else Bear,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        PulseMath.spreadPct(rows.map { it.rate })?.let { spread ->
            Spacer(Modifier.height(Space.xs))
            Text(
                stringResource(R.string.pulse_funding_spread, PulseMath.fmtSigned(spread, 4)),
                color = if (spread >= 0.01) Warn else scheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}
