package com.shortvideotracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Local-only schema. Nothing here is ever transmitted off the device.
 *
 * Deliberately NOT stored anywhere: video titles, thumbnails, captions, creator names, page
 * contents, full URLs, messages, UI trees or screenshots. Video identifiers are stored only as
 * per-install salted hashes.
 */

@Entity(tableName = "platforms")
data class PlatformEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "package_name") val packageName: String?,
    @ColumnInfo(name = "platform_type") val platformType: String,
    val enabled: Boolean,
    @ColumnInfo(name = "detector_type") val detectorType: String,
    @ColumnInfo(name = "capability_level") val capabilityLevel: String,
    @ColumnInfo(name = "detector_version") val detectorVersion: Int,
    @ColumnInfo(name = "last_detected_at") val lastDetectedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "video_events",
    foreignKeys = [
        ForeignKey(entity = PlatformEntity::class, parentColumns = ["id"], childColumns = ["platform_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["deduplication_key"], unique = true),
        Index(value = ["day_key", "platform_id"]),
        Index(value = ["platform_id"]),
        Index(value = ["event_timestamp"]),
    ],
)
data class VideoEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "platform_id") val platformId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "event_timestamp") val eventTimestamp: Long,
    /** Tracking day (respects the reset time that was configured when the event happened). */
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "video_identifier_hash") val videoIdentifierHash: String?,
    @ColumnInfo(name = "deduplication_key") val deduplicationKey: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "confidence_level") val confidenceLevel: String,
    @ColumnInfo(name = "evidence_type") val evidenceType: String,
    @ColumnInfo(name = "count_status") val countStatus: String,
    @ColumnInfo(name = "source_detector") val sourceDetector: String,
    @ColumnInfo(name = "detector_version") val detectorVersion: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    companion object {
        const val STATUS_COUNTED = "counted"
    }
}

/** Foreground time per platform per day (Usage Access for apps, own timer for web sites). */
@Entity(tableName = "activity_daily", primaryKeys = ["day_key", "platform_id", "source"])
data class ActivityDailyEntity(
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "platform_id") val platformId: String,
    /** "usage_stats" (whole-app foreground time) or "web_site_timer" (short-video sites only). */
    val source: String,
    @ColumnInfo(name = "active_millis") val activeMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        const val SOURCE_USAGE_STATS = "usage_stats"
        const val SOURCE_WEB_TIMER = "web_site_timer"
    }
}

@Entity(tableName = "warnings_sent", indices = [Index(value = ["day_key"])])
data class WarningSentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val scope: String,
    val kind: String,
    val threshold: Int,
    @ColumnInfo(name = "notified") val notified: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "block_targets", indices = [Index(value = ["kind", "value"], unique = true)])
data class BlockTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val value: String,
    @ColumnInfo(name = "platform_id") val platformId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Compact, auditable block log: one row per (day, target, event type) with an occurrence count. */
@Entity(
    tableName = "block_events",
    indices = [Index(value = ["day_key", "target_kind", "target_value", "event_type"], unique = true)],
)
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "target_kind") val targetKind: String,
    @ColumnInfo(name = "target_value") val targetValue: String,
    /** "activated" or "shown" (the user opened a blocked target). */
    @ColumnInfo(name = "event_type") val eventType: String,
    val reason: String,
    val occurrences: Int,
    @ColumnInfo(name = "first_at") val firstAt: Long,
    @ColumnInfo(name = "last_at") val lastAt: Long,
)

@Entity(tableName = "overrides", indices = [Index(value = ["day_key"])])
data class OverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "day_key") val dayKey: String,
    /** "temporary" or "pause_for_day". */
    val kind: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ends_at") val endsAt: Long?,
    @ColumnInfo(name = "cancelled_at") val cancelledAt: Long?,
) {
    companion object {
        const val KIND_TEMPORARY = "temporary"
        const val KIND_PAUSE_FOR_DAY = "pause_for_day"
    }
}

@Entity(tableName = "diagnostic_counters", primaryKeys = ["day_key", "platform_id", "metric"])
data class DiagnosticCounterEntity(
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "platform_id") val platformId: String,
    val metric: String,
    val value: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
