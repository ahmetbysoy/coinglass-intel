package com.coinglass.intel.data.alarm

import android.content.Context
import com.coinglass.intel.alert.AlertNotifier
import com.coinglass.intel.data.db.AlarmEntity
import com.coinglass.intel.data.db.AlarmFireEntity
import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.db.ScoreSnapEntity
import com.coinglass.intel.data.db.toEntity
import com.coinglass.intel.domain.AlarmEngine
import com.coinglass.intel.domain.AlarmHit
import com.coinglass.intel.domain.AlarmKind
import com.coinglass.intel.domain.AlarmOp
import com.coinglass.intel.domain.AlarmQuote
import kotlinx.coroutines.flow.Flow

class AlarmBook(private val db: AppDb) {
    fun observe(): Flow<List<AlarmEntity>> = db.alarm().observe()

    suspend fun add(rawSymbol: String, kind: AlarmKind, op: AlarmOp, threshold: Double, label: String = ""): Long? {
        val spec = AlarmEngine.draft(rawSymbol, kind, op, threshold, label) ?: return null
        return db.alarm().insert(spec.toEntity())
    }

    suspend fun setEnabled(id: Long, on: Boolean) {
        if (id <= 0L) return
        db.alarm().setEnabled(id, on)
    }

    suspend fun delete(id: Long) {
        if (id <= 0L) return
        db.alarm().delete(id)
        db.alarmFire().delete(id)
    }

    /** `quotes` = watchlist snaps (+ extras). Alarms always load from Room — never pass the alarm table here. */
    suspend fun evaluate(quotes: List<AlarmQuote>, live: AlarmQuote?, now: Long): List<AlarmHit> {
        val alarms = db.alarm().all().mapNotNull { it.toSpec() }
        val last = db.alarmFire().all().associate { it.alarmId to it.lastTs }
        return AlarmEngine.check(alarms, AlarmEngine.mergeLive(quotes, live), last, now)
    }

    suspend fun markFired(hits: List<AlarmHit>, now: Long) {
        for (h in hits) {
            db.alarmFire().upsert(AlarmFireEntity(h.alarm.id, now, h.value))
        }
    }

    suspend fun notifyHits(ctx: Context, hits: List<AlarmHit>, now: Long) {
        if (hits.isEmpty()) return
        markFired(hits, now)
        for (h in hits) AlertNotifier.alarmHit(ctx, h)
    }

    companion object {
        fun quoteOf(s: ScoreSnapEntity): AlarmQuote =
            AlarmQuote(s.symbol, s.price, s.score, s.funding)
    }
}
