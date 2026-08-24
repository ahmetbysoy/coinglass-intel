package com.coinglass.intel.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.data.db.DiscoverySnapEntity
import com.coinglass.intel.data.db.PaperTradeEntity
import com.coinglass.intel.data.db.ScoreSnapEntity
import com.coinglass.intel.domain.Radar
import com.coinglass.intel.domain.RadarPane
import com.coinglass.intel.domain.RadarQuery
import com.coinglass.intel.domain.RadarRow
import com.coinglass.intel.domain.ScanSort
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn
import java.util.Locale
import kotlin.math.abs

@Composable
fun ScannerScreen(
    snaps: List<ScoreSnapEntity>,
    discovery: List<DiscoverySnapEntity>,
    watched: Set<String>,
    scanning: Boolean,
    staleSec: Int,
    now: Long,
    compare: List<String>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRefresh: () -> Unit,
    onCompare: (String) -> Unit,
    openPapers: List<PaperTradeEntity> = emptyList(),
) {
    val scheme = MaterialTheme.colorScheme
    var pane by rememberSaveable { mutableStateOf(RadarPane.DISCOVERY.name) }
    var sortName by rememberSaveable { mutableStateOf(ScanSort.ABS_SCORE.name) }
    var sortDesc by rememberSaveable { mutableStateOf(true) }
    var maxRisk by rememberSaveable { mutableIntStateOf(100) }
    var maxSpoof by rememberSaveable { mutableIntStateOf(100) }
    var minRr by rememberSaveable { mutableFloatStateOf(0f) }
    var query by rememberSaveable { mutableStateOf("") }
    var gridMode by rememberSaveable { mutableStateOf(false) }

    val paneEnum = runCatching { RadarPane.valueOf(pane) }.getOrDefault(RadarPane.DISCOVERY)
    val sort = runCatching { ScanSort.valueOf(sortName) }.getOrDefault(ScanSort.ABS_SCORE)

    val watchRows = remember(snaps) {
        snaps.map {
            RadarRow(
                symbol = it.symbol,
                price = it.price,
                score = it.score,
                direction = it.direction,
                coverage = it.coverage,
                updatedAt = it.updatedAt,
                risk = it.risk,
                spoof = it.spoof,
                netRr = it.netRr,
                vol24 = it.vol24,
                grade = "",
                candles = it.candles1hJson,
                discovery = false,
            )
        }
    }
    val discRows = remember(discovery, watched) {
        discovery.filter { it.symbol !in watched }.map {
            RadarRow(
                symbol = it.symbol,
                price = it.price,
                score = it.score,
                direction = it.direction,
                coverage = it.coverage,
                updatedAt = it.updatedAt,
                risk = null,
                spoof = it.spoof,
                netRr = it.netRr,
                vol24 = it.vol24,
                grade = it.grade,
                candles = it.candles1hJson,
                discovery = true,
            )
        }
    }
    val source = if (paneEnum == RadarPane.DISCOVERY) discRows else watchRows
    val q = RadarQuery(query, maxRisk, maxSpoof, minRr.toDouble(), sort, sortDesc)
    val ranked = remember(source, q) { Radar.rank(source, q) }
    val hot = remember(ranked) { Radar.hot(ranked) }
    val livePaper = remember(openPapers) { openPapers.filter { it.closedAt == null } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = Space.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("RADAR", color = scheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 13.sp)
                Text(
                    "${ranked.size}/${source.size}  ·  keşif ${discRows.size}  ·  watch ${watchRows.size}",
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            SegChip(if (gridMode) "GRID" else "LİSTE", false, scheme) { gridMode = !gridMode }
            IconButton(onClick = onRefresh, enabled = !scanning) {
                Icon(Icons.Default.Refresh, contentDescription = "tara", tint = scheme.primary)
            }
        }
        if (scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp), color = scheme.primary)
        }
        Spacer(Modifier.height(Space.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegChip("KEŞİF", paneEnum == RadarPane.DISCOVERY, scheme) { pane = RadarPane.DISCOVERY.name }
            SegChip("WATCHLIST", paneEnum == RadarPane.WATCHLIST, scheme) { pane = RadarPane.WATCHLIST.name }
            Spacer(Modifier.weight(1f))
            SearchPill(query, scheme, onChange = { query = it })
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = scheme.primary.copy(alpha = 0.22f),
            )
            FilterChip(
                selected = maxRisk < 100,
                onClick = { maxRisk = if (maxRisk < 100) 100 else 50 },
                label = { Text("risk ≤ 50", fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = maxSpoof < 100,
                onClick = { maxSpoof = if (maxSpoof < 100) 100 else 49 },
                label = { Text("spoof < 50", fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = minRr > 0f,
                onClick = { minRr = Radar.cycleMinRr(minRr.toDouble()).toFloat() },
                label = { Text("RR ≥ ${minRr.toInt()}", fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = true,
                onClick = { sortName = Radar.nextSort(sort).name },
                label = { Text(sort.label, fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = !sortDesc,
                onClick = { sortDesc = !sortDesc },
                label = { Text(if (sortDesc) "azalan" else "artan", fontSize = 11.sp) },
                colors = chipColors,
            )
        }
        Spacer(Modifier.height(Space.sm))

        if (livePaper.isNotEmpty()) {
            Text("AÇIK KAĞIT", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            livePaper.take(6).forEach { p ->
                val sideCol = if (p.side.uppercase(Locale.US).startsWith("L")) Bull else Bear
                Row(
                    Modifier.clickable { onOpen(p.symbol) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(sideCol))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${p.symbol}  ${p.side}  ${fmtPrice(p.entry)}  SL ${fmtPrice(p.sl)}  TP ${fmtPrice(p.tp)}",
                        color = scheme.onSurface,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
        }

        if (hot.isNotEmpty()) {
            Text("ÖNE ÇIKAN", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                items(hot, key = { "hot-" + it.symbol }) { h -> HotCard(h, scheme, onOpen) }
            }
            Spacer(Modifier.height(Space.sm))
        }

        if (compare.size == 2) {
            val pool = watchRows + discRows
            val a = pool.firstOrNull { it.symbol == compare[0] }
            val b = pool.firstOrNull { it.symbol == compare[1] }
            if (a != null && b != null) {
                CompareCard(a, b, scheme)
                Spacer(Modifier.height(Space.sm))
            }
        }

        when {
            ranked.isEmpty() -> EmptyState(paneEnum, filteredOut = source.isNotEmpty(), scheme)
            gridMode -> LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.weight(1f),
            ) {
                gridItems(ranked, key = { it.symbol }) { s -> HeatCell(s, scheme, onOpen) }
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.weight(1f),
            ) {
                items(ranked, key = { it.symbol }) { s ->
                    RadarListItem(
                        s = s,
                        stale = now - s.updatedAt > staleSec * 1000L,
                        inCompare = s.symbol in compare,
                        scheme = scheme,
                        onOpen = onOpen,
                        onAdd = onAdd,
                        onRemove = onRemove,
                        onCompare = onCompare,
                    )
                }
                item { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }
}

@Composable
private fun RadarListItem(
    s: RadarRow,
    stale: Boolean,
    inCompare: Boolean,
    scheme: androidx.compose.material3.ColorScheme,
    onOpen: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCompare: (String) -> Unit,
) {
    val col = dirColor(s.direction)
    val animScore by animateFloatAsState(s.score.toFloat(), tween(400), label = "score")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(scheme.surface)
            .border(1.dp, if (inCompare) scheme.primary else scheme.outline, RoundedCornerShape(Radii.md))
            .clickable { onOpen(s.symbol) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(76.dp)
                .background(Brush.verticalGradient(listOf(col, col.copy(alpha = 0.2f)))),
        )
        Column(Modifier.weight(1f).padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.symbol.removeSuffix("USDT"), color = scheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (s.grade.isNotBlank()) {
                    Spacer(Modifier.width(Space.sm))
                    Badge(s.grade, col)
                }
                if (stale) {
                    Spacer(Modifier.width(Space.sm))
                    Badge("BAYAT", Warn)
                }
            }
            Text(
                "${fmtPrice(s.price)}  ${s.direction}",
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Sparkline(s.candles, col)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(vertical = Space.md)) {
            Text(
                Radar.fmtSigned(animScore.toDouble(), 1),
                color = col,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "S${s.spoof} RR${Radar.fmtFixed(s.netRr, 1)} cov${s.coverage.toInt()}",
                color = if (s.spoof >= 50) Bear else scheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
        Text(
            if (inCompare) "VS*" else "VS",
            color = scheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onCompare(s.symbol) }.padding(Space.sm),
        )
        if (s.discovery) {
            IconButton(onClick = { onAdd(s.symbol) }) {
                Icon(Icons.Default.Add, contentDescription = "watchlist'e ekle", tint = scheme.primary)
            }
        } else {
            IconButton(onClick = { onRemove(s.symbol) }) {
                Icon(Icons.Default.Delete, contentDescription = "watchlist'ten çıkar", tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HotCard(h: RadarRow, scheme: androidx.compose.material3.ColorScheme, onOpen: (String) -> Unit) {
    val col = dirColor(h.direction)
    Column(
        Modifier
            .clip(RoundedCornerShape(Radii.md))
            .background(Brush.linearGradient(listOf(col.copy(alpha = 0.28f), scheme.surface)))
            .border(1.dp, col.copy(alpha = 0.45f), RoundedCornerShape(Radii.md))
            .clickable { onOpen(h.symbol) }
            .padding(Space.sm),
    ) {
        Text(h.symbol.removeSuffix("USDT"), color = scheme.onSurface, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Text(Radar.fmtSigned(h.score, 0), color = col, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        Text("RR ${Radar.fmtFixed(h.netRr, 1)} · S${h.spoof}", color = scheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun HeatCell(s: RadarRow, scheme: androidx.compose.material3.ColorScheme, onOpen: (String) -> Unit) {
    val col = dirColor(s.direction)
    val alpha = (0.15f + 0.45f * (abs(s.score) / 100.0).toFloat()).coerceIn(0.15f, 0.6f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.md))
            .background(col.copy(alpha = alpha))
            .clickable { onOpen(s.symbol) }
            .padding(Space.sm),
    ) {
        Text(s.symbol.removeSuffix("USDT"), color = scheme.onSurface, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Text(Radar.fmtSigned(s.score, 0), color = col, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        if (s.grade.isNotBlank()) Text(s.grade, color = scheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun CompareCard(a: RadarRow, b: RadarRow, scheme: androidx.compose.material3.ColorScheme) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(scheme.surface)
            .border(1.dp, scheme.primary.copy(alpha = 0.4f), RoundedCornerShape(Radii.md))
            .padding(Space.md),
    ) {
        Text("KARŞILAŞTIR", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        CompareLine("", a.symbol.removeSuffix("USDT"), b.symbol.removeSuffix("USDT"), null, scheme, header = true)
        CompareLine("skor", Radar.fmtSigned(a.score, 1), Radar.fmtSigned(b.score, 1), abs(a.score).compareTo(abs(b.score)), scheme)
        CompareLine("spoof", "S${a.spoof}", "S${b.spoof}", b.spoof.compareTo(a.spoof), scheme)
        CompareLine("net RR", Radar.fmtFixed(a.netRr, 2), Radar.fmtFixed(b.netRr, 2), abs(a.netRr).compareTo(abs(b.netRr)), scheme)
        CompareLine("kapsam", "${a.coverage.toInt()}", "${b.coverage.toInt()}", a.coverage.compareTo(b.coverage), scheme)
    }
}

@Composable
private fun CompareLine(
    label: String,
    left: String,
    right: String,
    winner: Int?,
    scheme: androidx.compose.material3.ColorScheme,
    header: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            left + if (winner != null && winner > 0) " *" else "",
            color = if (winner != null && winner > 0) Bull else scheme.onSurface,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (header) FontWeight.Black else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(label, color = scheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text(
            right + if (winner != null && winner < 0) " *" else "",
            color = if (winner != null && winner < 0) Bull else scheme.onSurface,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            fontWeight = if (header) FontWeight.Black else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EmptyState(pane: RadarPane, filteredOut: Boolean, scheme: androidx.compose.material3.ColorScheme) {
    val msg = when {
        filteredOut -> "Filtreler her şeyi eledi. Chip'leri gevşet."
        pane == RadarPane.DISCOVERY -> "Keşif boş. Yenile — 24s hacim + |chg| filtresi. Sabit coin listesi yok."
        else -> "Watchlist boş. Karar’da sembol yaz, yıldıza bas. Sabit coin listesi yok."
    }
    Text(msg, color = scheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = Space.xl))
}

@Composable
private fun SearchPill(query: String, scheme: androidx.compose.material3.ColorScheme, onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (!open) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.Search, contentDescription = "ara", tint = scheme.onSurfaceVariant)
        }
    } else {
        Row(
            Modifier
                .clip(RoundedCornerShape(Radii.xl))
                .background(scheme.surfaceVariant)
                .padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = scheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(110.dp).padding(vertical = 8.dp),
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "kapat",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).clickable {
                    onChange("")
                    open = false
                },
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.xl))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun SegChip(label: String, selected: Boolean, scheme: androidx.compose.material3.ColorScheme, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) scheme.onPrimary else scheme.onSurface,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.xl))
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = 6.dp),
    )
}

@Composable
private fun Sparkline(json: String, color: Color) {
    val closes = remember(json) { Radar.parseCloses(json) }
    if (closes.size < 2) return
    Canvas(
        Modifier
            .padding(top = 4.dp)
            .width(84.dp)
            .height(20.dp),
    ) {
        val lo = closes.min()
        val hi = closes.max()
        val span = (hi - lo).let { if (it == 0.0) 1.0 else it }
        val line = Path()
        closes.forEachIndexed { i, v ->
            val x = size.width * i / (closes.size - 1).toFloat()
            val y = size.height * (1f - ((v - lo) / span).toFloat())
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 2f, cap = StrokeCap.Round))
        val lastY = size.height * (1f - ((closes.last() - lo) / span).toFloat())
        drawCircle(color, radius = 3f, center = Offset(size.width, lastY))
    }
}

private fun dirColor(direction: String): Color = when {
    "BULL" in direction -> Bull
    "BEAR" in direction -> Bear
    else -> Warn
}
