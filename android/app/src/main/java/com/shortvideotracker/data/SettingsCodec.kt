package com.shortvideotracker.data

import com.shortvideotracker.core.engine.FloatingCounterMode
import com.shortvideotracker.core.engine.LimitRule
import com.shortvideotracker.core.engine.LimitSettings
import com.shortvideotracker.core.engine.TrackerSettings
import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialisation of [TrackerSettings], shared by storage and the React Native bridge. */
object SettingsCodec {

    fun encode(s: TrackerSettings): JSONObject = JSONObject().apply {
        put("onboardingCompleted", s.onboardingCompleted)
        put("monitoringEnabled", s.monitoringEnabled)
        put("warningsEnabled", s.warningsEnabled)
        put("blockingEnabled", s.blockingEnabled)
        put("floatingCounter", s.floatingCounter.code)
        put("resetMinuteOfDay", s.resetMinuteOfDay)
        put("minViewSeconds", s.minViewSeconds)
        put("sessionGapMinutes", s.sessionGapMinutes)
        put("overrideMinutes", s.overrideMinutes)
        put("maxOverridesPerDay", s.maxOverridesPerDay)
        put("retentionDays", s.retentionDays)
        put("theme", s.theme)
        put("limits", encodeLimits(s.limits))
    }

    fun decode(json: JSONObject?): TrackerSettings {
        val d = TrackerSettings()
        if (json == null) return d
        return TrackerSettings(
            onboardingCompleted = json.optBoolean("onboardingCompleted", d.onboardingCompleted),
            monitoringEnabled = json.optBoolean("monitoringEnabled", d.monitoringEnabled),
            warningsEnabled = json.optBoolean("warningsEnabled", d.warningsEnabled),
            blockingEnabled = json.optBoolean("blockingEnabled", d.blockingEnabled),
            floatingCounter = FloatingCounterMode.fromCode(json.optString("floatingCounter", d.floatingCounter.code)),
            resetMinuteOfDay = json.optInt("resetMinuteOfDay", d.resetMinuteOfDay),
            minViewSeconds = json.optInt("minViewSeconds", d.minViewSeconds),
            sessionGapMinutes = json.optInt("sessionGapMinutes", d.sessionGapMinutes),
            overrideMinutes = json.optInt("overrideMinutes", d.overrideMinutes),
            maxOverridesPerDay = json.optInt("maxOverridesPerDay", d.maxOverridesPerDay),
            retentionDays = json.optInt("retentionDays", d.retentionDays),
            theme = json.optString("theme", d.theme),
            limits = decodeLimits(json.optJSONObject("limits"), d.limits),
        )
    }

    private fun encodeLimits(l: LimitSettings) = JSONObject().apply {
        put("global", encodeRule(l.global))
        put("perPlatform", JSONObject().apply { l.perPlatform.forEach { (k, v) -> put(k, encodeRule(v)) } })
        put("warningMilestones", JSONArray(l.warningMilestones))
        put("approachingPercent", l.approachingPercent ?: JSONObject.NULL)
        put("repeatEveryVideosAfterLimit", l.repeatEveryVideosAfterLimit ?: JSONObject.NULL)
        put("enforceEstimatedCounts", l.enforceEstimatedCounts)
    }

    private fun decodeLimits(json: JSONObject?, d: LimitSettings): LimitSettings {
        if (json == null) return d
        val per = json.optJSONObject("perPlatform")
        val perPlatform = buildMap {
            per?.keys()?.forEach { key -> put(key, decodeRule(per.optJSONObject(key))) }
        }
        val milestones = json.optJSONArray("warningMilestones")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.opt(i).asIntOrNull() }
        } ?: d.warningMilestones
        return LimitSettings(
            global = if (json.has("global")) decodeRule(json.optJSONObject("global")) else d.global,
            perPlatform = perPlatform,
            warningMilestones = milestones,
            approachingPercent = if (json.has("approachingPercent")) json.opt("approachingPercent").asIntOrNull() else d.approachingPercent,
            repeatEveryVideosAfterLimit = json.opt("repeatEveryVideosAfterLimit").asIntOrNull(),
            enforceEstimatedCounts = json.optBoolean("enforceEstimatedCounts", d.enforceEstimatedCounts),
        )
    }

    private fun encodeRule(r: LimitRule) = JSONObject().apply {
        put("maxVideos", r.maxVideos ?: JSONObject.NULL)
        put("maxMinutes", r.maxMinutes ?: JSONObject.NULL)
    }

    private fun decodeRule(json: JSONObject?): LimitRule = LimitRule(
        maxVideos = json?.opt("maxVideos").asIntOrNull(),
        maxMinutes = json?.opt("maxMinutes").asIntOrNull(),
    )

    private fun Any?.asIntOrNull(): Int? = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    }
}
