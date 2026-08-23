package com.coinglass.intel.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coinglass.intel.IntelApp
import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ScoreWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as IntelApp
        val db = AppDb.get(applicationContext)
        val settings = SettingsStore(applicationContext).flow.first()
        if (settings.serviceEnabled) return Result.success()
        val coord = ScanCoordinator(app.restClient, db)
        coord.scan(
            notify = settings.notificationsEnabled,
            ctx = applicationContext,
            minAbs = settings.scoreAlertAbs,
        )
        coord.discover(
            notify = settings.opportunityNotify && settings.notificationsEnabled,
            ctx = applicationContext,
        )
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
