package com.ah0890.dailyweather.notifications

import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.testing.FakeNotificationScheduler
import com.ah0890.dailyweather.testing.FakeNotificationScheduler.Scheduled
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

/** What app start, settings changes and time-zone changes do with the user's notification settings. */
class NotificationSyncTest {

    private val scheduler = FakeNotificationScheduler()
    private val keep = NotificationScheduler.Policy.KeepExisting

    @Test
    fun `defaults schedule both notifications on weekdays`() {
        scheduler.syncAll(UserPreferences(), keep)

        assertEquals(
            listOf(
                Scheduled(NotificationKind.MorningSummary, LocalTime.of(7, 0), UserPreferences.WEEKDAYS, keep),
                Scheduled(NotificationKind.EveningCommute, LocalTime.of(15, 30), UserPreferences.WEEKDAYS, keep),
            ),
            scheduler.scheduled,
        )
    }

    @Test
    fun `switched-off notifications are cancelled`() {
        scheduler.syncAll(UserPreferences(dailyNotificationEnabled = false, eveningAlertEnabled = false), keep)

        assertEquals(emptyList<Scheduled>(), scheduler.scheduled)
        assertEquals(listOf(NotificationKind.MorningSummary, NotificationKind.EveningCommute), scheduler.cancelled)
    }

    @Test
    fun `enabled with no days is treated as off`() {
        val prefs = UserPreferences(eveningAlertDays = emptySet())

        scheduler.sync(NotificationKind.EveningCommute, prefs, keep)

        assertEquals(listOf(NotificationKind.EveningCommute), scheduler.cancelled)
        assertEquals(false, prefs.timing(NotificationKind.EveningCommute).active)
    }

    @Test
    fun `custom times and days are passed on`() {
        val prefs = UserPreferences(dailyNotificationTime = LocalTime.of(6, 20), dailyNotificationDays = UserPreferences.EVERY_DAY)

        scheduler.sync(NotificationKind.MorningSummary, prefs, NotificationScheduler.Policy.Replace)

        assertEquals(
            Scheduled(NotificationKind.MorningSummary, LocalTime.of(6, 20), UserPreferences.EVERY_DAY, NotificationScheduler.Policy.Replace),
            scheduler.scheduled.single(),
        )
    }
}
