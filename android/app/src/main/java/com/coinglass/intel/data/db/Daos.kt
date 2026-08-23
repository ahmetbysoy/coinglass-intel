package com.coinglass.intel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observe(): Flow<List<WatchEntity>>

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    suspend fun all(): List<WatchEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE symbol = :symbol)")
    fun observeHas(symbol: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: WatchEntity)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun delete(symbol: String)
}

@Dao
interface SnapDao {
    @Query("SELECT * FROM score_snap")
    fun observe(): Flow<List<ScoreSnapEntity>>

    @Query("SELECT * FROM score_snap")
    suspend fun all(): List<ScoreSnapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ScoreSnapEntity)

    @Query("DELETE FROM score_snap WHERE symbol = :symbol")
    suspend fun delete(symbol: String)
}
