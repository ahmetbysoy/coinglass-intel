package com.coinglass.intel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinglass.intel.ui.AppViewModel
import com.coinglass.intel.ui.IntelScreen
import com.coinglass.intel.ui.PerformanceScreen
import com.coinglass.intel.ui.ScannerScreen
import com.coinglass.intel.ui.SettingsScreen
import com.coinglass.intel.ui.theme.CoinGlassTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("symbol")?.let { vm.submit(it) }
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val tab by vm.tab.collectAsStateWithLifecycle()
            val snaps by vm.snaps.collectAsStateWithLifecycle()
            val scanning by vm.scanning.collectAsStateWithLifecycle()
            val now by vm.now.collectAsStateWithLifecycle()
            val compare by vm.compare.collectAsStateWithLifecycle()
            val outcomes by vm.outcomes.collectAsStateWithLifecycle()
            CoinGlassTheme(dark = settings.darkTheme) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { vm.selectTab(0) },
                                icon = { Icon(painterResource(R.drawable.ic_nav_live), contentDescription = "canli") },
                                label = { Text("Canli") },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { vm.selectTab(1) },
                                icon = { Icon(painterResource(R.drawable.ic_nav_scan), contentDescription = "tarayici") },
                                label = { Text("Tarayici") },
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { vm.selectTab(2) },
                                icon = { Icon(painterResource(R.drawable.ic_nav_hit), contentDescription = "isabet") },
                                label = { Text("Isabet") },
                            )
                            NavigationBarItem(
                                selected = tab == 3,
                                onClick = { vm.selectTab(3) },
                                icon = { Icon(painterResource(R.drawable.ic_nav_set), contentDescription = "ayarlar") },
                                label = { Text("Ayarlar") },
                            )
                        }
                    },
                ) { pad ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(pad)) {
                        when (tab) {
                            1 -> ScannerScreen(
                                snaps = snaps,
                                scanning = scanning,
                                staleSec = settings.staleSeconds,
                                now = now,
                                compare = compare,
                                onOpen = { sym ->
                                    vm.submit(sym)
                                    vm.selectTab(0)
                                },
                                onRemove = { vm.removeWatch(it) },
                                onRefresh = { vm.refreshScanner() },
                                onCompare = { vm.toggleCompare(it) },
                            )
                            2 -> PerformanceScreen(outcomes)
                            3 -> SettingsScreen(
                                s = settings,
                                onChange = { vm.updateSettings(it) },
                                onToggleService = { vm.toggleService(it) },
                            )
                            else -> IntelScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
