package com.ah0890.dailyweather.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// All values are metric (°C, km/h, mm, metres). Conversion to the user's preferred units
// happens at display time so cached data stays unit-independent.
// Times are local to the forecast location, described by [WeatherForecast.timeZone].

/** A complete forecast for one location: conditions now, the next hours and the next days. */
data class WeatherForecast(
    val coordinates: Coordinates,
    val timeZone: ZoneId,
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
) {
    /** Today's summary at the forecast location, falling back to the first day available. */
    val today: DailyForecast?
        get() = daily.firstOrNull { it.date == current.time.toLocalDate() } ?: daily.firstOrNull()

    /** Hourly entries starting with the current hour, at most [count] of them. */
    fun upcomingHours(count: Int = 24): List<HourlyForecast> {
        val thisHour = current.time.truncatedTo(ChronoUnit.HOURS)
        return hourly.filter { !it.time.isBefore(thisHour) }.take(count)
    }
}

data class CurrentWeather(
    val time: LocalDateTime,
    val condition: WeatherCondition,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val humidityPercent: Int?,
    val windSpeedKmh: Double?,
    val windDirectionDegrees: Int?,
    val precipitationMm: Double?,
    val isDay: Boolean,
    val uvIndex: Double?,
    val visibilityMeters: Double?,
)

data class HourlyForecast(
    val time: LocalDateTime,
    val condition: WeatherCondition,
    val temperatureC: Double,
    /** Chance of precipitation in percent (0–100), or null when the provider has no value. */
    val precipitationProbability: Int?,
    val precipitationMm: Double?,
    val windSpeedKmh: Double?,
    val isDay: Boolean,
)

data class DailyForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val maxTemperatureC: Double,
    val minTemperatureC: Double,
    /** Highest hourly chance of precipitation during the day, in percent (0–100). */
    val precipitationProbabilityMax: Int?,
    val precipitationSumMm: Double?,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val uvIndexMax: Double?,
    val windSpeedMaxKmh: Double?,
)
