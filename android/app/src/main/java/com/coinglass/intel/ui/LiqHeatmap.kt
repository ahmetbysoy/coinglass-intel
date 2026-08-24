package com.coinglass.intel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

private object HeatStyle {
    const val MIN_BAR_PX = 2f
    const val BASE_ALPHA = 0.30f
    const val HOT_ALPHA = 0.95f
    const val AXIS_STROKE = 1.2f
    const val MARK_STROKE = 1.6f
    const val CLUSTER_STROKE = 1.5f
    val chartHeight = 176.dp
    val markDash = floatArrayOf(10f, 6f)
}

@Composable
fun LiqHeatmap(
    grid: LiqHeat.Grid,
    mark: Double,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val stats = remember(grid, mark) { LiqHeat.stats(grid, mark) }
    var selected by remember(grid.lo, grid.hi, grid.bins.size) { mutableIntStateOf(-1) }
    val selectedNow by rememberUpdatedState(selected)
    val haptics = LocalHapticFeedback.current
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(grid.lo, grid.hi, grid.bins.size) {
        reveal.snapTo(0.18f)
        reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LİKİDASYON HARİTASI", color = scheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 0.8.sp)
            Spacer(Modifier.weight(1f))
            if (!grid.empty) {
                val biasPct = (stats.upBias * 100f).toInt().coerceIn(0, 100)
                val up = biasPct >= 50
                Text(
                    if (up) "yukarı %$biasPct" else "aşağı %${100 - biasPct}",
                    color = if (up) Bull else Bear,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (grid.empty) {
            Spacer(Modifier.height(Space.md))
            Text(
                "henüz kademe yok — forceOrder / CG liq bekleniyor",
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        } else {
            val n = grid.bins.size
            val sel = grid.bins.getOrNull(selected)
            Text(
                if (sel != null) {
                    "▸ ${fmtPrice(sel.mid)}  L ${fmtUsd(sel.longUsd)} · S ${fmtUsd(sel.shortUsd)}"
                } else {
                    "L ${fmtUsd(grid.longTot)}   S ${fmtUsd(grid.shortTot)}   mark ${fmtPrice(mark)}"
                },
                color = if (sel != null) scheme.primary else scheme.onSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(Space.sm))

            val outline = scheme.outline
            val accent = scheme.primary
            val clusterStroke = scheme.secondary.copy(alpha = 0.55f)
            val selFill = scheme.primary.copy(alpha = 0.14f)

            Row(Modifier.fillMaxWidth().height(HeatStyle.chartHeight)) {
                Text(
                    "L",
                    color = Bear,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Canvas(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp)
                        .semantics {
                            val extra = if (sel != null) {
                                " Seçili ${fmtPrice(sel.mid)} long ${fmtUsd(sel.longUsd)} short ${fmtUsd(sel.shortUsd)}."
                            } else {
                                ""
                            }
                            contentDescription =
                                "Likidasyon haritası. Long ${fmtUsd(grid.longTot)}, short ${fmtUsd(grid.shortTot)}.$extra"
                        }
                        .pointerInput(grid.lo, grid.hi, n) {
                            detectTapGestures { off ->
                                val idx = LiqHeat.binIndexAt(off.y, size.height, n)
                                val cur = selectedNow
                                selected = if (idx >= 0 && idx == cur) -1 else idx
                                if (idx >= 0 && idx != cur) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                        .pointerInput(grid.lo, grid.hi, n) {
                            var last = selectedNow
                            detectDragGestures { change, _ ->
                                val idx = LiqHeat.binIndexAt(change.position.y, size.height, n)
                                if (idx >= 0 && idx != last) {
                                    last = idx
                                    selected = idx
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                ) {
                    val h = size.height / n
                    val midX = size.width / 2f
                    val max = grid.maxUsd.let { if (it <= 0) 1.0 else it }
                    val gap = if (h > 4f) 1f else 0f
                    val clusterIdx = stats.clusters.map { it.index }.toSet()
                    val grow = reveal.value

                    for (i in 0 until n) {
                        val b = grid.bins[i]
                        val y = (n - 1 - i) * h
                        val li = (b.longUsd / max).toFloat()
                        val si = (b.shortUsd / max).toFloat()
                        val lw = (midX * li * grow).coerceAtLeast(if (b.longUsd > 0) HeatStyle.MIN_BAR_PX else 0f)
                        val sw = (midX * si * grow).coerceAtLeast(if (b.shortUsd > 0) HeatStyle.MIN_BAR_PX else 0f)
                        val bh = (h - 2f * gap).coerceAtLeast(1f)

                        if (lw > 0) {
                            drawRect(
                                lerp(Bear.copy(alpha = HeatStyle.BASE_ALPHA), Bear.copy(alpha = HeatStyle.HOT_ALPHA), li),
                                Offset(midX - lw, y + gap),
                                Size(lw, bh),
                            )
                        }
                        if (sw > 0) {
                            drawRect(
                                lerp(Bull.copy(alpha = HeatStyle.BASE_ALPHA), Bull.copy(alpha = HeatStyle.HOT_ALPHA), si),
                                Offset(midX, y + gap),
                                Size(sw, bh),
                            )
                        }
                        if (i in clusterIdx) {
                            drawRect(
                                clusterStroke,
                                Offset(0f, y + gap),
                                Size(size.width, bh),
                                style = Stroke(HeatStyle.CLUSTER_STROKE),
                            )
                        }
                        if (i == selected) {
                            drawRect(selFill, Offset(0f, y), Size(size.width, h))
                        }
                    }

                    drawLine(outline, Offset(midX, 0f), Offset(midX, size.height), HeatStyle.AXIS_STROKE)

                    val t = LiqHeat.markT(mark, grid.lo, grid.hi)
                    if (t.isFinite()) {
                        if (t in 0.0..1.0) {
                            val y = size.height * (1f - t.toFloat())
                            drawLine(
                                accent,
                                Offset(0f, y),
                                Offset(size.width, y),
                                HeatStyle.MARK_STROKE,
                                pathEffect = PathEffect.dashPathEffect(HeatStyle.markDash),
                            )
                        } else {
                            val y = if (t > 1.0) 4f else size.height - 4f
                            drawLine(accent, Offset(midX - 12f, y), Offset(midX + 12f, y), 3f)
                        }
                    }
                }
                Text(
                    "S",
                    color = Bull,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Column(
                    Modifier.fillMaxHeight().padding(start = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(fmtPrice(grid.hi), color = scheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(fmtPrice((grid.hi + grid.lo) / 2), color = scheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(fmtPrice(grid.lo), color = scheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(Modifier.height(Space.sm))
            val tot = grid.longTot + grid.shortTot
            val frac = if (tot > 0) (grid.longTot / tot).toFloat().coerceIn(0.03f, 0.97f) else 0.5f
            Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                Box(Modifier.weight(frac).fillMaxHeight().background(Bear))
                Box(Modifier.weight(1f - frac).fillMaxHeight().background(Bull))
            }

            if (stats.clusters.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    stats.clusters.forEach { c ->
                        Text(
                            "${fmtPrice(c.price)} · ${fmtUsd(c.usd)}",
                            color = if (c.longDominant) Bear else Bull,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radii.sm))
                                .background(scheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Text(
                "sol long liq · sağ short liq · kesik çizgi mark · dokun/sürükle: kademe",
                color = scheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
