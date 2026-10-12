package com.ah0890.dailyweather.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** When the user usually leaves for work and for home. */
data class CommuteTimes(
    val morning: LocalTime = LocalTime.of(8, 0),
    val evening: LocalTime = LocalTime.of(17, 0),
)

enum class CommuteLegType { Morning, Evening }

/** How wet a commute leg is likely to be. */
enum class CommuteRain { Dry, Possible, Likely }

/**
 * Conditions for one trip, taken over the departure hour and the hour after it.
 *
 * @property rainChance highest hourly chance of rain over the trip window, or null if unknown.
 * @property rainPeakTime the hour with [rainChance], or null if unknown.
 * @property windSpeedKmh strongest sustained wind over the trip window, or null if unknown.
 * @property windGustsKmh strongest gust over the trip window, or null if unknown.
 * @property visibilityMeters lowest visibility over the trip window, or null if unknown.
 * @property feelsLikeC feels-like temperature at departure.
 * @property uvIndex highest UV index over the trip window, or null if unknown.
 * @property hazards everything noteworthy over the trip window, see [WeatherHazard].
 */
data class CommuteLeg(
    val type: CommuteLegType,
    val departure: LocalDateTime,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val temperatureC: Double,
    val rainChance: Int?,
    val windSpeedKmh: Double?,
    val rain: CommuteRain,
    val windy: Boolean,
    val windGustsKmh: Double? = null,
    val rainPeakTime: LocalDateTime? = null,
    val visibilityMeters: Double? = null,
    val feelsLikeC: Double = temperatureC,
    val uvIndex: Double? = null,
    val hazards: Set<WeatherHazard> = emptySet(),
)

/** Overall advice for the day's commute, most important concern first. */
enum class CommuteAdvice { RainLikely, RainPossible, Windy, Dry }

data class CommuteSummary(
    val date: LocalDate,
    val isToday: Boolean,
    val legs: List<CommuteLeg>,
    val advice: CommuteAdvice,
)
