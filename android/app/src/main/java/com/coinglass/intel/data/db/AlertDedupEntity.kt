package com.coinglass.intel.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "alert_dedup")
data class AlertDedupEntity(
    @PrimaryKey val symbol: String,
    val lastScore: Double,
    val lastTs: Long,
)

@Dao
interface AlertDedupDao {
    @Query("SELECT * FROM alert_dedup WHERE symbol = :symbol")
    suspend fun get(symbol: String): AlertDedupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AlertDedupEntity)
}
