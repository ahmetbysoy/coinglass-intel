package com.coinglass.intel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchEntity::class,
        ScoreSnapEntity::class,
        OutcomeEntity::class,
        AlertDedupEntity::class,
        DiscoverySnapEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun watch(): WatchDao
    abstract fun snap(): SnapDao
    abstract fun outcome(): OutcomeDao
    abstract fun dedup(): AlertDedupDao
    abstract fun discovery(): DiscoveryDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "intel.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { inst = it }
        }
    }
}
