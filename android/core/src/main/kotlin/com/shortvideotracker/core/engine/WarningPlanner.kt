package com.shortvideotracker.core.engine

enum class WarningKind(val code: String) {
    MILESTONE("milestone"),
    APPROACHING_VIDEO_LIMIT("approaching_video_limit"),
    VIDEO_LIMIT_REACHED("video_limit_reached"),
    AFTER_LIMIT_REPEAT("after_limit_repeat"),
    APPROACHING_TIME_LIMIT("approaching_time_limit"),
    TIME_LIMIT_REACHED("time_limit_reached"),
}

data class PlannedWarning(
    /** Stable id; persisted so the same warning is never sent twice, even after a restart. */
    val id: String,
    val scope: LimitScope,
    val kind: WarningKind,
    /** The threshold that was crossed (videos or minutes). */
    val threshold: Int,
    val currentVideos: Int,
    val limit: Int?,
    val currentMinutes: Long?,
    /** True when some of the counted videos are estimates. */
    val includesEstimates: Boolean,
)

data class WarningPlan(
    /** Warnings to show now: at most one per scope and family, the most significant. */
    val toNotify: List<PlannedWarning>,
    /** Every crossed threshold, including ones superseded by a bigger one, to mark as sent. */
    val toRecord: List<PlannedWarning>,
)

object WarningPlanner {

    fun plan(dayKey: String, settings: LimitSettings, statuses: List<LimitStatus>, alreadySent: Set<String>): WarningPlan {
        val crossed = statuses.flatMap { crossedThresholds(dayKey, settings, it) }
        val fresh = crossed.filter { it.id !in alreadySent }
        val toNotify = fresh
            .groupBy { it.scope.key to family(it.kind) }
            .values
            .map { group -> group.maxWith(compareBy({ severity(it.kind) }, { it.threshold })) }
            .sortedWith(compareBy({ it.scope !is LimitScope.Global }, { it.scope.key }, { family(it.kind) }))
        return WarningPlan(toNotify = toNotify, toRecord = fresh)
    }

    private fun crossedThresholds(dayKey: String, settings: LimitSettings, s: LimitStatus): List<PlannedWarning> {
        val out = mutableListOf<PlannedWarning>()
        fun add(kind: WarningKind, threshold: Int, videos: Int, includesEstimates: Boolean, limit: Int?) {
            out += PlannedWarning(
                id = "$dayKey|${s.scope.key}|${kind.code}|$threshold",
                scope = s.scope,
                kind = kind,
                threshold = threshold,
                currentVideos = videos,
                limit = limit,
                currentMinutes = s.foregroundMinutes,
                includesEstimates = includesEstimates,
            )
        }

        if (s.scope is LimitScope.Global) {
            // Milestones are informational and use every detected video, clearly labelled.
            for (m in settings.warningMilestones.filter { it > 0 }.distinct()) {
                if (s.totalVideos >= m) add(WarningKind.MILESTONE, m, s.totalVideos, s.estimatedVideos > 0, s.rule.maxVideos)
            }
        }

        val maxVideos = s.rule.maxVideos
        val enforcedHasEstimates = s.enforcedIncludesEstimates && s.estimatedVideos > 0
        if (maxVideos != null) {
            val approach = settings.approachingPercent?.let { pct -> (maxVideos * pct + 99) / 100 }
            if (approach != null && approach in 1 until maxVideos && s.enforcedVideos >= approach) {
                add(WarningKind.APPROACHING_VIDEO_LIMIT, approach, s.enforcedVideos, enforcedHasEstimates, maxVideos)
            }
            if (s.videoLimitReached) {
                add(WarningKind.VIDEO_LIMIT_REACHED, maxVideos, s.enforcedVideos, enforcedHasEstimates, maxVideos)
                val every = settings.repeatEveryVideosAfterLimit
                if (every != null && every > 0) {
                    var t = maxVideos + every
                    while (t <= s.enforcedVideos) {
                        add(WarningKind.AFTER_LIMIT_REPEAT, t, s.enforcedVideos, enforcedHasEstimates, maxVideos)
                        t += every
                    }
                }
            }
        }

        val maxMinutes = s.rule.maxMinutes
        val minutes = s.foregroundMinutes
        if (maxMinutes != null && minutes != null) {
            val approach = settings.approachingPercent?.let { pct -> (maxMinutes * pct + 99) / 100 }
            if (approach != null && approach in 1 until maxMinutes && minutes >= approach) {
                add(WarningKind.APPROACHING_TIME_LIMIT, approach, s.totalVideos, false, maxMinutes)
            }
            if (s.timeLimitReached) add(WarningKind.TIME_LIMIT_REACHED, maxMinutes, s.totalVideos, false, maxMinutes)
        }
        return out
    }

    private fun family(kind: WarningKind): String = when (kind) {
        WarningKind.MILESTONE -> "milestone"
        WarningKind.APPROACHING_VIDEO_LIMIT, WarningKind.VIDEO_LIMIT_REACHED, WarningKind.AFTER_LIMIT_REPEAT -> "videos"
        WarningKind.APPROACHING_TIME_LIMIT, WarningKind.TIME_LIMIT_REACHED -> "time"
    }

    private fun severity(kind: WarningKind): Int = when (kind) {
        WarningKind.MILESTONE, WarningKind.APPROACHING_VIDEO_LIMIT, WarningKind.APPROACHING_TIME_LIMIT -> 0
        WarningKind.VIDEO_LIMIT_REACHED, WarningKind.TIME_LIMIT_REACHED -> 1
        WarningKind.AFTER_LIMIT_REPEAT -> 2
    }
}
