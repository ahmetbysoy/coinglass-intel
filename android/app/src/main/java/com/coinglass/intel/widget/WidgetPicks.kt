package com.coinglass.intel.widget

import com.coinglass.intel.data.db.ScoreSnapEntity
import kotlin.math.abs

/** Watchlist snaps only. Never invents a coin. */
object WidgetPicks {
    fun top(snaps: List<ScoreSnapEntity>, n: Int = 3): List<ScoreSnapEntity> =
        snaps.sortedByDescending { abs(it.score) }.take(n)
}
