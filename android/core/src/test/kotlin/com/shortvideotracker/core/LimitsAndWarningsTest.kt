package com.shortvideotracker.core

import com.shortvideotracker.core.engine.LimitEvaluator
import com.shortvideotracker.core.engine.LimitRule
import com.shortvideotracker.core.engine.LimitScope
import com.shortvideotracker.core.engine.LimitSettings
import com.shortvideotracker.core.engine.PlatformUsage
import com.shortvideotracker.core.engine.WarningKind
import com.shortvideotracker.core.engine.WarningPlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LimitsAndWarningsTest {

    private val usage = listOf(
        PlatformUsage("youtube_shorts", verifiedVideos = 0, estimatedVideos = 30, foregroundMillis = 20 * 60_000L),
        PlatformUsage("brave", verifiedVideos = 12, estimatedVideos = 0, foregroundMillis = null),
    )

    @Test
    fun estimatedCountsDoNotEnforceByDefault() {
        val s = LimitEvaluator.evaluate(LimitSettings(global = LimitRule(maxVideos = 40)), usage).single()
        assertEquals(12, s.enforcedVideos)
        assertEquals(42, s.totalVideos)
        assertFalse(s.reached)
        assertEquals(28, s.remainingVideos)
    }

    @Test
    fun estimatedCountsEnforceWhenOptedIn() {
        val s = LimitEvaluator.evaluate(LimitSettings(global = LimitRule(maxVideos = 40), enforceEstimatedCounts = true), usage).single()
        assertTrue(s.videoLimitReached)
        assertEquals(0, s.remainingVideos)
    }

    @Test
    fun timeLimitUsesKnownForegroundTimeOnly() {
        val settings = LimitSettings(global = LimitRule(maxMinutes = 20))
        val s = LimitEvaluator.evaluate(settings, usage).single()
        assertEquals(20L, s.foregroundMinutes)
        assertTrue(s.timeLimitReached)
    }

    @Test
    fun perPlatformLimitsAreEvaluatedSeparately() {
        val settings = LimitSettings(
            global = LimitRule(maxVideos = 100),
            perPlatform = mapOf("youtube_shorts" to LimitRule(maxVideos = 10), "tiktok" to LimitRule()),
            enforceEstimatedCounts = true,
        )
        val statuses = LimitEvaluator.evaluate(settings, usage)
        assertEquals(listOf(LimitScope.Global, LimitScope.Platform("youtube_shorts")), statuses.map { it.scope })
        assertTrue(statuses[1].reached)
        assertFalse(statuses[0].reached)
    }

    @Test
    fun warningsAreNotRepeatedOnceRecorded() {
        val settings = LimitSettings(global = LimitRule(maxVideos = 50), warningMilestones = listOf(10, 25), enforceEstimatedCounts = true)
        val statuses = LimitEvaluator.evaluate(settings, usage) // 42 videos
        val first = WarningPlanner.plan("2026-10-26", settings, statuses, emptySet())
        // Milestones 10 and 25 crossed together: only the highest is shown, both recorded.
        assertEquals(listOf(WarningKind.MILESTONE, WarningKind.APPROACHING_VIDEO_LIMIT), first.toNotify.map { it.kind })
        assertEquals(25, first.toNotify[0].threshold)
        assertTrue(first.toNotify[0].includesEstimates)
        assertEquals(3, first.toRecord.size)

        val second = WarningPlanner.plan("2026-10-26", settings, statuses, first.toRecord.map { it.id }.toSet())
        assertTrue(second.toNotify.isEmpty())

        val nextDay = WarningPlanner.plan("2026-10-27", settings, statuses, first.toRecord.map { it.id }.toSet())
        assertEquals(2, nextDay.toNotify.size)
    }

    @Test
    fun limitReachedSupersedesApproachingAndRepeatsWhenConfigured() {
        val settings = LimitSettings(
            global = LimitRule(maxVideos = 20),
            warningMilestones = emptyList(),
            repeatEveryVideosAfterLimit = 10,
            enforceEstimatedCounts = true,
        )
        val statuses = LimitEvaluator.evaluate(settings, usage) // 42 enforced
        val plan = WarningPlanner.plan("d", settings, statuses, emptySet())
        val shown = plan.toNotify.single()
        assertEquals(WarningKind.AFTER_LIMIT_REPEAT, shown.kind)
        assertEquals(40, shown.threshold)
        assertEquals(
            setOf(WarningKind.APPROACHING_VIDEO_LIMIT, WarningKind.VIDEO_LIMIT_REACHED, WarningKind.AFTER_LIMIT_REPEAT),
            plan.toRecord.map { it.kind }.toSet(),
        )
    }
}
