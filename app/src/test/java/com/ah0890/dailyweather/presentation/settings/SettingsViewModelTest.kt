package com.ah0890.dailyweather.presentation.settings

import com.ah0890.dailyweather.data.local.DataStorePreferencesRepository
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.testing.InMemoryPreferencesDataStore
import com.ah0890.dailyweather.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `settings changes are saved`() = runTest {
        val repository = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
        val vm = SettingsViewModel(repository)

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
}
