package com.shortvideotracker.core

import com.shortvideotracker.core.engine.DayClock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DayClockTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun midnightReset() {
        val c = DayClock(0, zone)
        assertEquals("2026-10-26", c.dayKey(at(2026, 10, 26, 0, 0)))
        assertEquals("2026-10-25", c.dayKey(at(2026, 10, 25, 23, 59)))
    }

    @Test
    fun earlyMorningBelongsToPreviousDayWithLateReset() {
        val c = DayClock(4 * 60, zone)
        assertEquals("2026-10-25", c.dayKey(at(2026, 10, 26, 3, 59)))
        assertEquals("2026-10-26", c.dayKey(at(2026, 10, 26, 4, 0)))
    }

    @Test
    fun nextResetIsTheFollowingResetTime() {
        val c = DayClock(4 * 60, zone)
        assertEquals(at(2026, 10, 27, 4, 0), c.nextResetAfter(at(2026, 10, 26, 22, 0)))
        assertEquals(at(2026, 10, 26, 4, 0), c.nextResetAfter(at(2026, 10, 26, 1, 0)))
    }

    @Test
    fun dstDayHasCorrectBounds() {
        val ny = ZoneId.of("America/New_York")
        val c = DayClock(0, ny)
        val day = LocalDate.of(2026, 11, 1) // DST ends: 25-hour day
        assertEquals(25 * 3_600_000L, c.dayEnd(day) - c.dayStart(day))
    }
}
