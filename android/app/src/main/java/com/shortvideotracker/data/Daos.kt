package com.shortvideotracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

data class PlatformDayCount(
    val dayKey: String,
    val platformId: String,
    val confidenceLevel: String,
    val count: Int,
)

data class PlatformDayActivity(
    val dayKey: String,
    val platformId: String,
    val source: String,
    val activeMillis: Long,
)

data class RecentEventRow(
    val platformId: String,
    val eventTimestamp: Long,
    val confidenceLevel: String,
    val evidenceType: String,
    val sourceDetector: String,
    val detectorVersion: Int,
)

@Dao
interface PlatformDao {
    @Query("SELECT * FROM platforms ORDER BY id")
    suspend fun all(): List<PlatformEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(platforms: List<PlatformEntity>)

    @Query(
        """UPDATE platforms SET display_name = :displayName, package_name = :packageName,
           platform_type = :platformType, detector_type = :detectorType,
           capability_level = :capabilityLevel, detector_version = :detectorVersion, updated_at = :now
           WHERE id = :id""",
    )
    suspend fun updateDefinition(
        id: String,
        displayName: String,
        packageName: String?,
        platformType: String,
        detectorType: String,
        capabilityLevel: String,
        detectorVersion: Int,
        now: Long,
    )

    @Query("UPDATE platforms SET enabled = :enabled, updated_at = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Query("UPDATE platforms SET last_detected_at = :at WHERE id = :id AND (last_detected_at IS NULL OR last_detected_at < :at)")
    suspend fun markDetected(id: String, at: Long)

    @Query("UPDATE platforms SET last_detected_at = NULL")
    suspend fun clearLastDetected()
}

@Dao
interface VideoEventDao {
    /** Returns -1 when the deduplication key already exists, so duplicates never count twice. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(event: VideoEventEntity): Long

    @Query(
        """SELECT day_key AS dayKey, platform_id AS platformId, confidence_level AS confidenceLevel, COUNT(*) AS count
           FROM video_events
           WHERE count_status = 'counted' AND day_key BETWEEN :fromDay AND :toDay
           GROUP BY day_key, platform_id, confidence_level""",
    )
    suspend fun countsByDay(fromDay: String, toDay: String): List<PlatformDayCount>

    @Query(
        """SELECT platform_id AS platformId, event_timestamp AS eventTimestamp, confidence_level AS confidenceLevel,
                  evidence_type AS evidenceType, source_detector AS sourceDetector, detector_version AS detectorVersion
           FROM video_events ORDER BY event_timestamp DESC LIMIT :limit""",
    )
    suspend fun recent(limit: Int): List<RecentEventRow>

    @Query("SELECT COUNT(*) FROM video_events")
    suspend fun countAll(): Int

    @Query("SELECT MIN(day_key) FROM video_events")
    suspend fun firstDay(): String?

    @Query("DELETE FROM video_events WHERE day_key = :dayKey")
    suspend fun deleteDay(dayKey: String)

    @Query("DELETE FROM video_events WHERE day_key < :beforeDay")
    suspend fun deleteBefore(beforeDay: String)

    @Query("DELETE FROM video_events")
    suspend fun deleteAll()
}

@Dao
interface ActivityDao {
    @Upsert
    suspend fun upsert(row: ActivityDailyEntity)

    @Query("SELECT * FROM activity_daily WHERE day_key = :dayKey AND platform_id = :platformId AND source = :source")
    suspend fun get(dayKey: String, platformId: String, source: String): ActivityDailyEntity?

    @Query(
        """SELECT day_key AS dayKey, platform_id AS platformId, source, active_millis AS activeMillis
           FROM activity_daily WHERE day_key BETWEEN :fromDay AND :toDay""",
    )
    suspend fun byDay(fromDay: String, toDay: String): List<PlatformDayActivity>

    @Query("DELETE FROM activity_daily WHERE day_key = :dayKey")
    suspend fun deleteDay(dayKey: String)

    @Query("DELETE FROM activity_daily WHERE day_key < :beforeDay")
    suspend fun deleteBefore(beforeDay: String)

    @Query("DELETE FROM activity_daily")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM activity_daily")
    suspend fun countAll(): Int
}

@Dao
interface WarningDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: WarningSentEntity): Long

    @Query("SELECT id FROM warnings_sent WHERE day_key = :dayKey")
    suspend fun idsForDay(dayKey: String): List<String>

    @Query("DELETE FROM warnings_sent WHERE day_key = :dayKey")
    suspend fun deleteDay(dayKey: String)

    @Query("DELETE FROM warnings_sent WHERE day_key < :beforeDay")
    suspend fun deleteBefore(beforeDay: String)

    @Query("DELETE FROM warnings_sent")
    suspend fun deleteAll()
}

@Dao
interface BlockDao {
    @Query("SELECT * FROM block_targets ORDER BY kind, value")
    suspend fun targets(): List<BlockTargetEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTarget(target: BlockTargetEntity): Long

    @Query("DELETE FROM block_targets WHERE kind = :kind AND value = :value")
    suspend fun deleteTarget(kind: String, value: String)

    @Query("DELETE FROM block_targets")
    suspend fun deleteAllTargets()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: BlockEventEntity): Long

    @Query(
        """UPDATE block_events SET occurrences = occurrences + 1, last_at = :at
           WHERE day_key = :dayKey AND target_kind = :kind AND target_value = :value AND event_type = :eventType""",
    )
    suspend fun bumpEvent(dayKey: String, kind: String, value: String, eventType: String, at: Long)

    @Query("SELECT * FROM block_events WHERE day_key = :dayKey ORDER BY first_at")
    suspend fun eventsForDay(dayKey: String): List<BlockEventEntity>

    @Query("DELETE FROM block_events WHERE day_key = :dayKey")
    suspend fun deleteEventsForDay(dayKey: String)

    @Query("DELETE FROM block_events WHERE day_key < :beforeDay")
    suspend fun deleteEventsBefore(beforeDay: String)

    @Query("DELETE FROM block_events")
    suspend fun deleteAllEvents()

    @Insert
    suspend fun insertOverride(row: OverrideEntity): Long

    @Query("SELECT * FROM overrides WHERE day_key = :dayKey ORDER BY started_at")
    suspend fun overridesForDay(dayKey: String): List<OverrideEntity>

    @Query("UPDATE overrides SET cancelled_at = :at WHERE day_key = :dayKey AND cancelled_at IS NULL")
    suspend fun cancelOverrides(dayKey: String, at: Long)

    @Query("DELETE FROM overrides WHERE day_key < :beforeDay")
    suspend fun deleteOverridesBefore(beforeDay: String)

    @Query("DELETE FROM overrides WHERE day_key = :dayKey")
    suspend fun deleteOverridesForDay(dayKey: String)

    @Query("DELETE FROM overrides")
    suspend fun deleteAllOverrides()
}

@Dao
interface DiagnosticDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: DiagnosticCounterEntity): Long

    @Query(
        """UPDATE diagnostic_counters SET value = value + :delta, updated_at = :at
           WHERE day_key = :dayKey AND platform_id = :platformId AND metric = :metric""",
    )
    suspend fun add(dayKey: String, platformId: String, metric: String, delta: Long, at: Long)

    @Query("SELECT * FROM diagnostic_counters WHERE day_key = :dayKey")
    suspend fun forDay(dayKey: String): List<DiagnosticCounterEntity>

    @Query("DELETE FROM diagnostic_counters WHERE day_key = :dayKey")
    suspend fun deleteDay(dayKey: String)

    @Query("DELETE FROM diagnostic_counters WHERE day_key < :beforeDay")
    suspend fun deleteBefore(beforeDay: String)

    @Query("DELETE FROM diagnostic_counters")
    suspend fun deleteAll()
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
}
