package com.coinglass.intel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinglass.intel.domain.SessionClock
import com.coinglass.intel.domain.fmtUsd
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn

@Composable
fun PulseScreen(vm: AppViewModel) {
    val state by vm.live.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val r = state.report
    val session = SessionClock.name(now)
    val fundLine = run {
        val n = r?.nextFundingMs ?: 0L
        if (n <= 0L) "funding η yok"
        else {
            val m = ((n - now) / 60_000L).coerceAtLeast(0)
            val hot = m < 30
            "${if (hot) "FUNDING YAKIN " else ""}${m / 60}s ${m % 60}d  ·  ${"%+.4f".format((r?.funding ?: 0.0) * 100)}%"
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
        Text(state.symbol.ifBlank { "sembol yok — Karar’da pair yaz" }, color = scheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(Space.md))
        PulseCard("SEANS (UTC)", session)
        PulseCard(
            "FUNDING",
            fundLine,
            warn = (r?.nextFundingMs ?: 0L) - now in 1..(30 * 60_000L),
        )
        PulseCard(
            "LİKİDASYON",
            if (!state.liqSeen) "akış yok"
            else "L ${fmtUsd(r?.liqLong ?: 0.0)}   S ${fmtUsd(r?.liqShort ?: 0.0)}",
        )
        r?.warnings.orEmpty().forEach { Text("! $it", color = Warn, fontSize = 12.sp, modifier = Modifier.padding(top = Space.xs)) }
        Spacer(Modifier.height(Space.md))
        DomBlock(state, r)
        Spacer(Modifier.height(Space.xl))
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
        Text(body, color = if (warn) Warn else scheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}
