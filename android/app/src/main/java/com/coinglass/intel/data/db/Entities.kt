package com.coinglass.intel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchEntity(
    @PrimaryKey val symbol: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "score_snap")
data class ScoreSnapEntity(
    @PrimaryKey val symbol: String,
    val price: Double,
    val score: Double,
    val direction: String,
    val sl: Double,
    val tp: Double,
    val coverage: Double,
    val updatedAt: Long,
    val candles1hJson: String = "[]",
    val candles4hJson: String = "[]",
)
