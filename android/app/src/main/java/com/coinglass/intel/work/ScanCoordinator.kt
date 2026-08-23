package com.coinglass.intel.work

import android.content.Context
import com.coinglass.intel.alert.AlertNotifier
import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.db.ScoreSnapEntity
import okhttp3.OkHttpClient
import kotlin.math.abs

/** Single scan+notify path for FGS and WorkManager. */
class ScanCoordinator(
    restClient: OkHttpClient,
    private val db: AppDb,
) {
    private val scanner = WatchlistScanner(restClient, db)
    private val last = mutableMapOf<String, Pair<Long, Double>>()

    suspend fun scan(notify: Boolean, ctx: Context, minAbs: Double): List<ScoreSnapEntity> {
        val snaps = scanner.scanAll()
        if (!notify) return snaps
        val now = System.currentTimeMillis()
        for (s in snaps) {
            if (abs(s.score) < minAbs) continue
            val prev = last[s.symbol]
            if (prev != null) {
                val (ts, old) = prev
                if (now - ts < 10 * 60_000L && abs(s.score - old) < 8.0) continue
            }
            last[s.symbol] = now to s.score
            val prio = s.spoof < 40 && s.risk < 50 && abs(s.netRr) >= 1.0
            AlertNotifier.scoreAlert(ctx, s.symbol, s.score, s.direction, s.price, priority = prio)
        }
        return snaps
    }
}
