package com.coinglass.intel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.domain.LiqHeat
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.domain.fmtUsd
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space

@Composable
fun LiqHeatmap(grid: LiqHeat.Grid, mark: Double) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
    ) {
        Text("LİKİDASYON HARİTASI", color = scheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 0.8.sp)
        if (grid.empty) {
            Text("henüz kademe yok — forceOrder / CG liq bekleniyor", color = scheme.onSurfaceVariant, fontSize = 12.sp)
            return
        }
        Text(
            "L ${fmtUsd(grid.longTot)}   S ${fmtUsd(grid.shortTot)}   mercek ${fmtPrice(mark)}",
            color = scheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth().height(168.dp)) {
            Text("L", color = Bear, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Top))
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
            ) {
                val n = grid.bins.size
                if (n == 0) return@Canvas
                val h = size.height / n
                val midX = size.width / 2f
                val max = grid.maxUsd.let { if (it <= 0) 1.0 else it }
                grid.bins.asReversed().forEachIndexed { i, b ->
                    val y = i * h
                    val lw = (midX * (b.longUsd / max).toFloat()).coerceAtLeast(if (b.longUsd > 0) 2f else 0f)
                    val sw = (midX * (b.shortUsd / max).toFloat()).coerceAtLeast(if (b.shortUsd > 0) 2f else 0f)
                    if (lw > 0) drawRect(Bear.copy(alpha = 0.75f), Offset(midX - lw, y + 1f), Size(lw, (h - 2f).coerceAtLeast(1f)))
                    if (sw > 0) drawRect(Bull.copy(alpha = 0.75f), Offset(midX, y + 1f), Size(sw, (h - 2f).coerceAtLeast(1f)))
                }
                drawLine(scheme.outline, Offset(midX, 0f), Offset(midX, size.height), 1.2f)
                if (mark in grid.lo..grid.hi && grid.hi > grid.lo) {
                    val t = ((mark - grid.lo) / (grid.hi - grid.lo)).toFloat()
                    val y = size.height * (1f - t)
                    drawLine(scheme.primary, Offset(0f, y), Offset(size.width, y), 1.6f)
                }
            }
            Text("S", color = Bull, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Top))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmtPrice(grid.hi), color = scheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text(fmtPrice(grid.lo), color = scheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Text("sol long liq · sağ short liq · çizgi mark", color = scheme.onSurfaceVariant, fontSize = 10.sp)
    }
}
