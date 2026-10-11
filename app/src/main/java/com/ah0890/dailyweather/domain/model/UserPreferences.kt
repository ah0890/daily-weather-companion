package com.ah0890.dailyweather.domain.model

enum class TemperatureUnit { Celsius, Fahrenheit }

enum class ThemeMode { System, Light, Dark }

/** Everything the user can configure. Defaults apply until something is changed. */
data class UserPreferences(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    val themeMode: ThemeMode = ThemeMode.System,
    val commuteTimes: CommuteTimes = CommuteTimes(),
)
