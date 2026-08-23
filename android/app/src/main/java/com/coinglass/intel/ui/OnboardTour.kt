package com.coinglass.intel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.ui.theme.Bear
import com.coinglass.intel.ui.theme.Bull
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import com.coinglass.intel.ui.theme.Warn

@Composable
fun OnboardTour(
    onSubmit: (String) -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var step by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.lg),
    ) {
        BrandMark()
        Spacer(Modifier.height(Space.md))
        when (step) {
            0 -> {
                Text("KARAR ASİSTANI", color = scheme.primary, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(
                    "7 kaynağı senin yerine okurum. Üstte tek cümle: LONG / SHORT / GİRME.",
                    color = scheme.onSurface, fontSize = 14.sp,
                )
                Spacer(Modifier.height(Space.sm))
                Text("CoinGlass rakam yığar. Ben 3 şeyi eleyip bağırırım.", color = scheme.onSurfaceVariant, fontSize = 12.sp)
            }
            1 -> {
                Text("GİRME NE ZAMAN?", color = Bear, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text("spoof ≥ 50  → sahte duvara SL koyma", color = scheme.onSurface, fontSize = 13.sp)
                Text("netRR < 1   → fee + funding yiyor", color = scheme.onSurface, fontSize = 13.sp)
                Text("coverage < %40 → veri eksik, skor yalan", color = scheme.onSurface, fontSize = 13.sp)
                Spacer(Modifier.height(Space.sm))
                Text("A/B/C/D notu bunlardan çıkar. Yeşil/kırmızı pastelleşmez.", color = scheme.onSurfaceVariant, fontSize = 12.sp)
            }
            else -> {
                Text("CANLI DENE", color = scheme.primary, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text("Sabit liste yok. Kendi pair’ini yaz — REST 600 mum + WS gelir.", color = scheme.onSurface, fontSize = 13.sp)
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("ör. herhangi bir USDT pair", color = scheme.onSurfaceVariant) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focus.clearFocus()
                        if (query.isNotBlank()) {
                            onSubmit(query)
                            onDone()
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = scheme.primary,
                        unfocusedBorderColor = scheme.outline,
                        focusedTextColor = scheme.onSurface,
                        unfocusedTextColor = scheme.onSurface,
                    ),
                    shape = RoundedCornerShape(Radii.md),
                )
            }
        }
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone) { Text("atla", color = scheme.onSurfaceVariant) }
            Spacer(Modifier.weight(1f))
            Text("${step + 1}/3", color = scheme.onSurfaceVariant, fontSize = 11.sp)
            TextButton(onClick = {
                if (step < 2) step += 1
                else if (query.isNotBlank()) {
                    onSubmit(query)
                    onDone()
                } else onDone()
            }) {
                Text(if (step < 2) "ileri" else "başla", color = scheme.primary, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun BrandMark() {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(36.dp)) {
            val w = size.width
            val hex = Path().apply {
                moveTo(w * 0.5f, 2f)
                lineTo(w * 0.92f, w * 0.25f)
                lineTo(w * 0.92f, w * 0.75f)
                lineTo(w * 0.5f, w - 2f)
                lineTo(w * 0.08f, w * 0.75f)
                lineTo(w * 0.08f, w * 0.25f)
                close()
            }
            drawPath(hex, scheme.primary, style = Stroke(width = 3f))
            drawLine(Bull, Offset(w * 0.32f, w * 0.72f), Offset(w * 0.32f, w * 0.48f), 4f)
            drawLine(scheme.primary, Offset(w * 0.5f, w * 0.72f), Offset(w * 0.5f, w * 0.32f), 4f)
            drawLine(Bear, Offset(w * 0.68f, w * 0.72f), Offset(w * 0.68f, w * 0.55f), 4f)
        }
        Spacer(Modifier.size(Space.sm))
        Column {
            Text("COINGLASS INTEL", color = scheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 12.sp)
            Text("karar · spoof · netRR", color = scheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}
