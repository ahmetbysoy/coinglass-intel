package com.coinglass.intel.alert

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.coinglass.intel.IntelApp
import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.settings.SettingsStore
import com.coinglass.intel.work.ScanCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

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
        val coord = ScanCoordinator(app.restClient, db)
        val book = com.coinglass.intel.data.alarm.AlarmBook(db)
        val scanner = com.coinglass.intel.work.WatchlistScanner(app.restClient, db)
        var turn = 0
        while (scope.isActive) {
            val cfg = settings.flow.first()
            val watch = db.watch().all()
            startForeground(AlertNotifier.ID_FG, AlertNotifier.foreground(this, watch.size))
            val snaps = if (cfg.notificationsEnabled && watch.isNotEmpty()) {
                coord.scan(notify = true, ctx = this, minAbs = cfg.scoreAlertAbs)
            } else {
                emptyList()
            }
            if (cfg.notificationsEnabled) {
                val quotes = snaps.map { com.coinglass.intel.data.alarm.AlarmBook.quoteOf(it) }.toMutableList()
                val have = quotes.map { it.symbol }.toSet()
                val extra = db.alarm().enabled().map { it.symbol }.distinct().filter { it !in have }
                for ((i, sym) in extra.withIndex()) {
                    if (i > 0) delay(200)
                    scanner.quote(sym)?.let { quotes += it }
                }
                val now = System.currentTimeMillis()
                val hits = book.evaluate(quotes, live = null, now)
                book.notifyHits(this, hits, now)
            }
            if (turn % 5 == 0) {
                coord.discover(
                    notify = cfg.opportunityNotify && cfg.notificationsEnabled,
                    ctx = this,
                )
            }
            turn++
            delay(30_000)
        }
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
