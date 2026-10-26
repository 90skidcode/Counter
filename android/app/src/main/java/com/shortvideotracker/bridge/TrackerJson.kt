package com.shortvideotracker.bridge

import android.content.Context
import android.content.pm.PackageManager
import com.shortvideotracker.core.engine.BlockState
import com.shortvideotracker.core.engine.LimitScope
import com.shortvideotracker.core.engine.LimitStatus
import com.shortvideotracker.core.engine.SettingsIssue
import com.shortvideotracker.core.model.ConfidenceLevel
import com.shortvideotracker.core.model.PlatformDefinition
import com.shortvideotracker.core.model.PlatformRegistry
import com.shortvideotracker.core.model.PlatformType
import com.shortvideotracker.data.ActivityDailyEntity
import com.shortvideotracker.data.PlatformEntity
import com.shortvideotracker.permissions.PermissionStatusProvider
import com.shortvideotracker.tracking.TrackingCoordinator
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the JSON payloads returned to React Native. The shapes are mirrored by the
 * TypeScript types in src/domain/types.ts; keep both in sync.
 */
class TrackerJson(private val context: Context, private val coordinator: TrackingCoordinator) {

    private val permissions = PermissionStatusProvider(context)

    fun permissions(): JSONObject = permissions.toJson()

    /** Runtime monitoring state of a platform, accounting for permissions and user choices. */
    private fun monitoringStatus(def: PlatformDefinition, entity: PlatformEntity?, installed: Boolean): Pair<String, ConfidenceLevel> {
        val usageAccess = permissions.usageAccessGranted()
        val accessibility = permissions.accessibilityRunning()
        val fallback = if (def.platformType == PlatformType.APP && usageAccess) ConfidenceLevel.C_USAGE_ONLY else ConfidenceLevel.D_UNAVAILABLE
        return when {
            entity?.enabled == false -> "disabled" to ConfidenceLevel.D_UNAVAILABLE
            !coordinator.settings.monitoringEnabled -> "paused" to ConfidenceLevel.D_UNAVAILABLE
            !installed -> "not_installed" to ConfidenceLevel.D_UNAVAILABLE
            !def.capabilityLevel.producesVideoCounts ->
                (if (usageAccess) "usage_only" else "needs_usage_access") to fallback
            !accessibility -> "needs_accessibility" to fallback
            else -> "active" to def.capabilityLevel
        }
    }

