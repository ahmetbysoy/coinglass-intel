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
import androidx.compose.material3.MaterialTheme
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
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn
import kotlin.math.abs

@Composable
fun IntelScreen(vm: AppViewModel) {
    val state by vm.live.collectAsStateWithLifecycle()
    val cfg by vm.settings.collectAsStateWithLifecycle()
    var query by remember(state.symbol) { mutableStateOf(state.symbol) }
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .imePadding()
            .padding(horizontal = Space.lg),
    ) {
        Header(state)
        if (state.stale) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "BAYAT VERİ",
                color = scheme.background,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(Warn)
                    .padding(horizontal = 10.dp, vertical = Space.xs),
            )
        }
        Spacer(Modifier.height(Space.md))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.uppercase() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = scheme.primary) },
            placeholder = { Text("sembol — BTC, ETH, ALLO…", color = scheme.onSurfaceVariant) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.submit(query)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outline,
                focusedTextColor = scheme.onSurface,
                unfocusedTextColor = scheme.onSurface,
                cursorColor = scheme.primary,
            ),
            shape = RoundedCornerShape(Radii.md),
            trailingIcon = {
                if (state.symbol.isNotBlank()) {
                    IconButton(onClick = { vm.toggleWatch(state.symbol) }) {
                        Icon(
                            if (state.inWatchlist) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "watchlist",
                            tint = if (state.inWatchlist) Warn else scheme.onSurfaceVariant,
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
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (state.chips.size > 1) {
                Text(
                    "<", color = scheme.primary, fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable { vm.cycleWatch(-1) }.padding(Space.sm),
                )
            }
            state.chips.forEach { chip ->
                val on = chip == state.symbol
                Text(
                    text = chip.removeSuffix("USDT"),
                    color = if (on) scheme.onPrimary else scheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.xl))
                        .background(if (on) scheme.primary else scheme.surfaceVariant)
                        .clickable {
                            query = chip
                            vm.submit(chip)
                        }
                        .padding(horizontal = Space.md, vertical = 6.dp),
                )
            }
            if (state.chips.size > 1) {
                Text(
                    ">", color = scheme.primary, fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable { vm.cycleWatch(1) }.padding(Space.sm),
                )
            }
        }
        Spacer(Modifier.height(Space.md))
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
            if (r != null && !r.enterOk && r.verdict.isNotBlank()) {
                Banner(r.verdict, Bear)
                Spacer(Modifier.height(Space.sm))
            }
            FundingBanner(r)
            VerdictCard(r)
            Spacer(Modifier.height(10.dp))
            PriceCard(state, r)
            Spacer(Modifier.height(10.dp))
            StrategyCard(r, state.hit.line, state.chartTf)
            Spacer(Modifier.height(10.dp))
            SourceStale(state, cfg.staleSeconds)
            Spacer(Modifier.height(10.dp))
            val candles = when (state.chartTf) {
                "1m" -> state.candles1m
                "3m" -> state.candles3m
                "15m" -> state.candles15m
                else -> state.candles5m
            }
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
            )
            Spacer(Modifier.height(10.dp))
            Components(r)
            Spacer(Modifier.height(10.dp))
            MetricsGrid(r, state.liqSeen)
            if (state.restErrors.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                WarnCard(state.restErrors.take(4).map { "REST $it" })
            }
            Spacer(Modifier.height(10.dp))
            TfRow(r?.tfPreds.orEmpty())
            if (!r?.strategyWarnings.isNullOrEmpty()) {
                Spacer(Modifier.height(10.dp))
                WarnCard(r!!.strategyWarnings)
            }
            Spacer(Modifier.height(18.dp))
            ConnBar(state)
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun Header(state: IntelUiState) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("COINGLASS INTEL", color = scheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, fontSize = 13.sp)
            Text("phone · v4.3 karar katmanı", color = scheme.onSurfaceVariant, fontSize = 11.sp)
        }
        val live = state.conn.public.connected || state.conn.market.connected
        val c by animateColorAsState(if (live) Bull else Bear, label = "live")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(Radii.xl))
                .background(scheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(6.dp))
            Text(if (live) "CANLI" else "KOPUK", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Banner(text: String, color: Color) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        color = scheme.onError,
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(color)
            .padding(Space.md),
    )
}

@Composable
private fun FundingBanner(r: V4Report?) {
    val n = r?.nextFundingMs ?: 0L
    if (n <= 0L) return
    val m = ((n - System.currentTimeMillis()) / 60_000L)
    if (m < 0 || m >= 30) return
    Banner("FUNDING ${m}dk — netRR'a fee yazıldı, sıkışma riski", Warn)
    Spacer(Modifier.height(Space.sm))
}

