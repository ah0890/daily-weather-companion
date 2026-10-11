package com.ah0890.dailyweather.presentation.settings

import com.ah0890.dailyweather.data.local.DataStorePreferencesRepository
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.repository.PreferencesRepository
import com.ah0890.dailyweather.notifications.NotificationScheduler
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
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeScheduler : NotificationScheduler {
        val scheduled = mutableListOf<Pair<LocalTime, NotificationScheduler.Policy>>()
        var cancelled = 0
        override fun schedule(time: LocalTime, policy: NotificationScheduler.Policy) {
            scheduled += time to policy
        }
        override fun cancel() {
            cancelled++
        }
    }

    private val repository = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val scheduler = FakeScheduler()
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
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun `turning the summary on schedules it at the saved time`() = runTest {
        repository.setDailyNotificationTime(LocalTime.of(6, 15))

        vm.setDailyNotificationEnabled(true)
        advanceUntilIdle()

        assertTrue(repository.preferences.first().dailyNotificationEnabled)
        assertEquals(listOf(LocalTime.of(6, 15) to NotificationScheduler.Policy.Replace), scheduler.scheduled)
    }

    @Test
    fun `changing the time reschedules an enabled summary`() = runTest {
        vm.setDailyNotificationTime(LocalTime.of(6, 45))
        advanceUntilIdle()

        assertEquals(LocalTime.of(6, 45), repository.preferences.first().dailyNotificationTime)
        assertEquals(listOf(LocalTime.of(6, 45) to NotificationScheduler.Policy.Replace), scheduler.scheduled)
    }

    @Test
    fun `changing the time of a disabled summary does not schedule it`() = runTest {
        repository.setDailyNotificationEnabled(false)

        vm.setDailyNotificationTime(LocalTime.of(6, 45))
        advanceUntilIdle()

        assertTrue(scheduler.scheduled.isEmpty())
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
