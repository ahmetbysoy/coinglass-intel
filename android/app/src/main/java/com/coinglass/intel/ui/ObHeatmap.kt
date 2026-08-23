package com.coinglass.intel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn
import kotlin.math.max

@Composable
fun ObHeatmap(
    bids: List<Pair<Double, Double>>,
    asks: List<Pair<Double, Double>>,
    spoof: Int,
    bidWall: Double,
    askWall: Double,
) {
    if (bids.isEmpty() && asks.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(true) }
    val maxQ = max(
        bids.maxOfOrNull { it.second } ?: 1.0,
        asks.maxOfOrNull { it.second } ?: 1.0,
    ).let { if (it <= 0) 1.0 else it }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
    ) {
        Text(
            if (open) "DOM  kapat" else "DOM  aç",
            color = scheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            modifier = Modifier.clickable { open = !open },
        )
        if (!open) return
        Spacer(Modifier.height(Space.sm))
        Text(
            if (spoof >= 50) "spoof $spoof — kalın bar SL değil" else "canlı kitap · kalın = duvar",
            color = if (spoof >= 50) Warn else scheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                bids.take(8).forEach { (p, q) ->
                    LevelRow(p, q, maxQ, Bull, spoof >= 50 && bidWall > 0 && kotlin.math.abs(p - bidWall) / max(p, 1e-9) < 0.0008)
                }
            }
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                asks.take(8).forEach { (p, q) ->
                    LevelRow(p, q, maxQ, Bear, spoof >= 50 && askWall > 0 && kotlin.math.abs(p - askWall) / max(p, 1e-9) < 0.0008)
                }
            }
        }
    }
}

@Composable
private fun LevelRow(price: Double, qty: Double, maxQ: Double, color: androidx.compose.ui.graphics.Color, spoofed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val frac = (qty / maxQ).toFloat().coerceIn(0.04f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac)
                .height(16.dp)
                .background(if (spoofed) Warn.copy(alpha = 0.55f) else color.copy(alpha = 0.35f)),
        )
        Text(
            "${fmtPrice(price)}  ${"%.2f".format(qty)}",
            color = if (spoofed) Warn else scheme.onSurface,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 4.dp),
        )
    }
}
