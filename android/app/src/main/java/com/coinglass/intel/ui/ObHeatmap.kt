package com.coinglass.intel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.domain.ObBook
import com.coinglass.intel.domain.ObLevel
import com.coinglass.intel.domain.fmtPrice
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn
import java.util.Locale

@Composable
fun ObHeatmap(
    bids: List<Pair<Double, Double>>,
    asks: List<Pair<Double, Double>>,
    spoof: Int,
    bidWall: Double,
    askWall: Double,
    modifier: Modifier = Modifier,
    onLevelClick: ((price: Double) -> Unit)? = null,
) {
    if (bids.isEmpty() && asks.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(true) }
    var deep by rememberSaveable { mutableStateOf(false) }
    var cumulative by rememberSaveable { mutableStateOf(false) }

    val spoofOn = ObBook.spoofActive(spoof)
    val rows = ObBook.rows(deep)
    val book = remember(bids, asks, rows, spoofOn, bidWall, askWall) {
        ObBook.build(bids, asks, rows, spoofOn, bidWall, askWall)
    }

    val pulse = rememberInfiniteTransition(label = "spoof-pulse")
    val pulseA by pulse.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "wallPulse",
    )
    val wallAlpha = if (spoofOn) pulseA else 0.55f
    val chevron by animateFloatAsState(if (open) 0f else -90f, label = "chevron")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) { open = !open }
                .semantics { role = Role.Button }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "DOM · CANLI KİTAP",
                color = scheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.weight(1f))
            book.spreadPct?.let {
                Text(
                    "spread ${String.format(Locale.US, "%.3f", it)}%",
                    color = scheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(Space.sm))
            }
            Text("▾", color = scheme.primary, fontSize = 12.sp, modifier = Modifier.rotate(chevron))
        }

        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(Space.sm))
                Text(
                    if (spoofOn) "spoof $spoof — kalın bar SL değil, tuzak olabilir"
                    else "canlı kitap · kalın bar = gerçek duvar",
                    color = if (spoofOn) Warn else scheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(Space.sm))
                Row {
                    ModeChip("16 DERİNLİK", deep) { deep = !deep }
                    Spacer(Modifier.width(Space.sm))
                    ModeChip("KÜMÜLATİF", cumulative) { cumulative = !cumulative }
                }
                Spacer(Modifier.height(Space.sm))
                ImbalanceBar(book.bidTotal, book.askTotal)
                Spacer(Modifier.height(Space.sm))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "ALIŞ",
                        color = Bull,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "SATIŞ",
                        color = Bear,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(2.dp))
                val maxQ = book.maxQ(cumulative)
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        book.bids.forEach { lvl ->
                            LevelRow(
                                level = lvl,
                                maxQ = maxQ,
                                cumulative = cumulative,
                                color = Bull,
                                mirrored = true,
                                wallAlpha = wallAlpha,
                                onClick = onLevelClick?.let { cb -> { cb(lvl.price) } },
                            )
                        }
                    }
                    Spacer(Modifier.width(Space.sm))
                    Column(Modifier.weight(1f)) {
                        book.asks.forEach { lvl ->
                            LevelRow(
                                level = lvl,
                                maxQ = maxQ,
                                cumulative = cumulative,
                                color = Bear,
                                mirrored = false,
                                wallAlpha = wallAlpha,
                                onClick = onLevelClick?.let { cb -> { cb(lvl.price) } },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "Σ ${ObBook.fmtQty(book.bidTotal)}",
                        color = Bull,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Σ ${ObBook.fmtQty(book.askTotal)}",
                        color = Bear,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImbalanceBar(bidTotal: Double, askTotal: Double) {
    val scheme = MaterialTheme.colorScheme
    val total = bidTotal + askTotal
    val target = if (total > 0) (bidTotal / total).toFloat() else 0.5f
    val bidShare by animateFloatAsState(
        target.coerceIn(0.05f, 0.95f),
        tween(400),
        label = "imbalance",
    )
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        ) {
            Box(Modifier.weight(bidShare).fillMaxHeight().background(Bull.copy(alpha = 0.85f)))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.weight(1f - bidShare).fillMaxHeight().background(Bear.copy(alpha = 0.85f)))
        }
        Spacer(Modifier.height(2.dp))
        val bidPct = (target * 100f).toInt()
        Text(
            "alıcı %$bidPct · satıcı %${100 - bidPct}",
            color = scheme.onSurfaceVariant,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ModeChip(text: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        color = if (active) scheme.primary else scheme.onSurfaceVariant,
        fontSize = 10.sp,
        fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) scheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (active) scheme.primary.copy(alpha = 0.4f) else scheme.outline,
                RoundedCornerShape(6.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun LevelRow(
    level: ObLevel,
    maxQ: Double,
    cumulative: Boolean,
    color: Color,
    mirrored: Boolean,
    wallAlpha: Float,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val shown = if (cumulative) level.cumQty else level.qty
    val frac by animateFloatAsState(
        (shown / maxQ).toFloat().coerceIn(ObBook.MIN_BAR, 1f),
        tween(300),
        label = "bar",
    )
    val align = if (mirrored) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.surfaceVariant)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .align(align)
                .fillMaxWidth(frac)
                .fillMaxHeight()
                .background(
                    if (level.isWall) Warn.copy(alpha = wallAlpha)
                    else color.copy(alpha = 0.35f),
                ),
        )
        Text(
            "${fmtPrice(level.price)}  ${ObBook.fmtQty(shown)}",
            color = if (level.isWall) Warn else scheme.onSurface,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(align)
                .padding(horizontal = 4.dp),
        )
    }
}
