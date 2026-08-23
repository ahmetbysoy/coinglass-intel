package com.coinglass.intel.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "outcomes", indices = [Index("symbol"), Index("ts")])
data class OutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val ts: Long,
    val price: Double,
    val score: Double,
    val direction: String,
    val ob: Double,
    val tf: Double,
    val oi: Double,
    val funding: Double,
    val liq: Double,
    val vol: Double,
    val mom: Double,
    val px5: Double? = null,
    val win5: Boolean? = null,
    val settled5: Boolean = false,
    val px15: Double? = null,
    val win15: Boolean? = null,
    val settled15: Boolean = false,
    val px1h: Double? = null,
    val win1h: Boolean? = null,
    val settled1h: Boolean = false,
)
