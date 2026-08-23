package com.coinglass.intel.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinglass.intel.data.settings.UserSettings
import com.coinglass.intel.ui.theme.Accent
import com.coinglass.intel.ui.theme.Bg
import com.coinglass.intel.ui.theme.Mute
import com.coinglass.intel.ui.theme.Text as Ink

@Composable
fun SettingsScreen(
    s: UserSettings,
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    onToggleService: (Boolean) -> Unit,
) {
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onChange { it.copy(notificationsEnabled = true) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text("AYARLAR", color = Accent, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

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

        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(24.dp))
        Text(
            "Esikler kullanici girdisidir. Sembol listesi hardcode degil — watchlist senin yazdiklarin.",
            color = Mute, fontSize = 12.sp,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, on: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = value, onCheckedChange = on)
    }
}

@Composable
private fun SliderRow(label: String, value: Double, min: Double, max: Double, fmt: String, on: (Double) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("$label  ${fmt.format(value)}", color = Ink, fontSize = 14.sp)
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { on(it.toDouble()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}
