package com.ah0890.dailyweather.notifications

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/** Timing rules for scheduled notifications. Pure functions so they can be tested without Android. */
object NotificationSchedule {

    /** A notification that would arrive later than this after its intended time is skipped as stale. */
    val LATE_LIMIT: Duration = Duration.ofHours(3)

    val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()

    /**
     * The next time [time] occurs strictly after [now] on one of [days], in [now]'s zone, or null
     * if no day is selected. Uses the calendar, not "now + 24 h", so daylight-saving changes keep
     * the notification at the same wall-clock time.
     */
    fun nextRun(now: ZonedDateTime, time: LocalTime, days: Set<DayOfWeek> = EVERY_DAY): ZonedDateTime? =
        (0L..7L).asSequence()
            .map { now.toLocalDate().plusDays(it) }
            .filter { it.dayOfWeek in days }
            .map { it.atTime(time).atZone(now.zone) }
            .firstOrNull { it.isAfter(now) }

    fun delayUntilNextRun(now: ZonedDateTime, time: LocalTime, days: Set<DayOfWeek> = EVERY_DAY): Duration? =
        nextRun(now, time, days)?.let { Duration.between(now, it) }

    /** The most recent scheduled time at or before [now], or null if no day is selected. */
    fun previousRun(now: ZonedDateTime, time: LocalTime, days: Set<DayOfWeek> = EVERY_DAY): ZonedDateTime? =
        (0L..7L).asSequence()
            .map { now.toLocalDate().minusDays(it) }
            .filter { it.dayOfWeek in days }
            .map { it.atTime(time).atZone(now.zone) }
            .firstOrNull { !it.isAfter(now) }

    /**
     * Whether a run at [now] is too late to be useful, e.g. the device was off all morning or
     * the job was delayed into a day that isn't selected. Measured from the most recent scheduled
     * time at or before [now].
     */
    fun isTooLate(now: ZonedDateTime, time: LocalTime, days: Set<DayOfWeek> = EVERY_DAY): Boolean {
        val lastRun = previousRun(now, time, days) ?: return true
        return Duration.between(lastRun, now) > LATE_LIMIT
    }
}
