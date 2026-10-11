package com.ah0890.dailyweather.domain.repository

import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

interface PreferencesRepository {

    /** Current preferences; emits again whenever one changes. */
    val preferences: Flow<UserPreferences>

    suspend fun setTemperatureUnit(unit: TemperatureUnit)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setMorningCommute(time: LocalTime)

    suspend fun setEveningCommute(time: LocalTime)
}
