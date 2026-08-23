package com.coinglass.intel.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coinglass.intel.IntelApp
import com.coinglass.intel.alert.AlertNotifier
import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ScoreWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as IntelApp
        val db = AppDb.get(applicationContext)
        val settings = SettingsStore(applicationContext).flow.first()
        if (settings.serviceEnabled) return Result.success()
        WatchlistScanner(app.restClient, db).scanAll()
        if (settings.notificationsEnabled) {
            val snaps = db.snap().all()
            for (s in snaps) {
                if (abs(s.score) >= settings.scoreAlertAbs) {
                    AlertNotifier.scoreAlert(applicationContext, s.symbol, s.score, s.direction, s.price)
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val NAME = "watchlist-score"
        fun enqueue(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ScoreWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.UPDATE, req,
            )
        }
    }
}
