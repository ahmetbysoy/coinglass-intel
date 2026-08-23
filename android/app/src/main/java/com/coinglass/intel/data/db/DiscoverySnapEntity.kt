package com.coinglass.intel.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Keşif sonucu. Watchlist'e yazılmaz. */
@Entity(tableName = "discovery_snap")
data class DiscoverySnapEntity(
    @PrimaryKey val symbol: String,
    val price: Double,
    val score: Double,
    val grade: String,
    val spoof: Int,
    val netRr: Double,
    val vol24: Double,
    val coverage: Double,
    val updatedAt: Long,
    val direction: String = "",
    val candles1hJson: String = "[]",
)

@Dao
interface DiscoveryDao {
    @Query("SELECT * FROM discovery_snap")
    fun observe(): Flow<List<DiscoverySnapEntity>>

    @Query("SELECT * FROM discovery_snap")
    suspend fun all(): List<DiscoverySnapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DiscoverySnapEntity)

    @Query("DELETE FROM discovery_snap WHERE symbol = :symbol")
    suspend fun delete(symbol: String)

    @Query("DELETE FROM discovery_snap")
    suspend fun clear()
}
