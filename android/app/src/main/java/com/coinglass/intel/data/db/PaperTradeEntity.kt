package com.coinglass.intel.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "paper_trade", indices = [Index("symbol"), Index("openedAt")])
data class PaperTradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val side: String,
    val entry: Double,
    val sl: Double,
    val tp: Double,
    val openedAt: Long,
    val closedAt: Long? = null,
    val exitPx: Double? = null,
    val win: Boolean? = null,
    val source: String = "manual",
    val reason: String = "",
    val ob: Double = 0.0,
    val tf: Double = 0.0,
    val oi: Double = 0.0,
    val funding: Double = 0.0,
    val liq: Double = 0.0,
    val vol: Double = 0.0,
    val mom: Double = 0.0,
)

@Dao
interface PaperDao {
    @Insert
    suspend fun insert(row: PaperTradeEntity): Long

    @Update
    suspend fun update(row: PaperTradeEntity)

    @Query("SELECT * FROM paper_trade ORDER BY openedAt DESC")
    fun observe(): Flow<List<PaperTradeEntity>>

    @Query("SELECT * FROM paper_trade WHERE closedAt IS NULL ORDER BY openedAt DESC")
    fun observeOpen(): Flow<List<PaperTradeEntity>>

    @Query("SELECT * FROM paper_trade WHERE symbol = :symbol AND closedAt IS NULL")
    suspend fun openFor(symbol: String): List<PaperTradeEntity>

    @Query("SELECT * FROM paper_trade WHERE symbol = :symbol ORDER BY openedAt DESC LIMIT 1")
    suspend fun last(symbol: String): PaperTradeEntity?

    @Query("SELECT * FROM paper_trade WHERE closedAt IS NOT NULL ORDER BY closedAt DESC LIMIT :limit")
    suspend fun settled(limit: Int = 200): List<PaperTradeEntity>
}
