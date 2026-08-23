package com.coinglass.intel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.data.db.OutcomeEntity
import com.coinglass.intel.ui.theme.Accent
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bg
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Mute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PerformanceScreen(rows: List<OutcomeEntity>) {
    val settled = rows.filter { it.settled15 }
    val wins = settled.count { it.win15 == true }
    val wr = if (settled.isEmpty()) 0.0 else wins.toDouble() / settled.size
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 16.dp),
    ) {
        Text("İSABET", color = Accent, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 13.sp)
        Text(
            if (settled.isEmpty()) "henuz settle yok — 15dk sonra dolacak"
            else "15m win-rate %${(wr * 100).toInt()}  ($wins/${settled.size})",
            color = Mute, fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn {
            items(rows.take(80), key = { it.id }) { o ->
                val mark = when {
                    o.win15 == true -> "OK" to Bull
                    o.win15 == false -> "X" to Bear
                    else -> "…" to Mute
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(mark.first, color = mark.second, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${o.symbol}  ${o.direction}  ${"%+.1f".format(o.score)}", color = Accent, fontSize = 13.sp)
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(o.ts)) +
                                "  px ${"%.4f".format(o.price)}" +
                                (o.px15?.let { " → ${"%.4f".format(it)}" } ?: ""),
                            color = Mute, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
