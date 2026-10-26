package com.shortvideotracker.core

import com.shortvideotracker.core.engine.LimitRule
import com.shortvideotracker.core.engine.LimitSettings
import com.shortvideotracker.core.engine.SettingsIssue
import com.shortvideotracker.core.engine.SettingsValidator
import com.shortvideotracker.core.engine.TrackerSettings
import com.shortvideotracker.core.model.ConfidenceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsValidatorTest {
    private val levels = mapOf("youtube_shorts" to ConfidenceLevel.B_INFERRED)

    @Test
    fun defaultsAreValid() {
        val v = SettingsValidator.validate(TrackerSettings(), blockTargetCount = 0, enabledPlatformLevels = levels)
        assertFalse(v.hasErrors)
        assertTrue(v.issues.isEmpty())
    }

    @Test
    fun outOfRangeValuesAreClampedWithErrors() {
        val v = SettingsValidator.validate(
            TrackerSettings(minViewSeconds = 500, limits = LimitSettings(global = LimitRule(maxVideos = 0))),
            blockTargetCount = 0,
            enabledPlatformLevels = levels,
        )
        assertTrue(v.hasErrors)
        assertEquals(60, v.settings.minViewSeconds)
        assertEquals(1, v.settings.limits.global.maxVideos)
    }

    @Test
    fun blockingWithOnlyEstimatesWarnsAboutEnforcementMode() {
        val v = SettingsValidator.validate(TrackerSettings(blockingEnabled = true), blockTargetCount = 1, enabledPlatformLevels = levels)
        assertEquals(listOf("limits.enforceEstimatedCounts"), v.issues.map { it.field })
        assertEquals(SettingsIssue.Severity.WARNING, v.issues.single().severity)
    }

    @Test
    fun platformLimitAboveGlobalIsExplained() {
        val v = SettingsValidator.validate(
            TrackerSettings(limits = LimitSettings(global = LimitRule(maxVideos = 20), perPlatform = mapOf("youtube_shorts" to LimitRule(maxVideos = 30)))),
            blockTargetCount = 0,
            enabledPlatformLevels = levels,
        )
        assertTrue(v.issues.any { it.field == "limits.perPlatform.youtube_shorts.maxVideos" })
    }
}