@Composable
private fun VerdictCard(r: V4Report?) {
    val scheme = MaterialTheme.colorScheme
    val dir = r?.direction ?: "…"
    val color = dirColor(dir)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("KARAR", color = scheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 0.8.sp)
                Text(
                    r?.verdict ?: "veri bekleniyor…",
                    color = if (r?.enterOk == true) color else Bear,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            GradeBadge(r?.grade ?: "–")
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mini("skor", "%+.1f".format(r?.totalScore ?: 0.0), color)
            Mini("netRR", "%.2f".format(r?.netRr ?: 0.0), if ((r?.netRr ?: 0.0) >= 1) Bull else Bear)
            Mini("spoof", "${r?.spoof ?: 0}", if ((r?.spoof ?: 0) >= 50) Bear else scheme.onSurface)
            Mini("risk", "${r?.risk ?: 0}")
        }
        if (r != null && r.spoof >= 50) {
            Text(
                "spoof ${r.spoof} → SL duvarı yok, sadece ATR+VAL",
                color = Warn, fontSize = 11.sp, modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

@Composable
private fun GradeBadge(grade: String) {
    val scheme = MaterialTheme.colorScheme
    val bg = when (grade) {
        "A" -> Bull
        "B" -> scheme.primary
        "C" -> Warn
        else -> Bear
    }
    Text(
        grade,
        color = Color.White,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(bg)
            .padding(horizontal = Space.md, vertical = Space.xs),
    )
}

@Composable
private fun PriceCard(state: IntelUiState, r: V4Report?) {
    val scheme = MaterialTheme.colorScheme
    Card {
        Text(state.symbol, color = scheme.onSurfaceVariant, fontSize = 12.sp, letterSpacing = 1.sp)
        val px = r?.price?.takeIf { it > 0 } ?: state.lastPrice
        Text(
            fmtPrice(px),
            color = scheme.onSurface,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        val chg = r?.chg24 ?: 0.0
        val chgColor = if (chg >= 0) Bull else Bear
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(
                "${if (chg >= 0) "+" else ""}${"%.2f".format(chg)}%  24h",
                color = chgColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text("vol ${fmtUsd(r?.vol24 ?: 0.0)}", color = scheme.onSurfaceVariant)
        }
        Text(state.statusLine, color = scheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = Space.xs))
    }
}

@Composable
private fun StrategyCard(r: V4Report?, hitLine: String, chartTf: String) {
    val scheme = MaterialTheme.colorScheme
    Card {
        Text("STRATEJİ", color = scheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(Space.xs))
        Text(r?.strategy ?: "veri bekleniyor…", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(hitLine, color = scheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = Space.xs))
        if (!r?.why.isNullOrBlank()) {
            Text("neden: ${r!!.why}", color = scheme.primary, fontSize = 12.sp, modifier = Modifier.padding(top = Space.xs))
        }
        if (r != null && r.sl > 0 && r.tp > 0) {
            Spacer(Modifier.height(Space.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Mini("SL", fmtPrice(r.sl), Bear)
                Mini("TP", fmtPrice(r.tp), Bull)
                Mini("RSI $chartTf", "%.1f".format(r.rsiTf[chartTf] ?: r.rsi5m))
            }
        }
    }
}

@Composable
private fun MetricsGrid(r: V4Report?, liqSeen: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val liqL = if (liqSeen) fmtUsd(r?.liqLong ?: 0.0) else "N/A"
    val liqS = if (liqSeen) fmtUsd(r?.liqShort ?: 0.0) else "N/A"
    val cells = listOf(
        Triple("OI", fmtUsd(r?.oi ?: 0.0), false),
        Triple("FUNDING", "${"%+.4f".format((r?.funding ?: 0.0) * 100)}%", abs(r?.funding ?: 0.0) > 0.0005),
        Triple("L/S", "%.3f".format(r?.ls ?: 1.0), (r?.ls ?: 1.0) > 2 || (r?.ls ?: 1.0) < 0.5),
        Triple("CVD", "${"%+.1f".format(r?.cvdPct ?: 0.0)}%", abs(r?.cvdPct ?: 0.0) > 20),
        Triple("LIQ L", liqL, false),
        Triple("LIQ S", liqS, false),
        Triple(
            "FUND η",
            run {
                val n = r?.nextFundingMs ?: 0L
                if (n <= 0L) "—"
                else {
                    val m = ((n - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
                    "${m / 60}s ${m % 60}d"
                }
            },
            ((r?.nextFundingMs ?: 0L) - System.currentTimeMillis()) in 1..(30 * 60_000L),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        cells.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { (k, v, hot) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radii.md))
                            .background(scheme.surface)
                            .border(1.dp, if (hot) Warn else scheme.outline, RoundedCornerShape(Radii.md))
                            .padding(10.dp),
                    ) {
                        Column {
                            Text(k, color = scheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 0.6.sp)
                            Text(
                                v,
                                color = scheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        preds.forEach { p ->
            val c = when (p.direction) {
                "UP" -> Bull
                "DOWN" -> Bear
                else -> Warn
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(scheme.surface)
                    .border(1.dp, scheme.outline, RoundedCornerShape(Radii.md))
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(p.timeframe, color = scheme.onSurfaceVariant, fontSize = 11.sp)
                Text(
                    p.direction,
                    color = c.copy(alpha = (0.35f + 0.65f * p.confidence.toFloat()).coerceIn(0.35f, 1f)),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                )
                Text("%${(p.confidence * 100).toInt()}", color = scheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Components(r: V4Report?) {
    val scheme = MaterialTheme.colorScheme
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
        Text("BİLEŞENLER", color = scheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(Space.sm))
        comps.forEach { (name, v) ->
            val c = if (v >= 0) Bull else Bear
            val frac = (abs(v) / 100.0).toFloat().coerceIn(0.02f, 1f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, color = scheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                LinearProgressIndicator(
                    progress = frac,
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = c,
                    trackColor = scheme.primaryContainer,
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
            .clip(RoundedCornerShape(Radii.md))
            .background(Color(0x22FFC14D))
            .border(1.dp, Warn.copy(alpha = 0.4f), RoundedCornerShape(Radii.md))
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        items.forEach { Text("! $it", color = Warn, fontSize = 12.sp) }
    }
}

@Composable
private fun ConnBar(state: IntelUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        LaneDot(state.conn.public)
        LaneDot(state.conn.market)
        LaneDot(state.conn.coinglass)
    }
}

@Composable
private fun LaneDot(s: LaneStats) {
    val scheme = MaterialTheme.colorScheme
    val c = if (s.connected) Bull else scheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(scheme.surfaceVariant)
            .padding(horizontal = Space.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(5.dp))
        Text("${s.name} ${s.frames}", color = scheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(14.dp),
        content = { content() },
    )
}

@Composable
private fun Mini(label: String, value: String, valueColor: Color? = null) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(label.uppercase(), color = scheme.onSurfaceVariant, fontSize = 10.sp)
        Text(
            value,
            color = valueColor ?: scheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun Onboard() {
    val scheme = MaterialTheme.colorScheme
    Card {
        Text("NASIL ÇALIŞIR", color = scheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(Space.sm))
        Text("1. Yukarıya herhangi bir USDT pair yaz (sabit liste yok).", color = scheme.onSurface, fontSize = 13.sp)
        Text("2. Yıldıza bas — watchlist senin girdilerin.", color = scheme.onSurface, fontSize = 13.sp)
        Text("3. Üstteki KARAR satırı 7 kaynağı tek cümleye indirir. GİRME kırmızıysa girme.", color = scheme.onSurface, fontSize = 13.sp)
        Spacer(Modifier.height(Space.sm))
        Text("terim", color = scheme.onSurfaceVariant, fontSize = 11.sp)
        Glossary("coverage", "kullanilabilen bilesen agirligi; dusukse skor eksik veri")
        Glossary("spoof", "kitapta durmayan sahte duvar; 50+ ise SL o duvari kullanmaz")
        Glossary("confluence", "timeframe oylari + hareket buyuklugu")
        Glossary("netRR", "TP/SL eksi fee ve yakin funding maliyeti")
        Glossary("grafik", "1m/3m/5m/15m chip; 600 mum REST seed, WS ezmez; VAL/VAH bant")
        Glossary("not", "A/B/C/D = coverage+confluence+spoof+risk+netRR ozeti")
    }
}

@Composable
private fun Glossary(term: String, expl: String) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Text(
        if (open) "$term: $expl" else "· $term",
        color = if (open) scheme.primary else scheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { open = !open },
    )
}

@Composable
private fun SourceStale(state: IntelUiState, staleSec: Int) {
    val scheme = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    fun tag(ms: Long) = if (ms == 0L) "yok" else if (now - ms > staleSec * 1000L) "BAYAT" else "ok"
    val bits = listOf(
        "fiyat" to tag(state.fresh.priceMs),
        "OI" to tag(state.fresh.oiMs),
        "fund" to tag(state.fresh.fundMs),
        "OB" to tag(state.fresh.obMs),
        "REST" to tag(state.fresh.restMs),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        bits.forEach { (k, v) ->
            val bad = v != "ok"
            Text(
                "$k $v",
                color = if (bad) Warn else scheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = if (bad) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(scheme.surfaceVariant)
                    .padding(horizontal = Space.sm, vertical = Space.xs),
            )
        }
    }
}

private fun dirColor(dir: String): Color = when {
    "BULL" in dir -> Bull
    "BEAR" in dir -> Bear
    else -> Warn
}
