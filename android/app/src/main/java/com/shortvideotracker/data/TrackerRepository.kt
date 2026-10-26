package com.shortvideotracker.data

import android.content.Context
import androidx.room.withTransaction
import com.shortvideotracker.core.engine.BlockTarget
import com.shortvideotracker.core.engine.BlockTargetKind
import com.shortvideotracker.core.engine.CountedVideoEvent
import com.shortvideotracker.core.engine.PlannedWarning
import com.shortvideotracker.core.engine.TrackerSettings
import com.shortvideotracker.core.model.PlatformRegistry
import org.json.JSONObject
import java.security.SecureRandom

/**
 * The single source of truth for everything the app knows. Native services and the React
 * Native UI both read through this repository; daily totals are always aggregated from
 * `video_events` rather than kept as a separate, drift-prone counter.
 */
class TrackerRepository private constructor(private val db: TrackerDatabase) {

    suspend fun seedPlatforms(now: Long) = db.withTransaction {
        db.platforms().insertIgnore(
            PlatformRegistry.ALL.map { p ->
                PlatformEntity(
                    id = p.id,
                    displayName = p.displayName,
                    packageName = p.packageNames.firstOrNull(),
                    platformType = p.platformType.code,
                    enabled = true,
                    detectorType = p.detectorType.code,
                    capabilityLevel = p.capabilityLevel.code,
                    detectorVersion = p.detectorVersion,
                    lastDetectedAt = null,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        // Keep stored definitions in sync with the versioned registry, preserving user choices.
        for (p in PlatformRegistry.ALL) {
            db.platforms().updateDefinition(
                p.id, p.displayName, p.packageNames.firstOrNull(), p.platformType.code,
                p.detectorType.code, p.capabilityLevel.code, p.detectorVersion, now,
            )
        }
    }

    suspend fun platforms(): List<PlatformEntity> = db.platforms().all()

    suspend fun setPlatformEnabled(id: String, enabled: Boolean, now: Long) = db.platforms().setEnabled(id, enabled, now)

    suspend fun installSalt(): String {
        db.settings().get(KEY_SALT)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val salt = bytes.joinToString("") { "%02x".format(it) }
        db.settings().put(SettingEntity(KEY_SALT, salt))
        return salt
    }

    suspend fun loadSettings(): TrackerSettings =
        SettingsCodec.decode(db.settings().get(KEY_SETTINGS)?.let { runCatching { JSONObject(it) }.getOrNull() })

    suspend fun saveSettings(settings: TrackerSettings) =
        db.settings().put(SettingEntity(KEY_SETTINGS, SettingsCodec.encode(settings).toString()))

    /**
     * Idempotent insert. Returns false when the deduplication key already exists, which means
     * the event was a duplicate and must not change any total.
     */
    suspend fun insertCountedEvent(event: CountedVideoEvent, dayKey: String, now: Long): Boolean = db.withTransaction {
        val rowId = db.videoEvents().insertIgnore(
            VideoEventEntity(
                platformId = event.platformId,
                sessionId = event.sessionId,
                eventTimestamp = event.eventTimestamp,
                dayKey = dayKey,
                videoIdentifierHash = event.videoIdentifierHash,
                deduplicationKey = event.deduplicationKey,
                eventType = event.eventType.code,
                confidenceLevel = event.confidence.code,
                evidenceType = event.evidence.code,
                countStatus = VideoEventEntity.STATUS_COUNTED,
                sourceDetector = event.sourceDetector,
                detectorVersion = event.detectorVersion,
                createdAt = now,
            ),
        )
        if (rowId != -1L) db.platforms().markDetected(event.platformId, event.eventTimestamp)
        rowId != -1L
    }

    suspend fun addDiagnostic(dayKey: String, platformId: String, metric: String, delta: Long, now: Long) = db.withTransaction {
        val inserted = db.diagnostics().insertIgnore(DiagnosticCounterEntity(dayKey, platformId, metric, delta, now))
        if (inserted == -1L) db.diagnostics().add(dayKey, platformId, metric, delta, now)
    }

    suspend fun diagnosticsForDay(dayKey: String) = db.diagnostics().forDay(dayKey)

    suspend fun countsByDay(fromDay: String, toDay: String) = db.videoEvents().countsByDay(fromDay, toDay)

    suspend fun activityByDay(fromDay: String, toDay: String) = db.activity().byDay(fromDay, toDay)

    suspend fun recentEvents(limit: Int) = db.videoEvents().recent(limit)

    suspend fun firstDay(): String? = db.videoEvents().firstDay()

    suspend fun setUsageMillis(dayKey: String, platformId: String, millis: Long, now: Long) =
        db.activity().upsert(ActivityDailyEntity(dayKey, platformId, ActivityDailyEntity.SOURCE_USAGE_STATS, millis, now))

    suspend fun addWebActivity(dayKey: String, platformId: String, deltaMillis: Long, now: Long) = db.withTransaction {
        val existing = db.activity().get(dayKey, platformId, ActivityDailyEntity.SOURCE_WEB_TIMER)?.activeMillis ?: 0L
        db.activity().upsert(
            ActivityDailyEntity(dayKey, platformId, ActivityDailyEntity.SOURCE_WEB_TIMER, existing + deltaMillis, now),
        )
    }

    suspend fun sentWarningIds(dayKey: String): Set<String> = db.warnings().idsForDay(dayKey).toSet()

    /** Returns true only for the caller that recorded the warning first. */
    suspend fun recordWarning(dayKey: String, w: PlannedWarning, notify: Boolean, now: Long): Boolean =
        db.warnings().insertIgnore(
            WarningSentEntity(w.id, dayKey, w.scope.key, w.kind.code, w.threshold, notify, now),
        ) != -1L

    suspend fun blockTargets(): List<BlockTarget> = db.blocks().targets().map {
        BlockTarget(if (it.kind == BlockTargetKind.DOMAIN.code) BlockTargetKind.DOMAIN else BlockTargetKind.APP, it.value, it.platformId)
    }

    suspend fun addBlockTarget(target: BlockTarget, now: Long) =
        db.blocks().insertTarget(BlockTargetEntity(kind = target.kind.code, value = target.value, platformId = target.platformId, createdAt = now))

    suspend fun removeBlockTarget(kind: BlockTargetKind, value: String) = db.blocks().deleteTarget(kind.code, value)

    /**
     * Logs a block event once per day and target. With [countRepeats], later occurrences
     * increment the counter (used for "the user opened a blocked app" attempts).
     */
    suspend fun recordBlockEvent(
        dayKey: String,
        target: BlockTarget,
        eventType: String,
        reason: String,
        now: Long,
        countRepeats: Boolean,
    ): Boolean =
        db.withTransaction {
            val inserted = db.blocks().insertEvent(
                BlockEventEntity(
                    dayKey = dayKey, targetKind = target.kind.code, targetValue = target.value,
                    eventType = eventType, reason = reason, occurrences = 1, firstAt = now, lastAt = now,
                ),
            )
            if (inserted == -1L && countRepeats) db.blocks().bumpEvent(dayKey, target.kind.code, target.value, eventType, now)
            inserted != -1L
        }

    suspend fun blockEventsForDay(dayKey: String) = db.blocks().eventsForDay(dayKey)

    suspend fun overridesForDay(dayKey: String) = db.blocks().overridesForDay(dayKey)

    suspend fun insertOverride(row: OverrideEntity) = db.blocks().insertOverride(row)

    suspend fun cancelOverrides(dayKey: String, now: Long) = db.blocks().cancelOverrides(dayKey, now)

    suspend fun clearDay(dayKey: String) = db.withTransaction {
        db.videoEvents().deleteDay(dayKey)
        db.activity().deleteDay(dayKey)
        db.warnings().deleteDay(dayKey)
        db.diagnostics().deleteDay(dayKey)
        db.blocks().deleteEventsForDay(dayKey)
        db.blocks().deleteOverridesForDay(dayKey)
    }

    /** Deletes all history but keeps settings, platforms and the block list. */
    suspend fun clearAllHistory() = db.withTransaction {
        db.videoEvents().deleteAll()
        db.activity().deleteAll()
        db.warnings().deleteAll()
        db.diagnostics().deleteAll()
        db.blocks().deleteAllEvents()
        db.blocks().deleteAllOverrides()
        db.platforms().clearLastDetected()
    }

    /** Deletes every piece of local data, including settings, block list and the install salt. */
    suspend fun deleteAllData() = db.withTransaction {
        clearAllHistory()
        db.blocks().deleteAllTargets()
        db.settings().delete(KEY_SETTINGS)
        db.settings().delete(KEY_SALT)
        for (p in PlatformRegistry.ALL) db.platforms().setEnabled(p.id, true, System.currentTimeMillis())
    }

    suspend fun applyRetention(cutoffDay: String) = db.withTransaction {
        db.videoEvents().deleteBefore(cutoffDay)
        db.activity().deleteBefore(cutoffDay)
        db.warnings().deleteBefore(cutoffDay)
        db.diagnostics().deleteBefore(cutoffDay)
        db.blocks().deleteEventsBefore(cutoffDay)
        db.blocks().deleteOverridesBefore(cutoffDay)
    }

    suspend fun storedCounts(): Map<String, Int> = mapOf(
        "videoEvents" to db.videoEvents().countAll(),
        "activityRows" to db.activity().countAll(),
        "blockTargets" to db.blocks().targets().size,
    )

    companion object {
        private const val KEY_SETTINGS = "tracker_settings"
        private const val KEY_SALT = "install_salt"

        @Volatile private var instance: TrackerRepository? = null

        fun get(context: Context): TrackerRepository = instance ?: synchronized(this) {
            instance ?: TrackerRepository(TrackerDatabase.get(context)).also { instance = it }
        }

        /** A repository over a caller-owned database (in-memory in instrumented tests). */
        fun forTesting(db: TrackerDatabase): TrackerRepository = TrackerRepository(db)
    }
}
