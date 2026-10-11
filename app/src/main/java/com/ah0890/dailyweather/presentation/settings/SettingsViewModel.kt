package com.ah0890.dailyweather.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ah0890.dailyweather.DailyWeatherApplication
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.repository.PreferencesRepository
import com.ah0890.dailyweather.notifications.NotificationScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

class SettingsViewModel(
    private val repository: PreferencesRepository,
    private val scheduler: NotificationScheduler,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setTemperatureUnit(unit: TemperatureUnit) = launch { repository.setTemperatureUnit(unit) }

    fun setThemeMode(mode: ThemeMode) = launch { repository.setThemeMode(mode) }

    fun setMorningCommute(time: LocalTime) = launch { repository.setMorningCommute(time) }

    fun setEveningCommute(time: LocalTime) = launch { repository.setEveningCommute(time) }

    fun setDailyNotificationEnabled(enabled: Boolean) = launch {
        repository.setDailyNotificationEnabled(enabled)
        if (enabled) {
            scheduler.schedule(repository.preferences.first().dailyNotificationTime, NotificationScheduler.Policy.Replace)
        } else {
            scheduler.cancel()
        }
    }

    fun setDailyNotificationTime(time: LocalTime) = launch {
        repository.setDailyNotificationTime(time)
        if (repository.preferences.first().dailyNotificationEnabled) {
            scheduler.schedule(time, NotificationScheduler.Policy.Replace)
        }
    }

    /** The one-time prompt on the Today screen was answered either way. */
    fun dismissNotificationPrompt() = launch { repository.setNotificationPromptDismissed() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DailyWeatherApplication).container
                SettingsViewModel(container.preferencesRepository, container.notificationScheduler)
            }
        }
    }
}
