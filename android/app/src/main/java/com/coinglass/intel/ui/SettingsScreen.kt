package com.coinglass.intel.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.R
import com.coinglass.intel.data.db.AlarmEntity
import com.coinglass.intel.data.settings.UserSettings
import com.coinglass.intel.domain.AlarmKind
import com.coinglass.intel.domain.AlarmOp
import com.coinglass.intel.domain.PrefsFormat
import com.coinglass.intel.ui.theme.Radii
import com.coinglass.intel.ui.theme.Space
import java.util.Locale
import kotlin.math.roundToInt

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
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onChange { it.copy(notificationsEnabled = granted) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Spacer(Modifier.height(Space.sm))
        Text(
            stringResource(R.string.settings_title),
            color = scheme.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            fontSize = 13.sp,
        )

        SectionCard(Icons.Filled.Notifications, stringResource(R.string.section_general)) {
            Toggle(stringResource(R.string.pref_notifications), s.notificationsEnabled) { on ->
                if (on && Build.VERSION.SDK_INT >= 33) {
                    perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onChange { it.copy(notificationsEnabled = on) }
                }
            }
            Toggle(stringResource(R.string.pref_foreground_service), s.serviceEnabled, onToggleService)
            Toggle(stringResource(R.string.pref_opportunity_notify), s.opportunityNotify) { on ->
                onChange { it.copy(opportunityNotify = on) }
            }
            Toggle(stringResource(R.string.pref_auto_paper), s.autoPaper) { on ->
                onChange { it.copy(autoPaper = on) }
            }
        }

        SectionCard(Icons.Filled.Palette, stringResource(R.string.section_appearance)) {
            Toggle(stringResource(R.string.pref_dark_theme), s.darkTheme) { on ->
                onChange { it.copy(darkTheme = on) }
            }
            Toggle(stringResource(R.string.pref_show_tour), !s.onboardDone) { on ->
                onChange { it.copy(onboardDone = !on) }
            }
        }

        SectionCard(Icons.Filled.Tune, stringResource(R.string.section_thresholds)) {
            SliderRow(
                label = stringResource(R.string.pref_score_threshold),
                value = s.scoreAlertAbs,
                min = 5.0,
                max = 80.0,
                display = { PrefsFormat.fmt("%.0f", it) },
            ) { v -> onChange { it.copy(scoreAlertAbs = v) } }
            LogSliderRow(
                label = stringResource(R.string.pref_liq_threshold),
                value = s.liqAlertUsd,
                min = 50_000.0,
                max = 2_000_000.0,
            ) { v -> onChange { it.copy(liqAlertUsd = v) } }
            LogSliderRow(
                label = stringResource(R.string.pref_whale_threshold),
                value = s.whaleAlertUsd,
                min = 100_000.0,
                max = 5_000_000.0,
            ) { v -> onChange { it.copy(whaleAlertUsd = v) } }
            SliderRow(
                label = stringResource(R.string.pref_stale_seconds),
                value = s.staleSeconds.toDouble(),
                min = 5.0,
                max = 120.0,
                display = { "${it.roundToInt()} sn" },
            ) { v -> onChange { it.copy(staleSeconds = v.roundToInt()) } }
        }

        SectionCard(Icons.Filled.Tune, stringResource(R.string.section_risk)) {
            LogSliderRow(
                label = stringResource(R.string.pref_equity),
                value = s.equityUsd,
                min = 100.0,
                max = 100_000.0,
            ) { v -> onChange { it.copy(equityUsd = v) } }
            SliderRow(
                label = stringResource(R.string.pref_risk_pct),
                value = s.riskPct,
                min = 0.25,
                max = 5.0,
                display = { PrefsFormat.fmt("%.2f", it) + " %" },
            ) { v -> onChange { it.copy(riskPct = v) } }
            val riskUsd = s.equityUsd * s.riskPct / 100.0
            Text(
                stringResource(R.string.risk_summary, PrefsFormat.compactUsd(riskUsd)),
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }

        AlarmPanel(
            alarms = alarms,
            onAdd = onAddAlarm,
            onToggle = onToggleAlarm,
            onDelete = onDeleteAlarm,
        )
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = scheme.primary)
            Spacer(Modifier.width(Space.sm))
            Text(title, color = scheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(Space.sm))
        content()
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, on: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = scheme.onSurface, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = value, onCheckedChange = on)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Double,
    min: Double,
    max: Double,
    display: (Double) -> String,
    onCommit: (Double) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), color = scheme.onSurface, fontSize = 14.sp)
            Text(display(local.toDouble()), color = scheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Slider(
            value = local.coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local.toDouble()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}

@Composable
private fun LogSliderRow(
    label: String,
    value: Double,
    min: Double,
    max: Double,
    onCommit: (Double) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var t by remember(value) { mutableStateOf(PrefsFormat.logToLinear(value, min, max)) }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), color = scheme.onSurface, fontSize = 14.sp)
            Text(
                PrefsFormat.compactUsd(PrefsFormat.linearToLog(t, min, max)),
                color = scheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
        Slider(
            value = t,
            onValueChange = { t = it },
            onValueChangeFinished = { onCommit(PrefsFormat.linearToLog(t, min, max)) },
            valueRange = 0f..1f,
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
    var symbol by rememberSaveable { mutableStateOf("") }
    var kindName by rememberSaveable { mutableStateOf(AlarmKind.PRICE.name) }
    var opName by rememberSaveable { mutableStateOf(AlarmOp.GTE.name) }
    var th by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    val kind = runCatching { AlarmKind.valueOf(kindName) }.getOrDefault(AlarmKind.PRICE)
    val op = runCatching { AlarmOp.valueOf(opName) }.getOrDefault(AlarmOp.GTE)
    val thValue = th.replace(',', '.').toDoubleOrNull()
    val symbolError = showErrors && symbol.isBlank()
    val thError = showErrors && thValue == null
    val pending = alarms.firstOrNull { it.id == pendingDeleteId }

    SectionCard(Icons.Filled.NotificationsActive, stringResource(R.string.alarm_title)) {
        Text(stringResource(R.string.alarm_hint), color = scheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(Space.sm))
        OutlinedTextField(
            value = symbol,
            onValueChange = { symbol = it.uppercase(Locale.ROOT) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = symbolError,
            supportingText = if (symbolError) {
                { Text(stringResource(R.string.alarm_symbol_required)) }
            } else {
                null
            },
            label = { Text(stringResource(R.string.alarm_symbol)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            FilterChip(
                selected = kind == AlarmKind.PRICE,
                onClick = { kindName = AlarmKind.PRICE.name },
                label = { Text(stringResource(R.string.alarm_kind_price)) },
            )
            FilterChip(
                selected = kind == AlarmKind.SCORE,
                onClick = { kindName = AlarmKind.SCORE.name },
                label = { Text(stringResource(R.string.alarm_kind_score)) },
            )
            FilterChip(
                selected = kind == AlarmKind.FUNDING,
                onClick = { kindName = AlarmKind.FUNDING.name },
                label = { Text(stringResource(R.string.alarm_kind_funding)) },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            FilterChip(selected = op == AlarmOp.GTE, onClick = { opName = AlarmOp.GTE.name }, label = { Text("≥") })
            FilterChip(selected = op == AlarmOp.LTE, onClick = { opName = AlarmOp.LTE.name }, label = { Text("≤") })
        }
        OutlinedTextField(
            value = th,
            onValueChange = { th = it },
            modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
            singleLine = true,
            isError = thError,
            supportingText = if (thError) {
                { Text(stringResource(R.string.alarm_threshold_invalid)) }
            } else {
                null
            },
            label = { Text(stringResource(R.string.alarm_threshold)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
            singleLine = true,
            label = { Text(stringResource(R.string.alarm_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Button(
            onClick = {
                if (symbol.isBlank() || thValue == null) {
                    showErrors = true
                } else {
                    onAdd(symbol.trim(), kind, op, thValue, label.trim())
                    symbol = ""
                    th = ""
                    label = ""
                    showErrors = false
                }
            },
            modifier = Modifier.padding(top = Space.sm),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(Space.xs))
            Text(stringResource(R.string.alarm_add))
        }

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
                AlarmRow(row, onToggle) { pendingDeleteId = row.id }
            }
        }
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.alarm_delete_confirm_title)) },
            text = { Text(stringResource(R.string.alarm_delete_confirm_msg, pending.symbol)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(pending.id)
                    pendingDeleteId = null
                }) { Text(stringResource(R.string.alarm_delete), color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AlarmRow(
    row: AlarmEntity,
    onToggle: (Long, Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val spec = row.toSpec()
    val kindLabel = when (spec?.kind) {
        AlarmKind.PRICE -> stringResource(R.string.alarm_kind_price)
        AlarmKind.SCORE -> stringResource(R.string.alarm_kind_score)
        AlarmKind.FUNDING -> stringResource(R.string.alarm_kind_funding)
        null -> row.kind
    }
    val opLabel = if (spec?.op == AlarmOp.LTE) "≤" else "≥"
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.symbol + if (row.label.isNotBlank()) "  ·  ${row.label}" else "",
                color = scheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "$kindLabel $opLabel ${PrefsFormat.fmt("%.4g", row.threshold)}",
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Switch(checked = row.enabled, onCheckedChange = { onToggle(row.id, it) })
        IconButton(onClick = onDeleteRequest) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.alarm_delete),
                tint = scheme.error,
            )
        }
    }
}
