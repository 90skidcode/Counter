package com.shortvideotracker.core

import com.shortvideotracker.core.engine.BlockStateCalculator
import com.shortvideotracker.core.engine.BlockSuspension
import com.shortvideotracker.core.engine.BlockTarget
import com.shortvideotracker.core.engine.BlockTargetKind
import com.shortvideotracker.core.engine.EssentialApps
import com.shortvideotracker.core.engine.LimitEvaluator
import com.shortvideotracker.core.engine.LimitRule
import com.shortvideotracker.core.engine.LimitScope
import com.shortvideotracker.core.engine.LimitSettings
import com.shortvideotracker.core.engine.PlatformUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockStateCalculatorTest {

    private val yt = BlockTarget(BlockTargetKind.APP, "com.google.android.youtube", "youtube_shorts")
    private val ig = BlockTarget(BlockTargetKind.APP, "com.instagram.android", "instagram_reels")
    private val tiktokWeb = BlockTarget(BlockTargetKind.DOMAIN, "tiktok.com", null)
    private val dialer = BlockTarget(BlockTargetKind.APP, "com.google.android.dialer", null)

    private fun state(
        settings: LimitSettings,
        usage: List<PlatformUsage>,
        blockingEnabled: Boolean = true,
        overrideUntil: Long? = null,
        paused: String? = null,
        now: Long = 1_000,
    ) = BlockStateCalculator.calculate(
        now = now,
        dayKey = "2026-10-26",
        resetsAt = 99_000,
        blockingEnabled = blockingEnabled,
        targets = listOf(yt, ig, tiktokWeb, dialer),
        statuses = LimitEvaluator.evaluate(settings, usage),
        overrideUntil = overrideUntil,
        pausedForDayKey = paused,
        isEssential = { EssentialApps.isEssential(it, emptySet(), "com.shortvideotracker") },
    )

    private val verified = listOf(
        PlatformUsage("youtube_shorts", 10, 0, null),
        PlatformUsage("instagram_reels", 2, 0, null),
    )

    @Test
    fun globalLimitBlocksAllTargetsExceptEssential() {
        val s = state(LimitSettings(global = LimitRule(maxVideos = 12)), verified)
        assertEquals(setOf(yt, ig, tiktokWeb), s.enforced.map { it.target }.toSet())
        assertNotNull(s.blocksHost("www.tiktok.com"))
        assertNull(s.blocksPackage("com.google.android.dialer"))
    }

    @Test
    fun platformLimitBlocksOnlyThatPlatform() {
        val s = state(
            LimitSettings(global = LimitRule(maxVideos = 100), perPlatform = mapOf("youtube_shorts" to LimitRule(maxVideos = 5))),
            verified,
        )
        assertEquals(listOf(yt), s.enforced.map { it.target })
        assertEquals(listOf<LimitScope>(LimitScope.Platform("youtube_shorts")), s.enforced.single().reasons)
    }

    @Test
    fun bothReasonsAreReportedGlobalFirst() {
        val s = state(
            LimitSettings(global = LimitRule(maxVideos = 12), perPlatform = mapOf("youtube_shorts" to LimitRule(maxVideos = 5))),
            verified,
        )
        assertEquals(listOf(LimitScope.Global, LimitScope.Platform("youtube_shorts")), s.blocksPackage(yt.value)!!.reasons)
    }

    @Test
    fun estimatedCountsDoNotBlockUnlessOptedIn() {
        val estimated = listOf(PlatformUsage("youtube_shorts", 0, 99, null))
        assertTrue(state(LimitSettings(global = LimitRule(maxVideos = 10)), estimated).enforced.isEmpty())
        assertEquals(3, state(LimitSettings(global = LimitRule(maxVideos = 10), enforceEstimatedCounts = true), estimated).enforced.size)
    }

    @Test
    fun suspensionsStopEnforcementButKeepTriggers() {
        val settings = LimitSettings(global = LimitRule(maxVideos = 1))
        assertEquals(BlockSuspension.DISABLED, state(settings, verified, blockingEnabled = false).suspension)
        val overridden = state(settings, verified, overrideUntil = 5_000)
        assertEquals(BlockSuspension.OVERRIDE, overridden.suspension)
        assertTrue(overridden.enforced.isEmpty())
        assertEquals(3, overridden.triggered.size)
        assertNull(state(settings, verified, overrideUntil = 500).suspension) // expired override
        assertEquals(BlockSuspension.PAUSED_FOR_DAY, state(settings, verified, paused = "2026-10-26").suspension)
        assertNull(state(settings, verified, paused = "2026-10-25").suspension)
    }
}
