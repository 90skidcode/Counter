package com.shortvideotracker.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.shortvideotracker.core.engine.BlockTarget
import com.shortvideotracker.core.engine.BlockTargetKind
import com.shortvideotracker.core.engine.ShortVideoSite
import com.shortvideotracker.core.engine.UrlClassifier
import com.shortvideotracker.core.model.ConfidenceLevel
import com.shortvideotracker.core.model.PlatformRegistry
import com.shortvideotracker.core.model.PlatformType
import com.shortvideotracker.data.ActivityDailyEntity
import com.shortvideotracker.data.SettingsCodec
import com.shortvideotracker.data.TrackerDatabase
import com.shortvideotracker.permissions.PermissionStatusProvider
import com.shortvideotracker.tracking.TrackingCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * TurboModule implementing the codegen spec in src/native/NativeShortVideoTracker.ts.
 * Complex payloads cross the bridge as JSON strings whose shapes are typed in
 * src/domain/types.ts and validated on the JavaScript side.
 *
 * Change notifications are pushed as the "ShortVideoTrackerChanged" event, so the UI never
 * needs to poll.
 */
class ShortVideoTrackerModule(reactContext: ReactApplicationContext) : NativeShortVideoTrackerSpec(reactContext) {

    private val coordinator = TrackingCoordinator.get(reactContext)
    private val json = TrackerJson(reactContext, coordinator)
    private val permissions = PermissionStatusProvider(reactContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val changeListener = TrackingCoordinator.Listener { reason ->
        if (reactApplicationContext.hasActiveReactInstance()) {
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(EVENT_CHANGED, reason)
        }
    }

    init {
        coordinator.addListener(changeListener)
    }

    override fun invalidate() {
        coordinator.removeListener(changeListener)
        scope.cancel()
        super.invalidate()
    }

    private fun run(promise: Promise, block: suspend () -> Any?) {
        scope.launch {
            try {
                promise.resolve(block())
            } catch (e: Exception) {
                promise.reject("tracker_error", e.message ?: e.javaClass.simpleName, e)
            }
        }
    }

    // ---- Permissions ---------------------------------------------------------------------

    override fun getPermissionStatus(promise: Promise) = run(promise) { json.permissions().toString() }

    override fun openSettings(kind: String, promise: Promise) = run(promise) {
        val intent = permissions.settingsIntent(kind) ?: return@run false
        try {
            reactApplicationContext.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            permissions.settingsIntent("appDetails")?.let { reactApplicationContext.startActivity(it) }
            false
        }
    }

    // ---- Dashboard, platforms, history ----------------------------------------------------

    override fun getTodaySummary(promise: Promise) = run(promise) {
        coordinator.call { recompute("summary_requested", notify = false) }
        json.todaySummary().toString()
    }

    override fun getPlatforms(promise: Promise) = run(promise) { json.platforms().toString() }

    override fun setPlatformEnabled(platformId: String, enabled: Boolean, promise: Promise) = run(promise) {
        require(PlatformRegistry.byId(platformId) != null) { "unknown platform $platformId" }
        coordinator.setPlatformEnabled(platformId, enabled)
        null
    }

    override fun getHistory(fromDay: String, toDay: String, promise: Promise) = run(promise) {
        historyRows(fromDay, toDay).toString()
    }

    private suspend fun historyRows(fromDay: String, toDay: String): JSONObject {
        val repo = coordinator.repository
        val counts = repo.countsByDay(fromDay, toDay)
        val activity = repo.activityByDay(fromDay, toDay)
        val keys = (counts.map { it.dayKey to it.platformId } + activity.map { it.dayKey to it.platformId }).toSortedSet(
            compareBy<Pair<String, String>>({ it.first }, { it.second }),
        )
        val rows = JSONArray()
        for ((day, platformId) in keys) {
            val def = PlatformRegistry.byId(platformId) ?: continue
            val c = counts.filter { it.dayKey == day && it.platformId == platformId }
            val source = if (def.platformType == PlatformType.APP) ActivityDailyEntity.SOURCE_USAGE_STATS else ActivityDailyEntity.SOURCE_WEB_TIMER
            val a = activity.firstOrNull { it.dayKey == day && it.platformId == platformId && it.source == source }
            rows.put(
                JSONObject().apply {
                    put("dayKey", day)
                    put("platformId", platformId)
                    put("verifiedVideos", c.filter { it.confidenceLevel == ConfidenceLevel.A_VERIFIED.code }.sumOf { it.count })
                    put("estimatedVideos", c.filter { it.confidenceLevel == ConfidenceLevel.B_INFERRED.code }.sumOf { it.count })
                    put("activeMillis", a?.activeMillis ?: JSONObject.NULL)
                    put("timeSource", source)
                },
            )
        }
        return JSONObject().apply {
            put("fromDay", fromDay)
            put("toDay", toDay)
            put("firstRecordedDay", repo.firstDay() ?: JSONObject.NULL)
            put("rows", rows)
        }
    }

    // ---- Settings -------------------------------------------------------------------------

    override fun getSettings(promise: Promise) = run(promise) { SettingsCodec.encode(coordinator.settings).toString() }

    override fun validateSettings(settingsJson: String, promise: Promise) = run(promise) {
        val validated = coordinator.call { validate(SettingsCodec.decode(JSONObject(settingsJson))) }
        JSONObject().apply {
            put("settings", SettingsCodec.encode(validated.settings))
            put("issues", json.issues(validated.issues))
            put("saved", false)
        }.toString()
    }

    override fun saveSettings(settingsJson: String, promise: Promise) = run(promise) {
        val (validated, saved) = coordinator.saveSettings(SettingsCodec.decode(JSONObject(settingsJson)))
        JSONObject().apply {
            put("settings", SettingsCodec.encode(if (saved) validated.settings else coordinator.settings))
            put("issues", json.issues(validated.issues))
            put("saved", saved)
        }.toString()
    }

    // ---- Blocking -------------------------------------------------------------------------

    override fun getBlockTargets(promise: Promise) = run(promise) {
        val selected = coordinator.call { repository.blockTargets() }
        val apps = JSONArray()
        for (def in PlatformRegistry.ALL) {
            for (pkg in def.packageNames) {
                val label = appLabel(pkg) ?: continue // only installed apps are offered
                apps.put(
                    JSONObject().apply {
                        put("kind", BlockTargetKind.APP.code)
                        put("value", pkg)
                        put("label", label)
                        put("platformId", def.id)
                        put("selected", selected.any { it.kind == BlockTargetKind.APP && it.value == pkg })
                    },
                )
            }
        }
        val suggestedDomains = ShortVideoSite.entries.flatMap { it.domains }
        val domains = JSONArray()
        (suggestedDomains + selected.filter { it.kind == BlockTargetKind.DOMAIN }.map { it.value }).distinct().forEach { d ->
            domains.put(
                JSONObject().apply {
                    put("kind", BlockTargetKind.DOMAIN.code)
                    put("value", d)
                    put("label", d)
                    put("platformId", JSONObject.NULL)
                    put("selected", selected.any { it.kind == BlockTargetKind.DOMAIN && it.value == d })
                },
            )
        }
        JSONObject().apply {
            put("apps", apps)
            put("domains", domains)
            put("supportedBrowsers", JSONArray(PlatformRegistry.ALL.filter { it.platformType == PlatformType.BROWSER }.map { it.displayName }))
        }.toString()
    }

    private fun appLabel(pkg: String): String? = try {
        val pm = reactApplicationContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        null
    }

    override fun addBlockTarget(kind: String, value: String, promise: Promise) = run(promise) {
        val result = JSONObject()
        val target = when (kind) {
            BlockTargetKind.APP.code -> {
                val def = PlatformRegistry.byPackage(value)
                if (def == null) {
                    result.put("error", "not_supported")
                    null
                } else {
                    BlockTarget(BlockTargetKind.APP, value, def.id)
                }
            }
            BlockTargetKind.DOMAIN.code -> {
                val domain = UrlClassifier.normaliseDomainInput(value)
                if (domain == null) {
                    result.put("error", "invalid_domain")
                    null
                } else {
                    BlockTarget(BlockTargetKind.DOMAIN, domain, null)
                }
            }
            else -> {
                result.put("error", "invalid_kind")
                null
            }
        }
        if (target != null) {
            if (coordinator.addBlockTarget(target)) {
                result.put("value", target.value)
            } else {
                result.put("error", "essential_app")
            }
        }
        result.put("ok", !result.has("error"))
        result.toString()
    }

    override fun removeBlockTarget(kind: String, value: String, promise: Promise) = run(promise) {
        val k = if (kind == BlockTargetKind.DOMAIN.code) BlockTargetKind.DOMAIN else BlockTargetKind.APP
        coordinator.removeBlockTarget(k, value)
        null
    }

    override fun getBlockState(promise: Promise) = run(promise) {
        val day = coordinator.dayKeyNow()
        val overrides = coordinator.call { repository.overridesForDay(day) }
        val events = coordinator.call { repository.blockEventsForDay(day) }
        json.blockState(coordinator.blockState).apply {
            put("overrideMinutes", coordinator.settings.overrideMinutes)
            put("overridesUsedToday", overrides.count { it.kind == "temporary" })
            put("maxOverridesPerDay", coordinator.settings.maxOverridesPerDay)
            put(
                "log",
                JSONArray().apply {
                    overrides.forEach { o ->
                        put(JSONObject().apply {
                            put("type", "override_${o.kind}")
                            put("at", o.startedAt)
                            put("until", o.endsAt ?: JSONObject.NULL)
                            put("cancelledAt", o.cancelledAt ?: JSONObject.NULL)
                        })
                    }
                    events.forEach { e ->
                        put(JSONObject().apply {
                            put("type", "block_${e.eventType}")
                            put("at", e.firstAt)
                            put("target", e.targetValue)
                            put("occurrences", e.occurrences)
                        })
                    }
                },
            )
        }.toString()
    }

    override fun startOverride(promise: Promise) = run(promise) {
        val error = coordinator.startOverride()
        JSONObject().apply {
            put("ok", error == null)
            put("error", error ?: JSONObject.NULL)
        }.toString()
    }

    override fun pauseBlockingForToday(promise: Promise) = run(promise) {
        coordinator.pauseBlockingForToday()
        null
    }

    override fun cancelOverrides(promise: Promise) = run(promise) {
        coordinator.cancelOverrides()
        null
    }

    // ---- Diagnostics and data management --------------------------------------------------

    override fun getDiagnostics(promise: Promise) = run(promise) {
        val day = coordinator.dayKeyNow()
        val counters = coordinator.call { repository.diagnosticsForDay(day) }
        val recent = coordinator.call { repository.recentEvents(25) }
        JSONObject().apply {
            put("dayKey", day)
            put("permissions", json.permissions())
            put("serviceConnected", com.shortvideotracker.detection.ShortVideoAccessibilityService.isConnected)
            put("databaseVersion", TrackerDatabase.VERSION)
            put("platforms", json.platforms())
            put(
                "counters",
                JSONArray().apply {
                    counters.forEach { c ->
                        put(JSONObject().apply { put("platformId", c.platformId); put("metric", c.metric); put("value", c.value) })
                    }
                },
            )
            // Minimal metadata only: no identifiers, hashes or content.
            put(
                "recentEvents",
                JSONArray().apply {
                    recent.forEach { e ->
                        put(JSONObject().apply {
                            put("platformId", e.platformId)
                            put("at", e.eventTimestamp)
                            put("confidenceLevel", e.confidenceLevel)
                            put("evidenceType", e.evidenceType)
                            put("detector", "${e.sourceDetector} v${e.detectorVersion}")
                        })
                    }
                },
            )
        }.toString()
    }

    override fun getStoredDataSummary(promise: Promise) = run(promise) {
        val counts = coordinator.call { repository.storedCounts() }
        val first = coordinator.call { repository.firstDay() }
        JSONObject().apply {
            counts.forEach { (k, v) -> put(k, v) }
            put("firstRecordedDay", first ?: JSONObject.NULL)
            put("retentionDays", coordinator.settings.retentionDays)
        }.toString()
    }

    override fun exportReport(fromDay: String, toDay: String, promise: Promise) = run(promise) {
        val rows = historyRows(fromDay, toDay).getJSONArray("rows")
        buildString {
            append("day,platform,verified_videos,estimated_videos,active_minutes,time_source\n")
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                val minutes = if (r.isNull("activeMillis")) "" else (r.getLong("activeMillis") / 60_000).toString()
                append(r.getString("dayKey")).append(',')
                    .append(r.getString("platformId")).append(',')
                    .append(r.getInt("verifiedVideos")).append(',')
                    .append(r.getInt("estimatedVideos")).append(',')
                    .append(minutes).append(',')
                    .append(r.getString("timeSource")).append('\n')
            }
        }
    }

    override fun clearToday(promise: Promise) = run(promise) {
        coordinator.clearToday()
        null
    }

    override fun clearAllHistory(promise: Promise) = run(promise) {
        coordinator.clearAllHistory()
        null
    }

    override fun deleteAllData(promise: Promise) = run(promise) {
        coordinator.deleteAllData()
        reactApplicationContext.getSharedPreferences("overlay_position", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        null
    }

    companion object {
        const val NAME = NativeShortVideoTrackerSpec.NAME
        const val EVENT_CHANGED = "ShortVideoTrackerChanged"
    }
}
