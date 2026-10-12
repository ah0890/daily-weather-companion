package com.ah0890.dailyweather.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class DataStorePreferencesRepositoryTest {

    @Test
    fun `defaults apply when nothing is stored`() = runTest {
        val repository = DataStorePreferencesRepository(InMemoryPreferencesDataStore())

        assertEquals(UserPreferences(), repository.preferences.first())
    }

    @Test
    fun `changes are stored and read back`() = runTest {
        val repository = DataStorePreferencesRepository(InMemoryPreferencesDataStore())

        repository.setTemperatureUnit(TemperatureUnit.Fahrenheit)
        repository.setThemeMode(ThemeMode.Dark)
        repository.setMorningCommute(LocalTime.of(7, 15))
        repository.setEveningCommute(LocalTime.of(18, 45))
        repository.setTransportMode(TransportMode.Motorcycle)

        assertEquals(
            UserPreferences(
                temperatureUnit = TemperatureUnit.Fahrenheit,
                themeMode = ThemeMode.Dark,
                commuteTimes = CommuteTimes(LocalTime.of(7, 15), LocalTime.of(18, 45)),
                transportMode = TransportMode.Motorcycle,
            ),
            repository.preferences.first(),
        )
    }

    @Test
    fun `day sets and the evening alert are stored and read back`() = runTest {
        val repository = DataStorePreferencesRepository(InMemoryPreferencesDataStore())

        repository.setWorkdays(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY))
        repository.setDailyNotificationDays(UserPreferences.EVERY_DAY)
        repository.setEveningAlertEnabled(false)
        repository.setEveningAlertTime(LocalTime.of(16, 10))
        repository.setEveningAlertDays(emptySet())
        repository.setSevereAlertsEnabled(false)

        val saved = repository.preferences.first()
        assertEquals(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), saved.workdays)
        assertEquals(UserPreferences.EVERY_DAY, saved.dailyNotificationDays)
        assertEquals(false, saved.eveningAlertEnabled)
        assertEquals(LocalTime.of(16, 10), saved.eveningAlertTime)
        assertEquals(emptySet<DayOfWeek>(), saved.eveningAlertDays)
        assertEquals(false, saved.severeAlertsEnabled)
    }

    @Test
    fun `weekdays are the default schedule`() = runTest {
        val saved = DataStorePreferencesRepository(InMemoryPreferencesDataStore()).preferences.first()

        assertEquals(UserPreferences.WEEKDAYS, saved.workdays)
        assertEquals(UserPreferences.WEEKDAYS, saved.dailyNotificationDays)
        assertEquals(UserPreferences.WEEKDAYS, saved.eveningAlertDays)
        assertEquals(LocalTime.of(15, 30), saved.eveningAlertTime)
    }

    @Test
    fun `unknown or invalid stored values fall back to defaults`() = runTest {
        val store = InMemoryPreferencesDataStore()
        store.edit {
            it[stringPreferencesKey("temperature_unit")] = "Kelvin"
            it[stringPreferencesKey("theme_mode")] = ""
            it[stringPreferencesKey("transport_mode")] = "Hovercraft"
            it[intPreferencesKey("workdays")] = 1 shl 7
            it[intPreferencesKey("daily_notification_days")] = -1
            it[intPreferencesKey("commute_morning_minutes")] = -5
            it[intPreferencesKey("commute_evening_minutes")] = 24 * 60
        }

        assertEquals(UserPreferences(), DataStorePreferencesRepository(store).preferences.first())
    }
}
