package com.ah0890.dailyweather.domain.model

import java.time.LocalDateTime

/**
 * A stretch of hours in which rain is likely.
 *
 * @property start the first rainy hour.
 * @property end the end of the last rainy hour (exclusive), e.g. 14:00–17:00 covers three hours.
 * @property peakProbability highest hourly chance of rain within the period, in percent.
 */
data class RainPeriod(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val peakProbability: Int,
)

/**
 * Rain summary for a window of hours (typically the rest of today).
 *
 * @property maxProbability highest hourly chance of rain, or null if the provider gave none.
 * @property peakTime the hour with [maxProbability].
 * @property totalMm expected rain over the window, or null if no amounts were provided.
 * @property periods hours where rain is likely, merged into continuous periods.
 */
data class RainInformation(
    val maxProbability: Int?,
    val peakTime: LocalDateTime?,
    val totalMm: Double?,
    val periods: List<RainPeriod>,
)
