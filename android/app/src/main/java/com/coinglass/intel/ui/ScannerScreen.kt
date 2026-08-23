package com.coinglass.intel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.data.db.DiscoverySnapEntity
import com.coinglass.intel.data.db.PaperTradeEntity
import com.coinglass.intel.data.db.ScoreSnapEntity
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private enum class ScanSort { ABS_SCORE, RISK, SPOOF, NET_RR, VOL }
private enum class RadarPane { DISCOVERY, WATCHLIST }

private data class RadarRow(
    val symbol: String,
    val price: Double,
    val score: Double,
    val direction: String,
    val coverage: Double,
    val updatedAt: Long,
    val risk: Int,
    val spoof: Int,
    val netRr: Double,
    val vol24: Double,
    val grade: String,
    val candles: String,
    val discovery: Boolean,
)

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
    var maxRisk by remember { mutableStateOf(100) }
    var maxSpoof by remember { mutableStateOf(100) }
    var minRr by remember { mutableStateOf(0.0) }
    var sort by remember { mutableStateOf(ScanSort.ABS_SCORE) }
    var grid by remember { mutableStateOf(false) }
    var pane by rememberSaveable { mutableStateOf(RadarPane.DISCOVERY.name) }
    val paneEnum = runCatching { RadarPane.valueOf(pane) }.getOrDefault(RadarPane.DISCOVERY)

    val watchRows = snaps.map {
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
    val discRows = discovery.filter { it.symbol !in watched }.map {
        RadarRow(
            symbol = it.symbol,
            price = it.price,
            score = it.score,
            direction = it.direction,
            coverage = it.coverage,
            updatedAt = it.updatedAt,
            risk = 0,
            spoof = it.spoof,
            netRr = it.netRr,
            vol24 = it.vol24,
            grade = it.grade,
            candles = it.candles1hJson,
            discovery = true,
        )
    }
    val source = if (paneEnum == RadarPane.DISCOVERY) discRows else watchRows
    val filtered = source.filter { it.risk <= maxRisk && it.spoof <= maxSpoof && abs(it.netRr) >= minRr }
    val ranked = when (sort) {
        ScanSort.ABS_SCORE -> filtered.sortedByDescending { abs(it.score) }
        ScanSort.RISK -> filtered.sortedBy { it.risk }
        ScanSort.SPOOF -> filtered.sortedBy { it.spoof }
        ScanSort.NET_RR -> filtered.sortedByDescending { abs(it.netRr) }
        ScanSort.VOL -> filtered.sortedByDescending { it.vol24 }
    }
    val hot = ranked.filter { abs(it.score) >= 20 && it.spoof < 50 && it.coverage >= 40 && abs(it.netRr) >= 1.0 }
        .take(3)
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
                    "${ranked.size}/${source.size}  |skor|   keşif ${discRows.size}  watch ${watchRows.size}",
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            IconButton(onClick = onRefresh, enabled = !scanning) {
                Icon(Icons.Default.Refresh, contentDescription = "tara", tint = scheme.primary)
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            SegChip("KEŞİF", paneEnum == RadarPane.DISCOVERY, scheme) { pane = RadarPane.DISCOVERY.name }
            SegChip("WATCHLIST", paneEnum == RadarPane.WATCHLIST, scheme) { pane = RadarPane.WATCHLIST.name }
        }
        val livePaper = openPapers.filter { it.closedAt == null }
        if (livePaper.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            Text("AÇIK KAĞIT", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            livePaper.take(6).forEach { p ->
                Text(
                    p.symbol + "  " + p.side + "  " + fmtPrice(p.entry) +
                        "  SL " + fmtPrice(p.sl) + "  TP " + fmtPrice(p.tp),
                    color = scheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onOpen(p.symbol) },
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Chip("risk≤$maxRisk", scheme) { maxRisk = if (maxRisk == 50) 100 else 50 }
            Chip("spoof≤$maxSpoof", scheme) { maxSpoof = if (maxSpoof == 49) 100 else 49 }
            Chip("RR≥${minRr.toInt()}", scheme) { minRr = if (minRr < 1) 1.0 else 0.0 }
            Chip("sıra ${sort.name.lowercase()}", scheme) {
                sort = ScanSort.entries[(sort.ordinal + 1) % ScanSort.entries.size]
            }
            Chip(if (grid) "grid" else "liste", scheme) { grid = !grid }
        }
        Spacer(Modifier.height(Space.sm))
        if (source.isEmpty()) {
            val msg = if (paneEnum == RadarPane.DISCOVERY) {
                "Keşif boş. Yenile — 24s hacim + |chg| filtre. Sabit coin listesi yok."
            } else {
                "Watchlist bos. Canli ekranda sembol yaz, yildiza bas. Sabit coin listesi yok."
            }
            Text(msg, color = scheme.onSurfaceVariant, fontSize = 13.sp)
        }
        if (hot.isNotEmpty()) {
            Text("ÖNE ÇIKAN", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(hot.joinToString("  ") { it.symbol.removeSuffix("USDT") + " " + "%+.0f".format(it.score) }, color = scheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(Space.sm))
        }
        if (compare.size == 2) {
            val pool = watchRows + discRows
            val a = pool.firstOrNull { it.symbol == compare[0] }
            val b = pool.firstOrNull { it.symbol == compare[1] }
            if (a != null && b != null) {
                Text("KARSILASTIR", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(
                    a.symbol + " " + "%+.1f".format(a.score) + " S" + a.spoof + " RR" + "%.2f".format(a.netRr) + "   vs   " + b.symbol + " " + "%+.1f".format(b.score) + " S" + b.spoof + " RR" + "%.2f".format(b.netRr),
                    color = scheme.onSurfaceVariant, fontSize = 12.sp,
                )
                Spacer(Modifier.height(Space.sm))
            }
        }
        if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.weight(1f),
            ) {
                gridItems(ranked, key = { it.symbol }) { s ->
                    val col = when {
                        "BULL" in s.direction -> Bull
                        "BEAR" in s.direction -> Bear
                        else -> Warn
                    }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radii.md))
                            .background(col.copy(alpha = (0.15f + 0.45f * (abs(s.score) / 100.0).toFloat()).coerceIn(0.15f, 0.6f)))
                            .clickable { onOpen(s.symbol) }
                            .padding(Space.sm),
                    ) {
                        Text(s.symbol.removeSuffix("USDT"), color = scheme.onSurface, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text("%+.0f".format(s.score), color = col, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        if (s.grade.isNotBlank()) Text(s.grade, color = scheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            items(ranked, key = { it.symbol }) { s ->
                val stale = now - s.updatedAt > staleSec * 1000L
                val col = when {
                    "BULL" in s.direction -> Bull
                    "BEAR" in s.direction -> Bear
                    else -> Warn
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.md))
                        .background(scheme.surface)
                        .border(1.dp, scheme.outline, RoundedCornerShape(Radii.md))
                        .clickable { onOpen(s.symbol) }
                        .padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.symbol, color = scheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (s.grade.isNotBlank()) {
                                Spacer(Modifier.width(Space.sm))
                                Text(s.grade, color = scheme.onSurface, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                        Text(
                            "${fmtPrice(s.price)}  ${s.direction}",
                            color = scheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        )
                        if (stale) Text("BAYAT VERİ", color = Warn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Sparkline(s.candles, col)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%+.1f".format(s.score),
                            color = col, fontSize = 22.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "S${s.spoof} RR" + "%.1f".format(s.netRr) + " cov${s.coverage.toInt()}",
                            color = if (s.spoof >= 50) Bear else scheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                    Text(
                        if (s.symbol in compare) "VS*" else "VS",
                        color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onCompare(s.symbol) }.padding(Space.sm),
                    )
                    if (s.discovery) {
                        IconButton(onClick = { onAdd(s.symbol) }) {
                            Icon(Icons.Default.Add, contentDescription = "watchlist", tint = scheme.primary)
                        }
                    } else {
                        IconButton(onClick = { onRemove(s.symbol) }) {
                            Icon(Icons.Default.Delete, contentDescription = "cikar", tint = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Space.xl)) }
        }
    }
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
private fun Chip(label: String, scheme: androidx.compose.material3.ColorScheme, onClick: () -> Unit) {
    Text(
        label,
        color = scheme.onSurface,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.xl))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = 6.dp),
    )
}

@Composable
private fun Sparkline(json: String, color: androidx.compose.ui.graphics.Color) {
    val closes = remember(json) { parseCloses(json) }
    if (closes.size < 2) return
    Canvas(
        Modifier
            .padding(top = 4.dp)
            .width(72.dp)
            .height(18.dp),
    ) {
        val lo = closes.min()
        val hi = closes.max()
        val span = (hi - lo).let { if (it == 0.0) 1.0 else it }
        val path = Path()
        closes.forEachIndexed { i, v ->
            val x = size.width * i / (closes.size - 1).toFloat()
            val y = size.height * (1f - ((v - lo) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2f, cap = StrokeCap.Round))
        drawLine(color.copy(alpha = 0.25f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
    }
}

private fun parseCloses(raw: String): List<Double> {
    if (raw.isBlank() || raw == "[]") return emptyList()
    return runCatching {
        val root = Json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
        root.mapNotNull { row ->
            val a = row.jsonArray
            a.getOrNull(4)?.jsonPrimitive?.doubleOrNull
        }
    }.getOrDefault(emptyList())
}
