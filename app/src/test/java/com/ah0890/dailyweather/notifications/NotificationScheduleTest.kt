package com.ah0890.dailyweather.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationScheduleTest {

    private val zone = ZoneId.of("Europe/London")
    private val seven = LocalTime.of(7, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone)

    @Test
    fun `before the time it runs later today`() {
        assertEquals(at(2026, 10, 10, 7), NotificationSchedule.nextRun(at(2026, 10, 10, 5, 30), seven))
    }

    @Test
    fun `after the time it runs tomorrow`() {
        assertEquals(at(2026, 10, 11, 7), NotificationSchedule.nextRun(at(2026, 10, 10, 9, 0), seven))
    }

    @Test
    fun `exactly at the time it runs tomorrow, so a finished run never repeats`() {
        assertEquals(at(2026, 10, 11, 7), NotificationSchedule.nextRun(at(2026, 10, 10, 7, 0), seven))
    }

    @Test
    fun `stays at 7am local time across the end of daylight saving`() {
        // Clocks go back on 25 Oct 2026 in the UK: that day is 25 hours long.
        val next = NotificationSchedule.nextRun(at(2026, 10, 24, 8, 0), seven)

        assertEquals(at(2026, 10, 25, 7), next)
        assertEquals(Duration.ofHours(24), NotificationSchedule.delayUntilNextRun(at(2026, 10, 24, 8, 0), seven))
    }

    @Test
    fun `stays at 7am local time across the start of daylight saving`() {
        // Clocks go forward on 29 Mar 2026 in the UK: that day is 23 hours long.
        assertEquals(Duration.ofHours(22), NotificationSchedule.delayUntilNextRun(at(2026, 3, 28, 8, 0), seven))
    }

    @Test
    fun `a run within three hours of the time is not too late`() {
        assertFalse(NotificationSchedule.isTooLate(at(2026, 10, 10, 7, 5), seven))
        assertFalse(NotificationSchedule.isTooLate(at(2026, 10, 10, 10, 0), seven))
    }

    @Test
    fun `a run long after the time is too late`() {
        assertTrue(NotificationSchedule.isTooLate(at(2026, 10, 10, 10, 1), seven))
        assertTrue(NotificationSchedule.isTooLate(at(2026, 10, 10, 18, 0), seven))
    }

    @Test
    fun `a run slightly before the time counts against yesterday and is too late`() {
        // A job that somehow ran early at 06:50 is not "today's" summary.
        assertTrue(NotificationSchedule.isTooLate(at(2026, 10, 10, 6, 50), seven))
    }
}
