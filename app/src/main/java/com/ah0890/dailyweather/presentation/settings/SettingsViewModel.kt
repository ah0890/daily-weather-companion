package com.ah0890.dailyweather.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ah0890.dailyweather.DailyWeatherApplication
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.repository.PreferencesRepository
import com.ah0890.dailyweather.domain.model.UvCategory
import com.ah0890.dailyweather.notifications.NotificationKind
import com.ah0890.dailyweather.notifications.NotificationScheduler
import com.ah0890.dailyweather.notifications.sync
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.DayOfWeek
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

    fun setTransportMode(mode: TransportMode) = launch { repository.setTransportMode(mode) }

    fun setWorkdays(days: Set<DayOfWeek>) = launch { repository.setWorkdays(days) }

    fun setDailyNotificationEnabled(enabled: Boolean) =
        updateNotification(NotificationKind.MorningSummary) { repository.setDailyNotificationEnabled(enabled) }

    fun setDailyNotificationTime(time: LocalTime) =
        updateNotification(NotificationKind.MorningSummary) { repository.setDailyNotificationTime(time) }

    fun setDailyNotificationDays(days: Set<DayOfWeek>) =
        updateNotification(NotificationKind.MorningSummary) { repository.setDailyNotificationDays(days) }

    fun setEveningAlertEnabled(enabled: Boolean) =
        updateNotification(NotificationKind.EveningCommute) { repository.setEveningAlertEnabled(enabled) }

    fun setEveningAlertTime(time: LocalTime) =
        updateNotification(NotificationKind.EveningCommute) { repository.setEveningAlertTime(time) }

    fun setEveningAlertDays(days: Set<DayOfWeek>) =
        updateNotification(NotificationKind.EveningCommute) { repository.setEveningAlertDays(days) }

    fun setSevereAlertsEnabled(enabled: Boolean) = launch { repository.setSevereAlertsEnabled(enabled) }

    fun setUvAlertEnabled(enabled: Boolean) = launch { repository.setUvAlertEnabled(enabled) }

    fun setUvAlertLevel(level: UvCategory) = launch { repository.setUvAlertLevel(level) }

    /** The one-time prompt on the Today screen was answered either way. */
    fun dismissNotificationPrompt() = launch { repository.setNotificationPromptDismissed() }

    /** Saves a change, then re-schedules (or cancels) that notification to match. */
    private fun updateNotification(kind: NotificationKind, change: suspend () -> Unit) = launch {
        change()
        scheduler.sync(kind, repository.preferences.first(), NotificationScheduler.Policy.Replace)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: IOException) {
                // Storage problem: the previous value stays in effect and the UI keeps showing it.
            }
        }
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
