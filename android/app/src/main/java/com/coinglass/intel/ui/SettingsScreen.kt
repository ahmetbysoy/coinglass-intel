package com.coinglass.intel.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.R
import com.coinglass.intel.data.db.AlarmEntity
import com.coinglass.intel.data.settings.UserSettings
import com.coinglass.intel.domain.AlarmKind
import com.coinglass.intel.domain.AlarmOp
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space

@Composable
fun SettingsScreen(
    s: UserSettings,
    alarms: List<AlarmEntity> = emptyList(),
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    onToggleService: (Boolean) -> Unit,
    onAddAlarm: (String, AlarmKind, AlarmOp, Double, String) -> Unit = { _, _, _, _, _ -> },
    onToggleAlarm: (Long, Boolean) -> Unit = { _, _ -> },
    onDeleteAlarm: (Long) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onChange { it.copy(notificationsEnabled = true) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg),
    ) {
        Text("AYARLAR", color = scheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        Panel {
            Toggle("Bildirimler", s.notificationsEnabled) { on ->
                if (on && Build.VERSION.SDK_INT >= 33) {
                    perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onChange { it.copy(notificationsEnabled = on) }
            }
            Toggle("On plan servisi (sesli uyari)", s.serviceEnabled) { on ->
                onToggleService(on)
            }
            Toggle("Koyu tema", s.darkTheme) { on -> onChange { it.copy(darkTheme = on) } }
            Toggle("Turı tekrar göster", !s.onboardDone) { on ->
                onChange { it.copy(onboardDone = !on) }
            }
            Toggle("Fırsat bildirimi (keşif A/B)", s.opportunityNotify) { on ->
                onChange { it.copy(opportunityNotify = on) }
            }
            Toggle("Oto kağıt (A/B + GİRME yok)", s.autoPaper) { on ->
                onChange { it.copy(autoPaper = on) }
            }
        }
        Spacer(Modifier.height(Space.md))
        Panel {
            SliderRow("Skor esigi |s|", s.scoreAlertAbs, 5.0, 80.0, "%.0f") {
                onChange { c -> c.copy(scoreAlertAbs = it) }
            }
            SliderRow("Likidasyon esigi $", s.liqAlertUsd, 50_000.0, 2_000_000.0, "%.0f") {
                onChange { c -> c.copy(liqAlertUsd = it) }
            }
            SliderRow("Balina esigi $", s.whaleAlertUsd, 100_000.0, 5_000_000.0, "%.0f") {
                onChange { c -> c.copy(whaleAlertUsd = it) }
            }
            SliderRow("Bayat veri (sn)", s.staleSeconds.toDouble(), 5.0, 120.0, "%.0f") {
                onChange { c -> c.copy(staleSeconds = it.toInt()) }
            }
            SliderRow("Bakiye $", s.equityUsd, 100.0, 100_000.0, "%.0f") {
                onChange { c -> c.copy(equityUsd = it) }
            }
            SliderRow("Risk % / islem", s.riskPct, 0.25, 5.0, "%.2f") {
                onChange { c -> c.copy(riskPct = it) }
            }
        }
        Spacer(Modifier.height(Space.md))
        AlarmPanel(
            alarms = alarms,
            onAdd = onAddAlarm,
            onToggle = onToggleAlarm,
            onDelete = onDeleteAlarm,
        )
        Spacer(Modifier.height(Space.xl))
        Text(
            "Tema switch artık gerçek: açık = pastel zemin, skor yeşil/kırmızı pastelleşmez. Sembol listesi hardcode değil.",
            color = scheme.onSurfaceVariant, fontSize = 12.sp,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
        content = { content() },
    )
}

@Composable
private fun Toggle(label: String, value: Boolean, on: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = scheme.onSurface, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = value, onCheckedChange = on)
    }
}

@Composable
private fun SliderRow(label: String, value: Double, min: Double, max: Double, fmt: String, on: (Double) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = Space.sm)) {
        Text("$label  ${fmt.format(value)}", color = scheme.onSurface, fontSize = 14.sp)
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { on(it.toDouble()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}

@Composable
private fun AlarmPanel(
    alarms: List<AlarmEntity>,
    onAdd: (String, AlarmKind, AlarmOp, Double, String) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var symbol by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AlarmKind.PRICE) }
    var op by remember { mutableStateOf(AlarmOp.GTE) }
    var th by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    Panel {
        Text(stringResource(R.string.alarm_title), color = scheme.primary, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Text(stringResource(R.string.alarm_hint), color = scheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(Space.sm))
        OutlinedTextField(
            value = symbol,
            onValueChange = { symbol = it.uppercase() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.alarm_symbol)) },
        )
        Row(Modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            KindChip("fiyat", kind == AlarmKind.PRICE) { kind = AlarmKind.PRICE }
            KindChip("|skor|", kind == AlarmKind.SCORE) { kind = AlarmKind.SCORE }
            KindChip("|fund|", kind == AlarmKind.FUNDING) { kind = AlarmKind.FUNDING }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            KindChip("≥", op == AlarmOp.GTE) { op = AlarmOp.GTE }
            KindChip("≤", op == AlarmOp.LTE) { op = AlarmOp.LTE }
        }
        OutlinedTextField(
            value = th,
            onValueChange = { th = it },
            modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
            singleLine = true,
            label = { Text(stringResource(R.string.alarm_threshold)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
            singleLine = true,
            label = { Text(stringResource(R.string.alarm_label)) },
        )
        Text(
            stringResource(R.string.alarm_add),
            color = scheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = Space.sm)
                .clip(RoundedCornerShape(Radii.sm))
                .background(scheme.primary)
                .clickable {
                    val v = th.replace(",", ".").toDoubleOrNull()
                    if (v != null) {
                        onAdd(symbol, kind, op, v, label)
                        symbol = ""
                        th = ""
                        label = ""
                    }
                }
                .padding(horizontal = Space.md, vertical = Space.sm),
        )
        if (alarms.isEmpty()) {
            Text(
                stringResource(R.string.alarm_empty),
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = Space.md),
            )
        } else {
            Spacer(Modifier.height(Space.md))
            alarms.forEach { row ->
                AlarmRow(row, onToggle, onDelete)
            }
        }
    }
}

@Composable
private fun AlarmRow(
    row: AlarmEntity,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val spec = row.toSpec()
    val kind = when (spec?.kind) {
        AlarmKind.PRICE -> "fiyat"
        AlarmKind.SCORE -> "|skor|"
        AlarmKind.FUNDING -> "|fund|"
        null -> row.kind
    }
    val op = if (spec?.op == AlarmOp.LTE) "≤" else "≥"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.symbol + if (row.label.isNotBlank()) "  ·  ${row.label}" else "",
                color = scheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("$kind $op ${row.threshold}", color = scheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(checked = row.enabled, onCheckedChange = { onToggle(row.id, it) })
        Text(
            stringResource(R.string.alarm_delete),
            color = scheme.error,
            fontSize = 12.sp,
            modifier = Modifier
                .padding(start = Space.sm)
                .clickable { onDelete(row.id) }
                .padding(Space.xs),
        )
    }
}

@Composable
private fun KindChip(label: String, on: Boolean, click: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        color = if (on) scheme.onPrimary else scheme.onSurface,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(end = 6.dp)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(Radii.sm))
            .background(if (on) scheme.primary else scheme.surfaceVariant)
            .clickable(onClick = click)
            .padding(horizontal = Space.sm, vertical = Space.sm),
    )
}
