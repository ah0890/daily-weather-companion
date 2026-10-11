package com.ah0890.dailyweather.domain.model

import java.time.LocalTime

enum class TemperatureUnit { Celsius, Fahrenheit }

enum class ThemeMode { System, Light, Dark }

/** Everything the user can configure. Defaults apply until something is changed. */
data class UserPreferences(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    val themeMode: ThemeMode = ThemeMode.System,
    val commuteTimes: CommuteTimes = CommuteTimes(),
    val dailyNotificationEnabled: Boolean = true,
    val dailyNotificationTime: LocalTime = LocalTime.of(7, 0),
    /** The one-time "turn on morning summaries" prompt on the Today screen was answered. */
    val notificationPromptDismissed: Boolean = false,
)