    private fun isInstalled(def: PlatformDefinition): Boolean = def.packageNames.any { pkg ->
        try {
            context.packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    suspend fun platforms(): JSONArray {
        val entities = coordinator.repository.platforms().associateBy { it.id }
        return JSONArray().apply {
            for (def in PlatformRegistry.ALL) {
                val entity = entities[def.id]
                val installed = isInstalled(def)
                val (status, effective) = monitoringStatus(def, entity, installed)
                put(
                    JSONObject().apply {
                        put("id", def.id)
                        put("displayName", def.displayName)
                        put("packageNames", JSONArray(def.packageNames))
                        put("platformType", def.platformType.code)
                        put("detectorType", def.detectorType.code)
                        put("detectorVersion", def.detectorVersion)
                        put("capabilityLevel", def.capabilityLevel.code)
                        put("effectiveLevel", effective.code)
                        put("enabled", entity?.enabled ?: true)
                        put("installed", installed)
                        put("monitoringStatus", status)
                        put("lastDetectedAt", entity?.lastDetectedAt ?: JSONObject.NULL)
                        put("lastSignalAt", coordinator.lastSignalAt[def.id] ?: JSONObject.NULL)
                        put("canDistinguishShortsFromRegular", def.canDistinguishShortsFromRegular)
                        put("measuresBackgroundPlayback", def.measuresBackgroundPlayback)
                        put("blockGranularity", def.blockGranularity.code)
                        put("testedAppVersions", def.testedAppVersions)
                        put("knownLimitations", JSONArray(def.knownLimitations))
                        put("timeSource", if (def.platformType == PlatformType.APP) ActivityDailyEntity.SOURCE_USAGE_STATS else ActivityDailyEntity.SOURCE_WEB_TIMER)
                    },
                )
            }
        }
    }

    suspend fun todaySummary(): JSONObject {
        val today = coordinator.today
        val platforms = platforms()
        val statusById = (0 until platforms.length()).associate { i ->
            platforms.getJSONObject(i).let { it.getString("id") to it }
        }
        return JSONObject().apply {
            put("generatedAt", System.currentTimeMillis())
            put("dayKey", today?.dayKey ?: coordinator.dayKeyNow())
            put("resetsAt", today?.resetsAt ?: JSONObject.NULL)
            put("permissions", permissions())
            put("monitoringEnabled", coordinator.settings.monitoringEnabled)
            put(
                "platforms",
                JSONArray().apply {
                    for (def in PlatformRegistry.ALL) {
                        val info = statusById[def.id] ?: continue
                        val usage = today?.platform(def.id)
                        val canCount = def.capabilityLevel.producesVideoCounts
                        val hasEvents = usage != null && usage.verifiedVideos + usage.estimatedVideos > 0
                        val countsKnown = canCount && (info.getString("monitoringStatus") == "active" || hasEvents)
                        put(
                            JSONObject().apply {
                                put("id", def.id)
                                put("displayName", def.displayName)
                                put("monitoringStatus", info.getString("monitoringStatus"))
                                put("effectiveLevel", info.getString("effectiveLevel"))
                                put("capabilityLevel", def.capabilityLevel.code)
                                // Unknown is null, never 0.
                                put("verifiedVideos", if (countsKnown) usage?.verifiedVideos ?: 0 else JSONObject.NULL)
                                put("estimatedVideos", if (countsKnown) usage?.estimatedVideos ?: 0 else JSONObject.NULL)
                                put("activeMillis", usage?.foregroundMillis ?: JSONObject.NULL)
                                put("timeSource", info.getString("timeSource"))
                                put("lastDetectedAt", info.get("lastDetectedAt"))
                                put("enabled", info.getBoolean("enabled"))
                            },
                        )
                    }
                },
            )
            put("limits", JSONArray().apply { today?.statuses?.forEach { put(limitStatus(it)) } })
            put("block", blockState(coordinator.blockState))
        }
    }

    fun limitStatus(s: LimitStatus) = JSONObject().apply {
        put("scope", s.scope.key)
        put("platformId", (s.scope as? LimitScope.Platform)?.platformId ?: JSONObject.NULL)
        put("maxVideos", s.rule.maxVideos ?: JSONObject.NULL)
        put("maxMinutes", s.rule.maxMinutes ?: JSONObject.NULL)
        put("verifiedVideos", s.verifiedVideos)
        put("estimatedVideos", s.estimatedVideos)
        put("enforcedVideos", s.enforcedVideos)
        put("enforcedIncludesEstimates", s.enforcedIncludesEstimates)
        put("remainingVideos", s.remainingVideos ?: JSONObject.NULL)
        put("activeMinutes", s.foregroundMinutes ?: JSONObject.NULL)
        put("videoLimitReached", s.videoLimitReached)
        put("timeLimitReached", s.timeLimitReached)
    }

    fun blockState(state: BlockState?): JSONObject = JSONObject().apply {
        put("blockingEnabled", coordinator.settings.blockingEnabled)
        if (state == null) {
            put("triggered", JSONArray())
            put("enforcedCount", 0)
            put("suspension", JSONObject.NULL)
            put("overrideUntil", JSONObject.NULL)
            put("resetsAt", JSONObject.NULL)
            return@apply
        }
        put(
            "triggered",
            JSONArray().apply {
                state.triggered.forEach { b ->
                    put(
                        JSONObject().apply {
                            put("kind", b.target.kind.code)
                            put("value", b.target.value)
                            put("platformId", b.target.platformId ?: JSONObject.NULL)
                            put("reasons", JSONArray(b.reasons.map { it.key }))
                        },
                    )
                }
            },
        )
        put("enforcedCount", state.enforced.size)
        put("suspension", state.suspension?.code ?: JSONObject.NULL)
        put("overrideUntil", state.overrideUntil ?: JSONObject.NULL)
        put("resetsAt", state.resetsAt)
    }

    fun issues(list: List<SettingsIssue>) = JSONArray().apply {
        list.forEach { i ->
            put(JSONObject().apply { put("field", i.field); put("severity", i.severity.code); put("message", i.message) })
        }
    }
}
