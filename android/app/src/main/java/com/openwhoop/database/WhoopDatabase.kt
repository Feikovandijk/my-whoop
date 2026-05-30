package com.openwhoop.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceEntity::class,
        HrSampleEntity::class,
        RrIntervalEntity::class,
        EventEntity::class,
        BatteryEntity::class,
        RawBatchEntity::class,
        CursorEntity::class,
        Spo2SampleEntity::class,
        SkinTempSampleEntity::class,
        RespSampleEntity::class,
        GravitySampleEntity::class,
        SleepSessionEntity::class,
        DailyMetricEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WhoopDatabase : RoomDatabase() {
    abstract fun whoopDao(): WhoopDao

    companion object {
        @Volatile
        private var INSTANCE: WhoopDatabase? = null

        fun getDatabase(context: Context): WhoopDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WhoopDatabase::class.java,
                    "openwhoop_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
