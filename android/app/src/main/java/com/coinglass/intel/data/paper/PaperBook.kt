package com.coinglass.intel.data.paper

import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.db.PaperTradeEntity
import com.coinglass.intel.domain.PaperEngine
import com.coinglass.intel.domain.model.V4Report

class PaperBook(private val db: AppDb) {
    suspend fun tryOpen(
        report: V4Report,
        source: String,
        now: Long = System.currentTimeMillis(),
    ): PaperTradeEntity? {
        val side = PaperEngine.sideOf(report.direction) ?: return null
        val last = db.paper().last(report.symbol)
        val open = db.paper().openFor(report.symbol)
        if (!PaperEngine.canOpen(
                enterOk = report.enterOk,
                grade = report.grade,
                symbol = report.symbol,
                lastOpenAt = last?.openedAt,
                now = now,
                hasOpen = open.isNotEmpty(),
            )
        ) return null
        if (report.price <= 0.0 || report.sl <= 0.0 || report.tp <= 0.0) return null
        val c = report.component
        val row = PaperTradeEntity(
            symbol = report.symbol,
            side = side,
            entry = report.price,
            sl = report.sl,
            tp = report.tp,
            openedAt = now,
            source = source,
            ob = c["ob"] ?: 0.0,
            tf = c["tf"] ?: 0.0,
            oi = c["oi"] ?: 0.0,
            funding = c["funding"] ?: 0.0,
            liq = c["liq"] ?: 0.0,
            vol = c["vol"] ?: 0.0,
            mom = c["mom"] ?: 0.0,
        )
        val id = db.paper().insert(row)
        return row.copy(id = id)
    }

    suspend fun settle(
        symbol: String,
        price: Double,
        now: Long = System.currentTimeMillis(),
    ): List<PaperTradeEntity> {
        if (price <= 0.0) return emptyList()
        val out = mutableListOf<PaperTradeEntity>()
        for (row in db.paper().openFor(symbol)) {
            val hit = PaperEngine.checkExit(
                side = row.side,
                entry = row.entry,
                sl = row.sl,
                tp = row.tp,
                px = price,
                openedAt = row.openedAt,
                now = now,
            ) ?: continue
            val next = row.copy(
                closedAt = now,
                exitPx = hit.px,
                win = hit.win,
                reason = hit.reason,
            )
            db.paper().update(next)
            out += next
        }
        return out
    }
}
