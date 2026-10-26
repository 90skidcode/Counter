package com.shortvideotracker.core.engine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Maps timestamps to "tracking days" that start at a configurable reset time instead of
 * midnight. A reset at 04:00 means 01:30 on Tuesday still belongs to Monday.
 *
 * Day keys are ISO dates (yyyy-MM-dd) so they sort and range-query as strings.
 */
class DayClock(
    resetMinuteOfDay: Int,
    private val zone: ZoneId,
) {
    private val resetTime: LocalTime = LocalTime.of(
        resetMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1) / 60,
        resetMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1) % 60,
    )

    fun dayOf(epochMillis: Long): LocalDate {
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
        return if (local.toLocalTime() < resetTime) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    fun dayKey(epochMillis: Long): String = dayOf(epochMillis).toString()

    /** Inclusive start of the tracking day, in epoch millis. */
    fun dayStart(day: LocalDate): Long = day.atTime(resetTime).atZone(zone).toInstant().toEpochMilli()

    /** Exclusive end of the tracking day (the next reset), in epoch millis. */
    fun dayEnd(day: LocalDate): Long = dayStart(day.plusDays(1))

    fun nextResetAfter(epochMillis: Long): Long = dayEnd(dayOf(epochMillis))

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
