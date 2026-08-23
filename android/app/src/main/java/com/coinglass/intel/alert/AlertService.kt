package com.coinglass.intel.alert

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.coinglass.intel.IntelApp
import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.settings.SettingsStore
import com.coinglass.intel.work.WatchlistScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class AlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val lastAlert = mutableMapOf<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AlertNotifier.ensureChannels(this)
        startForeground(AlertNotifier.ID_FG, AlertNotifier.foreground(this, 0))
        loop = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        val app = application as IntelApp
        val db = AppDb.get(this)
        val settings = SettingsStore(this)
        val scanner = WatchlistScanner(app.restClient, db)
        while (scope.isActive) {
            val cfg = settings.flow.first()
            val watch = db.watch().all()
            startForeground(AlertNotifier.ID_FG, AlertNotifier.foreground(this, watch.size))
            if (cfg.notificationsEnabled && watch.isNotEmpty()) {
                val snaps = scanner.scanAll()
                val now = System.currentTimeMillis()
                for (s in snaps) {
                    if (abs(s.score) < cfg.scoreAlertAbs) continue
                    val prev = lastAlert[s.symbol] ?: 0L
                    if (now - prev < 10 * 60_000L) continue
                    lastAlert[s.symbol] = now
                    AlertNotifier.scoreAlert(this, s.symbol, s.score, s.direction, s.price)
                }
            }
            delay(30_000)
        }
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
