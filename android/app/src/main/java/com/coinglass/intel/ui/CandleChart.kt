package com.coinglass.intel.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.domain.model.Candle
import com.coinglass.intel.ui.theme.Accent
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Line
import com.coinglass.intel.ui.theme.Mute
import com.coinglass.intel.ui.theme.Surface
import com.coinglass.intel.ui.theme.Warn

@Composable
fun CandleChart(
    candles: List<Candle>,
    entry: Double,
    sl: Double,
    tp: Double,
    label: String,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("GRAFİK $label", color = Mute, fontSize = 11.sp, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
            Text("1h / 4h", color = Accent, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            "entry ${fmtPrice(entry)}  sl ${fmtPrice(sl)}  tp ${fmtPrice(tp)}",
            color = Mute, fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF08141C)),
        ) {
            if (candles.size < 2) {
                Text("mum yok", color = Mute, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            } else {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(6.dp),
                ) {
                    val lows = candles.minOf { it.low }
                    val highs = candles.maxOf { it.high }
                    val extra = listOf(entry, sl, tp).filter { it > 0 }
                    val lo = (listOf(lows) + extra).min()
                    val hi = (listOf(highs) + extra).max()
                    val span = (hi - lo).let { if (it <= 0) 1.0 else it }
                    fun y(p: Double) = (size.height * (1f - ((p - lo) / span).toFloat())).coerceIn(0f, size.height)
                    val n = candles.size
                    val slot = size.width / n
                    val bodyW = (slot * 0.62f).coerceAtLeast(1.5f)
                    candles.forEachIndexed { i, c ->
                        val x = slot * i + slot / 2f
                        val up = c.close >= c.open
                        val col = if (up) Bull else Bear
                        drawLine(col, Offset(x, y(c.high)), Offset(x, y(c.low)), strokeWidth = 1.5f)
                        val top = y(maxOf(c.open, c.close))
                        val bot = y(minOf(c.open, c.close))
                        val h = (bot - top).coerceAtLeast(1.2f)
                        drawRect(col, Offset(x - bodyW / 2f, top), Size(bodyW, h))
                    }
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    if (entry > 0) drawLine(Accent, Offset(0f, y(entry)), Offset(size.width, y(entry)), 2f)
                    if (sl > 0) drawLine(Bear, Offset(0f, y(sl)), Offset(size.width, y(sl)), 2f, pathEffect = dash)
                    if (tp > 0) drawLine(Warn, Offset(0f, y(tp)), Offset(size.width, y(tp)), 2f, pathEffect = dash)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "dokun: timeframe degistir",
            color = Mute,
            fontSize = 10.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        )
    }
}
