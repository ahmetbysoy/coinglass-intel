package com.coinglass.intel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutcomeDao {
    @Insert
    suspend fun insert(row: OutcomeEntity): Long

    @Update
    suspend fun update(row: OutcomeEntity)

    @Query("SELECT * FROM outcomes WHERE symbol = :symbol ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(symbol: String, limit: Int = 120): List<OutcomeEntity>

    @Query("SELECT * FROM outcomes ORDER BY ts DESC LIMIT :limit")
    fun observe(limit: Int = 200): Flow<List<OutcomeEntity>>

    @Query("SELECT * FROM outcomes WHERE settled15 = 0 OR settled5 = 0 OR settled1h = 0")
    suspend fun unsettled(): List<OutcomeEntity>

    @Query("SELECT * FROM outcomes WHERE symbol = :symbol ORDER BY ts DESC LIMIT 1")
    suspend fun last(symbol: String): OutcomeEntity?

    @Query("SELECT * FROM outcomes WHERE settled15 = 1 ORDER BY ts DESC LIMIT :limit")
    suspend fun settled15(limit: Int = 400): List<OutcomeEntity>
}
