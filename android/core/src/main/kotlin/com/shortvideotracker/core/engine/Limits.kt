package com.shortvideotracker.core.engine

/** A limit for one scope. Null fields are "no limit". */
data class LimitRule(
    val maxVideos: Int? = null,
    val maxMinutes: Int? = null,
) {
    val isEmpty: Boolean get() = maxVideos == null && maxMinutes == null
}

data class LimitSettings(
    val global: LimitRule = LimitRule(maxVideos = 50),
    val perPlatform: Map<String, LimitRule> = emptyMap(),
    /** Informational milestones on the global video total, e.g. 25 → "You have watched 25". */
    val warningMilestones: List<Int> = listOf(25),
    /** Warn when this share of a limit is used. Null disables "approaching" warnings. */
    val approachingPercent: Int? = 80,
    /** After a video limit is reached, warn again every N videos. Null disables repeats. */
    val repeatEveryVideosAfterLimit: Int? = null,
    /**
     * Estimated (level B) counts only enforce limits when the user opts in. Time limits rely on
     * Usage Access foreground time, which Android measures directly.
     */
    val enforceEstimatedCounts: Boolean = false,
)

data class PlatformUsage(
    val platformId: String,
    val verifiedVideos: Int,
    val estimatedVideos: Int,
    /** Foreground time from Usage Access, or null when unavailable. */
    val foregroundMillis: Long?,
)

sealed interface LimitScope {
    val key: String

    data object Global : LimitScope {
        override val key = "global"
    }

    data class Platform(val platformId: String) : LimitScope {
        override val key = "platform:$platformId"
    }
}

data class LimitStatus(
    val scope: LimitScope,
    val rule: LimitRule,
    val verifiedVideos: Int,
    val estimatedVideos: Int,
    /** The count compared against [LimitRule.maxVideos]. */
    val enforcedVideos: Int,
    val enforcedIncludesEstimates: Boolean,
    val foregroundMinutes: Long?,
    val videoLimitReached: Boolean,
    val timeLimitReached: Boolean,
) {
    val reached: Boolean get() = videoLimitReached || timeLimitReached
    val remainingVideos: Int? get() = rule.maxVideos?.let { (it - enforcedVideos).coerceAtLeast(0) }
    val totalVideos: Int get() = verifiedVideos + estimatedVideos
}

object LimitEvaluator {

    /**
     * Evaluates the global limit across all [usage] rows and each configured per-platform
     * limit. Rows for disabled platforms must be excluded by the caller.
     */
    fun evaluate(settings: LimitSettings, usage: List<PlatformUsage>): List<LimitStatus> {
        val statuses = mutableListOf(
            status(LimitScope.Global, settings.global, usage, settings.enforceEstimatedCounts),
        )
        for ((platformId, rule) in settings.perPlatform.toSortedMap()) {
            if (rule.isEmpty) continue
            val rows = usage.filter { it.platformId == platformId }
            statuses += status(LimitScope.Platform(platformId), rule, rows, settings.enforceEstimatedCounts)
        }
        return statuses
    }

    private fun status(
        scope: LimitScope,
        rule: LimitRule,
        rows: List<PlatformUsage>,
        enforceEstimated: Boolean,
    ): LimitStatus {
        val verified = rows.sumOf { it.verifiedVideos }
        val estimated = rows.sumOf { it.estimatedVideos }
        val enforced = if (enforceEstimated) verified + estimated else verified
        val knownTimes = rows.mapNotNull { it.foregroundMillis }
        val minutes = if (knownTimes.isEmpty()) null else knownTimes.sum() / 60_000
        return LimitStatus(
            scope = scope,
            rule = rule,
            verifiedVideos = verified,
            estimatedVideos = estimated,
            enforcedVideos = enforced,
            enforcedIncludesEstimates = enforceEstimated,
            foregroundMinutes = minutes,
            videoLimitReached = rule.maxVideos != null && enforced >= rule.maxVideos,
            timeLimitReached = rule.maxMinutes != null && minutes != null && minutes >= rule.maxMinutes,
        )
    }
}
