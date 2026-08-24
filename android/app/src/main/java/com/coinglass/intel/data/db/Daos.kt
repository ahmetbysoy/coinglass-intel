package com.coinglass.intel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observe(): Flow<List<WatchEntity>>

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    suspend fun all(): List<WatchEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE symbol = :symbol)")
    fun observeHas(symbol: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE symbol = :symbol)")
    suspend fun has(symbol: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: WatchEntity)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun delete(symbol: String)

    /** Single transaction — double-tap cannot insert twice. true = added. */
    @Transaction
    suspend fun toggle(symbol: String): Boolean {
        if (symbol.isBlank()) return false
        if (has(symbol)) {
            delete(symbol)
            return false
        }
        upsert(WatchEntity(symbol))
        return true
    }
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
