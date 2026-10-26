package com.shortvideotracker.core

import com.shortvideotracker.core.engine.CountedVideoEvent
import com.shortvideotracker.core.engine.CountingConfig
import com.shortvideotracker.core.engine.CountingListener
import com.shortvideotracker.core.engine.DiagnosticMetric
import com.shortvideotracker.core.engine.IdentifierHasher
import com.shortvideotracker.core.engine.VideoCountingEngine
import com.shortvideotracker.core.engine.VideoObservation
import com.shortvideotracker.core.model.ConfidenceLevel
import com.shortvideotracker.core.model.EvidenceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class VideoCountingEngineTest {

    private val counted = mutableListOf<CountedVideoEvent>()
    private val diagnostics = mutableListOf<DiagnosticMetric>()
    private var sessionSeq = 0
    private var day = "2026-10-26"

    private fun engine(minViewMillis: Long = 3_000, gap: Long = 300_000) = VideoCountingEngine(
        config = CountingConfig(minViewMillis, gap),
        hasher = IdentifierHasher("test-salt-0123456789"),
        dayKeyOf = { day },
        newSessionId = { "s${++sessionSeq}" },
        listener = object : CountingListener {
            override fun onCounted(event: CountedVideoEvent) {
                counted += event
            }

            override fun onDiagnostic(platformId: String, metric: DiagnosticMetric) {
                diagnostics += metric
            }
        },
    )

    private fun pos(t: Long, p: Int, platform: String = "youtube_shorts") = VideoObservation(
        platformId = platform,
        sourceDetector = "known_pager_view",
        detectorVersion = 1,
        timestamp = t,
        videoKey = "pos:$p",
        confidence = ConfidenceLevel.B_INFERRED,
        evidence = EvidenceType.PAGER_POSITION_KNOWN_VIEW,
    )

    @Test
    fun repeatedCallbacksForSameVideoCountOnce() {
        val e = engine()
        repeat(20) { e.observe(pos(it * 200L, 0)) }
        e.tick(10_000)
        assertEquals(1, counted.size)
        assertEquals(DiagnosticMetric.DUPLICATE_CALLBACK_SUPPRESSED, diagnostics.first())
    }

    @Test
    fun videoCountsOnlyAfterMinimumViewTime() {
        val e = engine()
        e.observe(pos(0, 0))
        e.tick(2_999)
        assertEquals(0, counted.size)
        e.tick(3_000)
        assertEquals(1, counted.size)
        assertEquals(0L, counted.single().eventTimestamp)
    }

    @Test
    fun quickSwipesAreDiscarded() {
        val e = engine()
        e.observe(pos(0, 0))
        e.observe(pos(1_000, 1))
        e.observe(pos(2_000, 2))
        e.platformLeft("youtube_shorts", 2_500)
        assertEquals(0, counted.size)
        assertEquals(3, diagnostics.count { it == DiagnosticMetric.SHORT_VIEW_DISCARDED })
    }

    @Test
    fun scrollingToNewVideosCountsEach() {
        val e = engine()
        for (i in 0 until 5) e.observe(pos(i * 10_000L, i))
        e.platformLeft("youtube_shorts", 50_000)
        assertEquals(5, counted.size)
        assertEquals(5, counted.map { it.deduplicationKey }.toSet().size)
    }

    @Test
    fun scrollingBackInSameSessionIsNotCountedAgain() {
        val e = engine()
        e.observe(pos(0, 0))
        e.observe(pos(10_000, 1))
        e.observe(pos(20_000, 0))
        e.tick(40_000)
        assertEquals(2, counted.size)
        assertEquals(1, diagnostics.count { it == DiagnosticMetric.REPLAY_NOT_COUNTED })
    }

    @Test
    fun newSessionAfterGapCountsSamePositionAgainWithDifferentKey() {
        val e = engine()
        e.observe(pos(0, 0))
        e.platformLeft("youtube_shorts", 10_000)
        e.observe(pos(10_000 + 300_001, 0))
        e.tick(400_000)
        assertEquals(2, counted.size)
        assertNotEquals(counted[0].sessionId, counted[1].sessionId)
        assertNotEquals(counted[0].deduplicationKey, counted[1].deduplicationKey)
    }

    @Test
    fun returningWithinGapContinuesSession() {
        val e = engine()
        e.observe(pos(0, 0))
        e.platformLeft("youtube_shorts", 10_000)
        e.observe(pos(60_000, 0))
        e.tick(100_000)
        assertEquals(1, counted.size)
    }

    @Test
    fun dayRolloverStartsNewSession() {
        val e = engine()
        e.observe(pos(0, 0))
        e.tick(5_000)
        day = "2026-10-27"
        e.observe(pos(6_000, 0))
        e.tick(20_000)
        assertEquals(2, counted.size)
    }

    @Test
    fun platformsAreIndependent() {
        val e = engine()
        e.observe(pos(0, 0, "youtube_shorts"))
        e.observe(pos(100, 0, "instagram_reels"))
        e.tick(5_000)
        assertEquals(setOf("youtube_shorts", "instagram_reels"), counted.map { it.platformId }.toSet())
    }

    @Test
    fun deduplicationKeyIsDeterministicForSameSessionAndVideo() {
        val a = IdentifierHasher("test-salt-0123456789").hash("dedup-v1", "p", "s1", "pos:1")
        val b = IdentifierHasher("test-salt-0123456789").hash("dedup-v1", "p", "s1", "pos:1")
        val c = IdentifierHasher("other-salt-0123456789").hash("dedup-v1", "p", "s1", "pos:1")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun onlyStableIdentitiesGetAVideoHash() {
        val e = engine()
        e.observe(pos(0, 0))
        e.observe(
            VideoObservation("brave", "browser_address_bar", 1, 10_000, "id:youtube:abc123DEF", ConfidenceLevel.A_VERIFIED, EvidenceType.URL_VIDEO_ID),
        )
        e.tick(20_000)
        assertNull(counted[0].videoIdentifierHash)
        assertEquals(64, counted[1].videoIdentifierHash!!.length)
    }

    @Test
    fun nextDueAtReportsPendingCommitTime() {
        val e = engine()
        assertNull(e.nextDueAt())
        e.observe(pos(1_000, 0))
        assertEquals(4_000, e.nextDueAt())
        e.tick(4_000)
        assertNull(e.nextDueAt())
    }

    @Test
    fun zeroMinimumCountsImmediately() {
        val e = engine(minViewMillis = 0)
        e.observe(pos(0, 0))
        assertEquals(1, counted.size)
    }

    @Test
    fun usageOnlyObservationsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            VideoObservation("snapchat_spotlight", "usage", 1, 0, "pos:0", ConfidenceLevel.C_USAGE_ONLY, EvidenceType.USAGE_STATS)
        }
    }
}
