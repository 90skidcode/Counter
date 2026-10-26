package com.shortvideotracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shortvideotracker.core.engine.BlockTarget
import com.shortvideotracker.core.engine.BlockTargetKind
import com.shortvideotracker.core.engine.CountedVideoEvent
import com.shortvideotracker.core.engine.TrackerSettings
import com.shortvideotracker.core.model.ConfidenceLevel
import com.shortvideotracker.core.model.EvidenceType
import com.shortvideotracker.core.model.VideoEventType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerRepositoryTest {

    private lateinit var db: TrackerDatabase
    private lateinit var repo: TrackerRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrackerDatabase::class.java).build()
        repo = TrackerRepository.forTesting(db)
        repo.seedPlatforms(0)
    }

    @After
    fun tearDown() = db.close()

    private fun event(key: String, ts: Long = 1_000) = CountedVideoEvent(
        platformId = "youtube_shorts",
        sourceDetector = "known_pager_view",
        detectorVersion = 1,
        sessionId = "s1",
        eventTimestamp = ts,
        videoIdentifierHash = null,
        deduplicationKey = key,
        eventType = VideoEventType.VIDEO_VIEW,
        confidence = ConfidenceLevel.B_INFERRED,
        evidence = EvidenceType.PAGER_POSITION_KNOWN_VIEW,
    )

    @Test
    fun duplicateDeduplicationKeysNeverCountTwice() = runBlocking {
        assertTrue(repo.insertCountedEvent(event("k1"), "2026-10-26", 0))
        assertFalse(repo.insertCountedEvent(event("k1"), "2026-10-26", 0))
        assertTrue(repo.insertCountedEvent(event("k2"), "2026-10-26", 0))
        val counts = repo.countsByDay("2026-10-26", "2026-10-26")
        assertEquals(2, counts.sumOf { it.count })
        assertEquals(1_000L, repo.platforms().first { it.id == "youtube_shorts" }.lastDetectedAt)
    }

    @Test
    fun clearDayRemovesOnlyThatDay() = runBlocking {
        repo.insertCountedEvent(event("a"), "2026-10-25", 0)
        repo.insertCountedEvent(event("b"), "2026-10-26", 0)
        repo.setUsageMillis("2026-10-26", "youtube_shorts", 5_000, 0)
        repo.clearDay("2026-10-26")
        assertEquals(1, repo.countsByDay("2026-10-01", "2026-10-31").sumOf { it.count })
        assertTrue(repo.activityByDay("2026-10-26", "2026-10-26").isEmpty())
    }

    @Test
    fun clearAllHistoryKeepsSettingsAndTargets() = runBlocking {
        repo.saveSettings(TrackerSettings(blockingEnabled = true))
        repo.addBlockTarget(BlockTarget(BlockTargetKind.DOMAIN, "tiktok.com", null), 0)
        repo.insertCountedEvent(event("a"), "2026-10-26", 0)
        repo.clearAllHistory()
        assertEquals(0, repo.storedCounts()["videoEvents"])
        assertTrue(repo.loadSettings().blockingEnabled)
        assertEquals(1, repo.blockTargets().size)
        assertNull(repo.platforms().first { it.id == "youtube_shorts" }.lastDetectedAt)
    }

    @Test
    fun deleteAllDataRemovesEverythingAndRotatesSalt() = runBlocking {
        val salt = repo.installSalt()
        repo.saveSettings(TrackerSettings(blockingEnabled = true))
        repo.addBlockTarget(BlockTarget(BlockTargetKind.DOMAIN, "tiktok.com", null), 0)
        repo.insertCountedEvent(event("a"), "2026-10-26", 0)
        repo.deleteAllData()
        assertEquals(0, repo.storedCounts()["videoEvents"])
        assertEquals(0, repo.blockTargets().size)
        assertFalse(repo.loadSettings().blockingEnabled)
        assertTrue(salt != repo.installSalt())
    }

    @Test
    fun retentionDeletesOldRows() = runBlocking {
        repo.insertCountedEvent(event("old"), "2025-01-01", 0)
        repo.insertCountedEvent(event("new"), "2026-10-26", 0)
        repo.applyRetention("2026-01-01")
        assertEquals(listOf("2026-10-26"), repo.countsByDay("2000-01-01", "2100-01-01").map { it.dayKey })
    }
}
