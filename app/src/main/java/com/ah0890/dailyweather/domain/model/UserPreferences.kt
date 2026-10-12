package com.ah0890.dailyweather.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

enum class TemperatureUnit { Celsius, Fahrenheit }

enum class ThemeMode { System, Light, Dark }

/** Everything the user can configure. Defaults apply until something is changed. */
data class UserPreferences(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    val themeMode: ThemeMode = ThemeMode.System,
    val commuteTimes: CommuteTimes = CommuteTimes(),
    val transportMode: TransportMode = TransportMode.Other,
    /** Days the user goes to the office; the Office day card and 7-day indicators use these. */
    val workdays: Set<DayOfWeek> = WEEKDAYS,
    val dailyNotificationEnabled: Boolean = true,
    val dailyNotificationTime: LocalTime = LocalTime.of(7, 0),
    val dailyNotificationDays: Set<DayOfWeek> = WEEKDAYS,
    /** Afternoon alert about the trip home; only posted when there is something to act on. */
    val eveningAlertEnabled: Boolean = true,
    val eveningAlertTime: LocalTime = LocalTime.of(15, 30),
    val eveningAlertDays: Set<DayOfWeek> = WEEKDAYS,
    /** Separate notification when severe weather is forecast, checked with each scheduled run. */
    val severeAlertsEnabled: Boolean = true,
    /** The one-time "turn on morning summaries" prompt on the Today screen was answered. */
    val notificationPromptDismissed: Boolean = false,
) {
    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}

/** Quick choices for a set of days; anything else is [Custom]. */
enum class DayPreset {
    EveryDay,
    Weekdays,
    Weekends,
    Custom;

    companion object {
        fun of(days: Set<DayOfWeek>): DayPreset = when (days) {
            UserPreferences.EVERY_DAY -> EveryDay
            UserPreferences.WEEKDAYS -> Weekdays
            UserPreferences.WEEKEND -> Weekends
            else -> Custom
        }
    }

    /** The days this preset stands for, or null for [Custom]. */
    val days: Set<DayOfWeek>?
        get() = when (this) {
            EveryDay -> UserPreferences.EVERY_DAY
            Weekdays -> UserPreferences.WEEKDAYS
            Weekends -> UserPreferences.WEEKEND
            Custom -> null
        }
}
