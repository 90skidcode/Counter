package com.shortvideotracker.core.engine

import com.shortvideotracker.core.model.ConfidenceLevel

enum class FloatingCounterMode(val code: String) {
    OFF("off"),

    /** Show the counter on top of monitored short-video apps only. */
    MONITORED_APPS("monitored_apps"),

    /** Show the counter on top of every app (except this one and the system UI). */
    ALL_APPS("all_apps");

    companion object {
        fun fromCode(code: String?): FloatingCounterMode = entries.firstOrNull { it.code == code } ?: MONITORED_APPS
    }
}

data class TrackerSettings(
    val onboardingCompleted: Boolean = false,
    val monitoringEnabled: Boolean = true,
    val warningsEnabled: Boolean = true,
    val blockingEnabled: Boolean = false,
    val floatingCounter: FloatingCounterMode = FloatingCounterMode.MONITORED_APPS,
    /** Minutes after midnight when the tracking day starts. */
    val resetMinuteOfDay: Int = 0,
    val minViewSeconds: Int = 3,
    val sessionGapMinutes: Int = 5,
    val limits: LimitSettings = LimitSettings(),
    /** Length of one temporary override. 0 disables overrides. */
    val overrideMinutes: Int = 10,
    val maxOverridesPerDay: Int = 3,
    val retentionDays: Int = 365,
    val theme: String = "system",
) {
    val countingConfig: CountingConfig
        get() = CountingConfig(
            minViewMillis = minViewSeconds * 1_000L,
            sessionGapMillis = sessionGapMinutes * 60_000L,
        )
}

data class SettingsIssue(
    val field: String,
    val severity: Severity,
    val message: String,
) {
    enum class Severity(val code: String) { ERROR("error"), WARNING("warning") }
}

data class ValidatedSettings(val settings: TrackerSettings, val issues: List<SettingsIssue>) {
    val hasErrors: Boolean get() = issues.any { it.severity == SettingsIssue.Severity.ERROR }
}

/**
 * Validates settings, clamps out-of-range values and explains conflicting combinations.
 * Errors mean a value was out of range and has been corrected; warnings describe effects the
 * user may not expect.
 */
object SettingsValidator {

    const val MAX_VIDEOS = 10_000
    const val MAX_MINUTES = 24 * 60

    fun validate(
        input: TrackerSettings,
        blockTargetCount: Int,
        enabledPlatformLevels: Map<String, ConfidenceLevel>,
    ): ValidatedSettings {
        val issues = mutableListOf<SettingsIssue>()
        fun error(field: String, message: String) {
            issues += SettingsIssue(field, SettingsIssue.Severity.ERROR, message)
        }
        fun warn(field: String, message: String) {
            issues += SettingsIssue(field, SettingsIssue.Severity.WARNING, message)
        }

        fun rule(field: String, r: LimitRule): LimitRule {
            val videos = r.maxVideos?.let { v ->
                if (v !in 1..MAX_VIDEOS) error("$field.maxVideos", "Video limit must be between 1 and $MAX_VIDEOS.")
                v.coerceIn(1, MAX_VIDEOS)
            }
            val minutes = r.maxMinutes?.let { m ->
                if (m !in 1..MAX_MINUTES) error("$field.maxMinutes", "Time limit must be between 1 and $MAX_MINUTES minutes.")
                m.coerceIn(1, MAX_MINUTES)
            }
            return LimitRule(videos, minutes)
        }

        val limits = input.limits
        val global = rule("limits.global", limits.global)
        val perPlatform = limits.perPlatform
            .mapValues { (id, r) -> rule("limits.perPlatform.$id", r) }
            .filterValues { !it.isEmpty }

        val milestones = limits.warningMilestones.filter { m ->
            val ok = m in 1..MAX_VIDEOS
            if (!ok) error("limits.warningMilestones", "Warning milestones must be between 1 and $MAX_VIDEOS.")
            ok
        }.distinct().sorted()
        if (global.maxVideos != null && milestones.any { it >= global.maxVideos }) {
            warn("limits.warningMilestones", "Milestones at or above the daily limit are covered by the limit-reached warning.")
        }

        val approaching = limits.approachingPercent?.let { p ->
            if (p !in 50..95) error("limits.approachingPercent", "Approaching warning must be between 50% and 95%.")
            p.coerceIn(50, 95)
        }
        val repeat = limits.repeatEveryVideosAfterLimit?.let { r ->
            if (r !in 1..1000) error("limits.repeatEveryVideosAfterLimit", "Repeat interval must be between 1 and 1000 videos.")
            r.coerceIn(1, 1000)
        }

        for ((id, r) in perPlatform) {
            if (global.maxVideos != null && r.maxVideos != null && r.maxVideos >= global.maxVideos) {
                warn("limits.perPlatform.$id.maxVideos", "This platform limit is not lower than the global limit, so the global limit is reached first.")
            }
            if (id !in enabledPlatformLevels) {
                warn("limits.perPlatform.$id", "This platform is not monitored, so its limit can only use app time.")
            }
        }

        if (input.resetMinuteOfDay !in 0 until DayClock.MINUTES_PER_DAY) error("resetMinuteOfDay", "Reset time must be within the day.")
        if (input.minViewSeconds !in 0..60) error("minViewSeconds", "Minimum viewing time must be between 0 and 60 seconds.")
        if (input.sessionGapMinutes !in 1..120) error("sessionGapMinutes", "Session gap must be between 1 and 120 minutes.")
        if (input.overrideMinutes !in 0..120) error("overrideMinutes", "Override length must be between 0 and 120 minutes.")
        if (input.maxOverridesPerDay !in 0..20) error("maxOverridesPerDay", "Overrides per day must be between 0 and 20.")
        if (input.retentionDays !in 7..3650) error("retentionDays", "History retention must be between 7 and 3650 days.")
        if (input.theme !in setOf("system", "light", "dark")) error("theme", "Unknown theme.")

        if (input.blockingEnabled) {
            if (blockTargetCount == 0) warn("blockingEnabled", "Blocking is on, but no apps or websites are selected.")
            if (global.isEmpty && perPlatform.isEmpty()) warn("blockingEnabled", "Blocking is on, but no limit is set.")
            val onlyEstimates = enabledPlatformLevels.values.none { it == ConfidenceLevel.A_VERIFIED }
            val hasVideoLimit = global.maxVideos != null || perPlatform.values.any { it.maxVideos != null }
            if (!limits.enforceEstimatedCounts && onlyEstimates && hasVideoLimit) {
                warn(
                    "limits.enforceEstimatedCounts",
                    "Your monitored platforms only provide estimated counts. Video limits will not block anything unless estimated-count enforcement is on.",
                )
            }
        }

        val sanitized = input.copy(
            resetMinuteOfDay = input.resetMinuteOfDay.coerceIn(0, DayClock.MINUTES_PER_DAY - 1),
            minViewSeconds = input.minViewSeconds.coerceIn(0, 60),
            sessionGapMinutes = input.sessionGapMinutes.coerceIn(1, 120),
            overrideMinutes = input.overrideMinutes.coerceIn(0, 120),
            maxOverridesPerDay = input.maxOverridesPerDay.coerceIn(0, 20),
            retentionDays = input.retentionDays.coerceIn(7, 3650),
            theme = input.theme.takeIf { it in setOf("system", "light", "dark") } ?: "system",
            limits = limits.copy(
                global = global,
                perPlatform = perPlatform,
                warningMilestones = milestones,
                approachingPercent = approaching,
                repeatEveryVideosAfterLimit = repeat,
            ),
        )
        return ValidatedSettings(sanitized, issues)
    }
}
