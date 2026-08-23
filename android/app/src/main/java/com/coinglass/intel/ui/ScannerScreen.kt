package com.coinglass.intel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.data.db.ScoreSnapEntity
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.ui.theme.Accent
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bg
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Line
import com.coinglass.intel.ui.theme.Mute
import com.coinglass.intel.ui.theme.Surface
import com.coinglass.intel.ui.theme.Warn
import kotlin.math.abs

@Composable
fun ScannerScreen(
    snaps: List<ScoreSnapEntity>,
    scanning: Boolean,
    staleSec: Int,
    now: Long,
    compare: List<String>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRefresh: () -> Unit,
    onCompare: (String) -> Unit,
) {
    val ranked = snaps.sortedByDescending { abs(it.score) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("TARAYICI", color = Accent, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 13.sp)
                Text("${ranked.size} watchlist  |skor| sirali", color = Mute, fontSize = 11.sp)
            }
            IconButton(onClick = onRefresh, enabled = !scanning) {
                Icon(Icons.Default.Refresh, contentDescription = "tara", tint = Accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (ranked.isEmpty()) {
            Text("Watchlist bos. Canli ekranda sembol yaz, yildiza bas. Sabit coin listesi yok.", color = Mute, fontSize = 13.sp)
        }
        if (compare.size == 2) {
            val a = snaps.firstOrNull { it.symbol == compare[0] }
            val b = snaps.firstOrNull { it.symbol == compare[1] }
            if (a != null && b != null) {
                Text("KARSILASTIR", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(
                    "${a.symbol} ${"%+.1f".format(a.score)} ${a.direction}   vs   ${b.symbol} ${"%+.1f".format(b.score)} ${b.direction}",
                    color = Mute, fontSize = 12.sp,
                )
                val d = a.score - b.score
                Text("skor farki ${"%+.1f".format(d)}", color = if (d >= 0) Bull else Bear, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .border(1.dp, Line, RoundedCornerShape(14.dp))
                        .clickable { onOpen(s.symbol) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.symbol, color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "${fmtPrice(s.price)}  ${s.direction}",
                            color = Mute, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        )
                        if (stale) Text("BAYAT VERİ", color = Warn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%+.1f".format(s.score),
                            color = col, fontSize = 22.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text("R${s.risk} S${s.spoof}", color = if (s.spoof >= 50) Bear else Mute, fontSize = 10.sp)
                    }
                    Text(
                        if (s.symbol in compare) "VS*" else "VS",
                        color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onCompare(s.symbol) }.padding(8.dp),
                    )
                    IconButton(onClick = { onRemove(s.symbol) }) {
                        Icon(Icons.Default.Delete, contentDescription = "cikar", tint = Mute)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
