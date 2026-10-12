package com.ah0890.dailyweather.testing

import com.ah0890.dailyweather.notifications.NotificationKind
import com.ah0890.dailyweather.notifications.NotificationScheduler
import java.time.DayOfWeek
import java.time.LocalTime

/** Records what would have been scheduled or cancelled. */
class FakeNotificationScheduler : NotificationScheduler {

    data class Scheduled(
        val kind: NotificationKind,
        val time: LocalTime,
        val days: Set<DayOfWeek>,
        val policy: NotificationScheduler.Policy,
    )

    val scheduled = mutableListOf<Scheduled>()
    val cancelled = mutableListOf<NotificationKind>()

    override fun schedule(kind: NotificationKind, time: LocalTime, days: Set<DayOfWeek>, policy: NotificationScheduler.Policy) {
        scheduled += Scheduled(kind, time, days, policy)
    }

    override fun cancel(kind: NotificationKind) {
        cancelled += kind
    }
}
