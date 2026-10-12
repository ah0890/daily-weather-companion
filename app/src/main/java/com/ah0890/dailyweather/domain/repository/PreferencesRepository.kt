package com.ah0890.dailyweather.domain.repository

import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.model.UvCategory
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalTime

interface PreferencesRepository {

    /** Current preferences; emits again whenever one changes. */
    val preferences: Flow<UserPreferences>

    suspend fun setTemperatureUnit(unit: TemperatureUnit)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setMorningCommute(time: LocalTime)

    suspend fun setEveningCommute(time: LocalTime)

    suspend fun setTransportMode(mode: TransportMode)

    suspend fun setDailyNotificationEnabled(enabled: Boolean)

    suspend fun setDailyNotificationTime(time: LocalTime)

    suspend fun setDailyNotificationDays(days: Set<DayOfWeek>)

    suspend fun setWorkdays(days: Set<DayOfWeek>)

    suspend fun setEveningAlertEnabled(enabled: Boolean)

    suspend fun setEveningAlertTime(time: LocalTime)

    suspend fun setEveningAlertDays(days: Set<DayOfWeek>)

    suspend fun setSevereAlertsEnabled(enabled: Boolean)

    suspend fun setUvAlertEnabled(enabled: Boolean)

    suspend fun setUvAlertLevel(level: UvCategory)

    suspend fun setNotificationPromptDismissed()
}
