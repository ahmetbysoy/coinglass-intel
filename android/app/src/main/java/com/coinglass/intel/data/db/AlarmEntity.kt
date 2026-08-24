package com.coinglass.intel.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.coinglass.intel.domain.AlarmSpec
import com.coinglass.intel.domain.AlarmEngine
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "alarm")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val kind: String,
    val op: String,
    val threshold: Double,
    val enabled: Boolean = true,
    val label: String = "",
) {
    fun toSpec(): AlarmSpec? {
        val k = AlarmEngine.kindOf(kind) ?: return null
        val o = AlarmEngine.opOf(op) ?: return null
        if (id <= 0L || symbol.isBlank()) return null
        return AlarmSpec(id, symbol, k, o, threshold, enabled, label)
    }
}

@Entity(tableName = "alarm_fire")
data class AlarmFireEntity(
    @PrimaryKey val alarmId: Long,
    val lastTs: Long,
    val lastValue: Double = 0.0,
)

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm ORDER BY id DESC")
    fun observe(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarm")
    suspend fun all(): List<AlarmEntity>

    @Query("SELECT * FROM alarm WHERE enabled = 1")
    suspend fun enabled(): List<AlarmEntity>

    @Insert
    suspend fun insert(row: AlarmEntity): Long

    @Query("UPDATE alarm SET enabled = :on WHERE id = :id")
    suspend fun setEnabled(id: Long, on: Boolean)

    @Query("DELETE FROM alarm WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AlarmFireDao {
    @Query("SELECT * FROM alarm_fire")
    suspend fun all(): List<AlarmFireEntity>

    @Query("SELECT * FROM alarm_fire WHERE alarmId = :id")
    suspend fun get(id: Long): AlarmFireEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AlarmFireEntity)

    @Query("DELETE FROM alarm_fire WHERE alarmId = :id")
    suspend fun delete(id: Long)
}

fun AlarmSpec.toEntity(): AlarmEntity = AlarmEntity(
    id = if (id > 0) id else 0,
    symbol = symbol,
    kind = AlarmEngine.kindKey(kind),
    op = AlarmEngine.opKey(op),
    threshold = threshold,
    enabled = enabled,
    label = label,
)
