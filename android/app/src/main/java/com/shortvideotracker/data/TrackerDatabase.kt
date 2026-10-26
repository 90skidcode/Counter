package com.shortvideotracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        PlatformEntity::class,
        VideoEventEntity::class,
        ActivityDailyEntity::class,
        WarningSentEntity::class,
        BlockTargetEntity::class,
        BlockEventEntity::class,
        OverrideEntity::class,
        DiagnosticCounterEntity::class,
        SettingEntity::class,
    ],
    version = TrackerDatabase.VERSION,
    exportSchema = true,
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun platforms(): PlatformDao
    abstract fun videoEvents(): VideoEventDao
    abstract fun activity(): ActivityDao
    abstract fun warnings(): WarningDao
    abstract fun blocks(): BlockDao
    abstract fun diagnostics(): DiagnosticDao
    abstract fun settings(): SettingDao

    companion object {
        const val VERSION = 1
        private const val NAME = "short_video_tracker.db"

        /**
         * Explicit migrations only. Destructive fallback is never enabled: losing a user's
         * history silently is worse than failing loudly in development.
         * Add new migrations here and export the schema (android/app/schemas) for each version.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        @Volatile private var instance: TrackerDatabase? = null

        fun get(context: Context): TrackerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TrackerDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
                .also { instance = it }
        }
    }
}
