package com.ah0890.dailyweather.notifications

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/** Timing rules for the morning summary. Pure functions so they can be tested without Android. */
object NotificationSchedule {

    /** A summary that would arrive later than this after its intended time is skipped as stale. */
    val LATE_LIMIT: Duration = Duration.ofHours(3)

    /**
     * The next time [time] occurs strictly after [now], in [now]'s zone. Uses the calendar, not
     * "now + 24 h", so daylight-saving changes keep the summary at the same wall-clock time.
     */
    fun nextRun(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }

    fun delayUntilNextRun(now: ZonedDateTime, time: LocalTime): Duration = Duration.between(now, nextRun(now, time))

    /**
     * Whether a run at [now] is too late to be useful, e.g. the device was off all morning.
     * Measured from the most recent scheduled time at or before [now].
     */
    fun isTooLate(now: ZonedDateTime, time: LocalTime): Boolean {
        val todayRun = now.toLocalDate().atTime(time).atZone(now.zone)
        val lastRun = if (todayRun.isAfter(now)) todayRun.minusDays(1) else todayRun
        return Duration.between(lastRun, now) > LATE_LIMIT
    }
}
