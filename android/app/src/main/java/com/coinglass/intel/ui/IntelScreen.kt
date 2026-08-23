package com.coinglass.intel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.domain.fmtUsd
import com.coinglass.intel.domain.model.IntelUiState
import com.coinglass.intel.domain.model.LaneStats
import com.coinglass.intel.domain.model.TfPred
import com.coinglass.intel.domain.model.V4Report
import com.coinglass.intel.ui.theme.Accent
import com.coinglass.intel.ui.theme.AccentDim
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bg
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Line
import com.coinglass.intel.ui.theme.Mute
import com.coinglass.intel.ui.theme.Surface
import com.coinglass.intel.ui.theme.Surface2
import com.coinglass.intel.ui.theme.Text
import com.coinglass.intel.ui.theme.Warn
import kotlin.math.abs

@Composable
fun IntelScreen(vm: AppViewModel) {
    val state by vm.live.collectAsStateWithLifecycle()
    val cfg by vm.settings.collectAsStateWithLifecycle()
    var query by remember(state.symbol) { mutableStateOf(state.symbol) }
    val focus = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Header(state)
        if (state.stale) {
            Spacer(Modifier.height(8.dp))
            Text(
                "BAYAT VERİ",
                color = Bg,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Warn)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.uppercase() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Accent) },
            placeholder = { Text("sembol — BTC, ETH, ALLO…", color = Mute) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.submit(query)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Line,
                focusedTextColor = Text,
                unfocusedTextColor = Text,
                cursorColor = Accent,
            ),
            shape = RoundedCornerShape(14.dp),
            trailingIcon = {
                if (state.symbol.isNotBlank()) {
                    IconButton(onClick = { vm.toggleWatch(state.symbol) }) {
                        Icon(
                            if (state.inWatchlist) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "watchlist",
                            tint = if (state.inWatchlist) Warn else Mute,
                        )
                    }
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.chips.forEach { chip ->
                val on = chip == state.symbol
                Text(
                    text = chip.removeSuffix("USDT"),
                    color = if (on) Bg else Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) Accent else Surface2)
                        .clickable {
                            query = chip
                            vm.submit(chip)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.symbol.isBlank() && state.chips.isEmpty()) {
                Onboard()
                return@Column
            }
            val r = state.report
            PriceCard(state, r)
            Spacer(Modifier.height(10.dp))
            StrategyCard(r, state.hit.line)
            Spacer(Modifier.height(10.dp))
            ScoreCard(r)
            Spacer(Modifier.height(10.dp))
            SourceStale(state, cfg.staleSeconds)
            Spacer(Modifier.height(10.dp))
            val candles = if (state.chartTf == "4h") state.candles4h else state.candles1h
            CandleChart(
                candles = candles,
                entry = r?.price ?: 0.0,
                sl = r?.sl ?: 0.0,
                tp = r?.tp ?: 0.0,
                label = state.chartTf,
                onToggle = { vm.toggleChartTf() },
                support = r?.support ?: 0.0,
                resistance = r?.resistance ?: 0.0,
                bidWall = r?.bidWall ?: 0.0,
                askWall = r?.askWall ?: 0.0,
            )
            Spacer(Modifier.height(10.dp))
            MetricsGrid(r, state.liqSeen)
            if (state.restErrors.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(state: IntelUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("COINGLASS INTEL", color = Accent, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, fontSize = 13.sp)
            Text("phone · v4.3 + scalper", color = Mute, fontSize = 11.sp)
        }
        val live = state.conn.public.connected || state.conn.market.connected
        val c by animateColorAsState(if (live) Bull else Bear, label = "live")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface2)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(6.dp))
            Text(if (live) "CANLI" else "KOPUK", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PriceCard(state: IntelUiState, r: V4Report?) {
    Card {
        Text(state.symbol, color = Mute, fontSize = 12.sp, letterSpacing = 1.sp)
        val px = r?.price?.takeIf { it > 0 } ?: state.lastPrice
        Text(
            fmtPrice(px),
            color = Text,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        val chg = r?.chg24 ?: 0.0
        val chgColor = if (chg >= 0) Bull else Bear
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${if (chg >= 0) "+" else ""}${"%.2f".format(chg)}%  24h",
                color = chgColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text("vol ${fmtUsd(r?.vol24 ?: 0.0)}", color = Mute)
        }
        Text(state.statusLine, color = Mute, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ScoreCard(r: V4Report?) {
    val dir = r?.direction ?: "…"
    val score = r?.totalScore ?: 0.0
    val color = dirColor(dir)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YÖN / SKOR", color = Mute, fontSize = 11.sp, letterSpacing = 0.8.sp)
                Text(dir, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Text(
                "%+.1f".format(score),
                color = color,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(8.dp))
        val frac = ((score + 100) / 200.0).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = frac,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = color,
            trackColor = AccentDim,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mini("confluence", "%+.1f".format(r?.confluence ?: 0.0))
            Mini("coverage", "${"%.0f".format(r?.coverage ?: 0.0)}%")
            Mini("risk", "${r?.risk ?: 0}")
            Mini("spoof", "${r?.spoof ?: 0}")
        }
    }
}

@Composable
private fun StrategyCard(r: V4Report?, hitLine: String) {
    Card {
        Text("STRATEJİ", color = Mute, fontSize = 11.sp, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(4.dp))
        Text(r?.strategy ?: "veri bekleniyor…", color = Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(hitLine, color = Mute, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        if (r != null && r.sl > 0 && r.tp > 0) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Mini("SL", fmtPrice(r.sl))
                Mini("TP", fmtPrice(r.tp))
                Mini("RSI 5m", "%.1f".format(r.rsi5m))
            }
        }
    }
}

@Composable
private fun MetricsGrid(r: V4Report?, liqSeen: Boolean) {
    val liqL = if (liqSeen) fmtUsd(r?.liqLong ?: 0.0) else "N/A"
    val liqS = if (liqSeen) fmtUsd(r?.liqShort ?: 0.0) else "N/A"
    val cells = listOf(
        "OI" to fmtUsd(r?.oi ?: 0.0),
        "FUNDING" to "${"%+.4f".format((r?.funding ?: 0.0) * 100)}%",
        "L/S" to "%.3f".format(r?.ls ?: 1.0),
        "CVD" to "${"%+.1f".format(r?.cvdPct ?: 0.0)}%",
        "LIQ L" to liqL,
        "LIQ S" to liqS,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (k, v) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Surface)
                            .border(1.dp, Line, RoundedCornerShape(14.dp))
                            .padding(10.dp),
                    ) {
                        Column {
                            Text(k, color = Mute, fontSize = 10.sp, letterSpacing = 0.6.sp)
                            Text(v, color = Text, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TfRow(preds: List<TfPred>) {
    if (preds.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        preds.forEach { p ->
            val c = when (p.direction) {
                "UP" -> Bull
                "DOWN" -> Bear
                else -> Warn
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface)
                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(p.timeframe, color = Mute, fontSize = 11.sp)
                Text(p.direction, color = c.copy(alpha = (0.35f + 0.65f * p.confidence.toFloat()).coerceIn(0.35f, 1f)), fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("%${(p.confidence * 100).toInt()}", color = Mute, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Components(r: V4Report?) {
    val comps = listOf(
        "OB" to (r?.component?.get("ob") ?: 0.0),
        "TF" to (r?.component?.get("tf") ?: 0.0),
        "OI" to (r?.component?.get("oi") ?: 0.0),
        "FUND" to (r?.component?.get("funding") ?: 0.0),
        "LIQ" to (r?.component?.get("liq") ?: 0.0),
        "VOL" to (r?.component?.get("vol") ?: 0.0),
        "MOM" to (r?.component?.get("mom") ?: 0.0),
    )
    Card {
        Text("BİLEŞENLER", color = Mute, fontSize = 11.sp, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(8.dp))
        comps.forEach { (name, v) ->
            val c = if (v >= 0) Bull else Bear
            val frac = (abs(v) / 100.0).toFloat().coerceIn(0.02f, 1f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, color = Mute, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                LinearProgressIndicator(
                    progress = frac,
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = c,
                    trackColor = AccentDim,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    "%+.1f".format(v),
                    color = c,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
    }
}

@Composable
private fun WarnCard(items: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x22FFC14D))
            .border(1.dp, Warn.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { Text("! $it", color = Warn, fontSize = 12.sp) }
    }
}

@Composable
private fun ConnBar(state: IntelUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LaneDot(state.conn.public)
        LaneDot(state.conn.market)
        LaneDot(state.conn.coinglass)
    }
}

@Composable
private fun LaneDot(s: LaneStats) {
    val c = if (s.connected) Bull else Mute
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(5.dp))
        Text("${s.name} ${s.frames}", color = Mute, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = { content() },
    )
}

@Composable
private fun Mini(label: String, value: String) {
    Column {
        Text(label.uppercase(), color = Mute, fontSize = 10.sp)
        Text(value, color = Text, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
private fun Onboard() {
    Card {
        Text("NASIL ÇALIŞIR", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("1. Yukarıya herhangi bir USDT pair yaz (sabit liste yok).", color = Text, fontSize = 13.sp)
        Text("2. Yıldıza bas — watchlist senin girdilerin.", color = Text, fontSize = 13.sp)
        Text("3. Tarayıcı |skor| sıralar, İsabet sekmesi settle sonuçlarını gösterir.", color = Text, fontSize = 13.sp)
    }
}

@Composable
private fun SourceStale(state: IntelUiState, staleSec: Int) {
    val now = System.currentTimeMillis()
    fun tag(ms: Long) = if (ms == 0L) "yok" else if (now - ms > staleSec * 1000L) "BAYAT" else "ok"
    val bits = listOf(
        "fiyat" to tag(state.fresh.priceMs),
        "OI" to tag(state.fresh.oiMs),
        "fund" to tag(state.fresh.fundMs),
        "OB" to tag(state.fresh.obMs),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        bits.forEach { (k, v) ->
            val bad = v != "ok"
            Text(
                "$k $v",
                color = if (bad) Warn else Mute,
                fontSize = 10.sp,
                fontWeight = if (bad) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun dirColor(dir: String): Color = when {
    "BULL" in dir -> Bull
    "BEAR" in dir -> Bear
    else -> Warn
}
