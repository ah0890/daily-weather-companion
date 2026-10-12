package com.ah0890.dailyweather.presentation.settings

import com.ah0890.dailyweather.data.local.DataStorePreferencesRepository
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.repository.PreferencesRepository
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.notifications.NotificationKind
import com.ah0890.dailyweather.notifications.NotificationScheduler
import com.ah0890.dailyweather.testing.FakeNotificationScheduler
import com.ah0890.dailyweather.testing.FakeNotificationScheduler.Scheduled
import com.ah0890.dailyweather.testing.InMemoryPreferencesDataStore
import com.ah0890.dailyweather.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val scheduler = FakeNotificationScheduler()
    private val morning = NotificationKind.MorningSummary
    private val evening = NotificationKind.EveningCommute
    private val replace = NotificationScheduler.Policy.Replace
    // Lazy: must be created after MainDispatcherRule has installed the test dispatcher.
    private val vm by lazy { SettingsViewModel(repository, scheduler) }

    @Test
    fun `display and commute settings are saved`() = runTest {
        vm.setTemperatureUnit(TemperatureUnit.Fahrenheit)
        vm.setThemeMode(ThemeMode.Light)
        vm.setMorningCommute(LocalTime.of(6, 30))
        vm.setEveningCommute(LocalTime.of(16, 0))
        advanceUntilIdle()

        val saved = repository.preferences.first()
        assertEquals(TemperatureUnit.Fahrenheit, saved.temperatureUnit)
        assertEquals(ThemeMode.Light, saved.themeMode)
        assertEquals(LocalTime.of(6, 30), saved.commuteTimes.morning)
        assertEquals(LocalTime.of(16, 0), saved.commuteTimes.evening)
    }

    @Test
    fun `turning the summary off cancels it`() = runTest {
        vm.setDailyNotificationEnabled(false)
        advanceUntilIdle()

        assertFalse(repository.preferences.first().dailyNotificationEnabled)
        assertEquals(listOf(morning), scheduler.cancelled)
    }

    @Test
    fun `turning the summary on schedules it at the saved time`() = runTest {
        repository.setDailyNotificationTime(LocalTime.of(6, 15))

        vm.setDailyNotificationEnabled(true)
        advanceUntilIdle()

        assertTrue(repository.preferences.first().dailyNotificationEnabled)
        assertEquals(listOf(Scheduled(morning, LocalTime.of(6, 15), UserPreferences.WEEKDAYS, replace)), scheduler.scheduled)
    }

    @Test
    fun `changing the time reschedules an enabled summary`() = runTest {
        vm.setDailyNotificationTime(LocalTime.of(6, 45))
        advanceUntilIdle()

        assertEquals(LocalTime.of(6, 45), repository.preferences.first().dailyNotificationTime)
        assertEquals(listOf(Scheduled(morning, LocalTime.of(6, 45), UserPreferences.WEEKDAYS, replace)), scheduler.scheduled)
    }

    @Test
    fun `changing the time of a disabled summary does not schedule it`() = runTest {
        repository.setDailyNotificationEnabled(false)

        vm.setDailyNotificationTime(LocalTime.of(6, 45))
        advanceUntilIdle()

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `changing the days reschedules on those days`() = runTest {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)

        vm.setDailyNotificationDays(days)
        advanceUntilIdle()

        assertEquals(days, repository.preferences.first().dailyNotificationDays)
        assertEquals(listOf(Scheduled(morning, LocalTime.of(7, 0), days, replace)), scheduler.scheduled)
    }

    @Test
    fun `selecting no days cancels the notification`() = runTest {
        vm.setDailyNotificationDays(emptySet())
        advanceUntilIdle()

        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf(morning), scheduler.cancelled)
    }

    @Test
    fun `the evening alert is scheduled separately`() = runTest {
        vm.setEveningAlertTime(LocalTime.of(16, 15))
        vm.setEveningAlertDays(UserPreferences.EVERY_DAY)
        advanceUntilIdle()

        assertEquals(
            listOf(
                Scheduled(evening, LocalTime.of(16, 15), UserPreferences.WEEKDAYS, replace),
                Scheduled(evening, LocalTime.of(16, 15), UserPreferences.EVERY_DAY, replace),
            ),
            scheduler.scheduled,
        )

        vm.setEveningAlertEnabled(false)
        advanceUntilIdle()

        assertEquals(listOf(evening), scheduler.cancelled)
    }

    @Test
    fun `office days and transport are saved without touching notifications`() = runTest {
        vm.setWorkdays(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY))
        vm.setTransportMode(TransportMode.Car)
        advanceUntilIdle()

        val saved = repository.preferences.first()
        assertEquals(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), saved.workdays)
        assertEquals(TransportMode.Car, saved.transportMode)
        assertTrue(scheduler.scheduled.isEmpty() && scheduler.cancelled.isEmpty())
    }

    @Test
    fun `answering the prompt is remembered`() = runTest {
        vm.dismissNotificationPrompt()
        advanceUntilIdle()

        assertTrue(repository.preferences.first().notificationPromptDismissed)
    }

    @Test
    fun `a storage error while saving does not crash`() = runTest {
        val failing = object : PreferencesRepository by repository {
            override suspend fun setThemeMode(mode: ThemeMode) = throw IOException("disk full")
        }
        val vm = SettingsViewModel(failing, scheduler)

        vm.setThemeMode(ThemeMode.Dark)
        advanceUntilIdle()

        assertEquals(ThemeMode.System, repository.preferences.first().themeMode)
    }
}
