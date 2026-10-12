package com.ah0890.dailyweather.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
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

    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    @Test
    fun `weekday schedule skips from friday to monday`() {
        // 9 Oct 2026 is a Friday.
        assertEquals(at(2026, 10, 12, 7), NotificationSchedule.nextRun(at(2026, 10, 9, 8, 0), seven, weekdays))
    }

    @Test
    fun `weekend schedule waits for saturday`() {
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        assertEquals(at(2026, 10, 10, 7), NotificationSchedule.nextRun(at(2026, 10, 7, 12, 0), seven, weekend))
    }

    @Test
    fun `a single custom day a week later`() {
        // Wednesday 7 Oct after the time → next Wednesday.
        assertEquals(at(2026, 10, 14, 7), NotificationSchedule.nextRun(at(2026, 10, 7, 9, 0), seven, setOf(DayOfWeek.WEDNESDAY)))
    }

    @Test
    fun `no days selected means no run`() {
        assertNull(NotificationSchedule.nextRun(at(2026, 10, 7, 9, 0), seven, emptySet()))
        assertNull(NotificationSchedule.delayUntilNextRun(at(2026, 10, 7, 9, 0), seven, emptySet()))
        assertTrue(NotificationSchedule.isTooLate(at(2026, 10, 7, 7, 5), seven, emptySet()))
    }

    @Test
    fun `a friday run delayed into saturday is too late`() {
        assertFalse(NotificationSchedule.isTooLate(at(2026, 10, 9, 8, 0), seven, weekdays))
        assertTrue(NotificationSchedule.isTooLate(at(2026, 10, 10, 7, 5), seven, weekdays))
        assertEquals(at(2026, 10, 9, 7), NotificationSchedule.previousRun(at(2026, 10, 10, 7, 5), seven, weekdays))
    }

    @Test
    fun `after a time-zone change the next run is at the local time in the new zone`() {
        // Same instant, seen from Karachi after travelling: 07:00 there, not 07:00 London time.
        val inKarachi = at(2026, 10, 10, 5, 0).withZoneSameInstant(ZoneId.of("Asia/Karachi")) // 09:00 local

        val next = NotificationSchedule.nextRun(inKarachi, seven)!!

        assertEquals(LocalTime.of(7, 0), next.toLocalTime())
        assertEquals(ZoneId.of("Asia/Karachi"), next.zone)
        assertEquals(LocalDate.of(2026, 10, 11), next.toLocalDate())
    }

    @Test
    fun `a run slightly before the time counts against yesterday and is too late`() {
        // A job that somehow ran early at 06:50 is not "today's" summary.
        assertTrue(NotificationSchedule.isTooLate(at(2026, 10, 10, 6, 50), seven))
    }
}
