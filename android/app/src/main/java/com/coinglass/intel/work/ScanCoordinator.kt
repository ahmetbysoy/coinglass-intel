package com.coinglass.intel.work

import android.content.Context
import com.coinglass.intel.alert.AlertNotifier
import com.coinglass.intel.data.db.AlertDedupEntity
import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.db.DiscoverySnapEntity
import com.coinglass.intel.data.db.ScoreSnapEntity
import com.coinglass.intel.domain.AlertDedup
import com.coinglass.intel.domain.DiscoveryPick
import okhttp3.OkHttpClient
import kotlin.math.abs

/** Single scan+notify path. Dedup lives in Room so Worker restarts do not spam. */
class ScanCoordinator(
    restClient: OkHttpClient,
    private val db: AppDb,
) {
    private val scanner = WatchlistScanner(restClient, db)
    private val discovery = MarketDiscovery(restClient, db)

    suspend fun scan(notify: Boolean, ctx: Context, minAbs: Double): List<ScoreSnapEntity> {
        val snaps = scanner.scanAll()
        runCatching { com.coinglass.intel.widget.IntelWidget.refresh(ctx) }
        if (!notify) return snaps
        val now = System.currentTimeMillis()
        for (s in snaps) {
            if (abs(s.score) < minAbs) continue
            val prev = db.dedup().get(s.symbol)
            if (AlertDedup.shouldSkip(prev?.lastTs, prev?.lastScore, now, s.score)) continue
            db.dedup().upsert(AlertDedupEntity(s.symbol, s.score, now))
            val prio = s.spoof < 40 && s.risk < 50 && abs(s.netRr) >= 1.0
            AlertNotifier.scoreAlert(ctx, s.symbol, s.score, s.direction, s.price, priority = prio)
        }
        return snaps
    }

    suspend fun discover(notify: Boolean, ctx: Context): List<DiscoverySnapEntity> {
        val snaps = discovery.discover()
        if (!notify) return snaps
        val now = System.currentTimeMillis()
        for (s in snaps) {
            if (!DiscoveryPick.isOpportunity(s.grade, s.spoof, s.netRr, s.coverage)) continue
            val prev = db.dedup().get(s.symbol)
            if (AlertDedup.shouldSkip(prev?.lastTs, prev?.lastScore, now, s.score)) continue
            db.dedup().upsert(AlertDedupEntity(s.symbol, s.score, now))
            AlertNotifier.opportunityAlert(ctx, s.symbol, s.score, s.grade, s.price)
        }
        return snaps
    }
}
