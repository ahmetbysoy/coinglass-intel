package com.coinglass.intel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.domain.ChartSeries
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.domain.model.Candle
import com.coinglass.intel.ui.theme.Accent
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bg
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Line
import com.coinglass.intel.ui.theme.Mute
import com.coinglass.intel.ui.theme.Surface
import com.coinglass.intel.ui.theme.Surface2
import com.coinglass.intel.ui.theme.Text as Ink
import com.coinglass.intel.ui.theme.Warn

@Composable
fun CandleChart(
    candles: List<Candle>,
    entry: Double,
    sl: Double,
    tp: Double,
    chartTf: String,
    onSelectTf: (String) -> Unit,
    support: Double = 0.0,
    resistance: Double = 0.0,
    bidWall: Double = 0.0,
    askWall: Double = 0.0,
) {
    val shown = ChartSeries.visible(candles)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("GRAFİK", color = Mute, fontSize = 11.sp, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
            Text("${shown.size}/${candles.size}", color = Mute, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartSeries.TFS.forEach { tf ->
                val on = tf == chartTf
                Text(
                    tf,
                    color = if (on) Bg else Ink,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) Accent else Surface2)
                        .clickable { onSelectTf(tf) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Text(
            "entry ${fmtPrice(entry)}  sl ${fmtPrice(sl)}  tp ${fmtPrice(tp)}",
            color = Mute, fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF08141C)),
        ) {
            if (shown.size < 2) {
                Text("mum yok — sembol seç, 600 bar REST geliyor", color = Mute, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            } else {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    val volH = size.height * 0.20f
                    val candleH = size.height - volH - 4f
                    val lows = shown.minOf { it.low }
                    val highs = shown.maxOf { it.high }
                    val extra = listOf(entry, sl, tp, support, resistance, bidWall, askWall).filter { it > 0 }
                    val lo = (listOf(lows) + extra).min()
                    val hi = (listOf(highs) + extra).max()
                    val span = (hi - lo).let { if (it <= 0) 1.0 else it }
                    fun y(p: Double) = (candleH * (1f - ((p - lo) / span).toFloat())).coerceIn(0f, candleH)
                    val n = shown.size
                    val slot = size.width / n
                    val bodyW = (slot * 0.62f).coerceIn(1.6f, 8f)
                    val maxVol = shown.maxOf { it.volume }.let { if (it <= 0) 1.0 else it }
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    val thin = PathEffect.dashPathEffect(floatArrayOf(4f, 5f), 0f)
                    if (support > 0) drawLine(Bull.copy(alpha = 0.55f), Offset(0f, y(support)), Offset(size.width, y(support)), 1.4f, pathEffect = thin)
                    if (resistance > 0) drawLine(Bear.copy(alpha = 0.55f), Offset(0f, y(resistance)), Offset(size.width, y(resistance)), 1.4f, pathEffect = thin)
                    if (bidWall > 0) drawLine(Accent.copy(alpha = 0.35f), Offset(0f, y(bidWall)), Offset(size.width, y(bidWall)), 1.2f)
                    if (askWall > 0) drawLine(Warn.copy(alpha = 0.35f), Offset(0f, y(askWall)), Offset(size.width, y(askWall)), 1.2f)
                    shown.forEachIndexed { i, c ->
                        val x = slot * i + slot / 2f
                        val up = c.close >= c.open
                        val col = if (up) Bull else Bear
                        drawLine(col, Offset(x, y(c.high)), Offset(x, y(c.low)), strokeWidth = 1.4f)
                        val top = y(maxOf(c.open, c.close))
                        val bot = y(minOf(c.open, c.close))
                        val h = (bot - top).coerceAtLeast(1.2f)
                        drawRect(col, Offset(x - bodyW / 2f, top), Size(bodyW, h))
                        val vh = (volH * (c.volume / maxVol).toFloat()).coerceAtLeast(1f)
                        drawRect(
                            col.copy(alpha = 0.45f),
                            Offset(x - bodyW / 2f, size.height - vh),
                            Size(bodyW, vh),
                        )
                    }
                    if (entry > 0) drawLine(Accent, Offset(0f, y(entry)), Offset(size.width, y(entry)), 2f)
                    if (sl > 0) drawLine(Bear, Offset(0f, y(sl)), Offset(size.width, y(sl)), 2f, pathEffect = dash)
                    if (tp > 0) drawLine(Warn, Offset(0f, y(tp)), Offset(size.width, y(tp)), 2f, pathEffect = dash)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "son ${ChartSeries.VISIBLE_BARS} mum  ·  VAL/VAH + hacim  ·  REST 600 seed, WS ezmez",
            color = Mute,
            fontSize = 10.sp,
        )
    }
}
