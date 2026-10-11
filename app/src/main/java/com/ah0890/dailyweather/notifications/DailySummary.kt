package com.ah0890.dailyweather.notifications

import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.usecase.RainAnalysis
import com.ah0890.dailyweather.domain.usecase.RainSummary
import java.time.LocalDateTime

/** Language-independent content of the morning notification. */
data class DailySummary(
    val locationName: String?,
    val condition: WeatherCondition,
    val highC: Double?,
    val lowC: Double?,
    val rainChance: Int?,
    val rain: RainSummary,
    val umbrella: UmbrellaAdvice,
) {
    companion object {
        /** Summary of [forecast] as seen at [now] (location-local), for the rest of that day. */
        fun from(forecast: WeatherForecast, locationName: String?, now: LocalDateTime): DailySummary {
            val current = forecast.advancedTo(now)
            val outlook = RainAnalysis.outlookForRestOfToday(current)
            val today = current.today
            return DailySummary(
                locationName = locationName,
                // The day's overall condition reads better in the morning than the current hour's.
                condition = today?.condition ?: current.current.condition,
                highC = today?.maxTemperatureC,
                lowC = today?.minTemperatureC,
                rainChance = outlook.rain.maxProbability,
                rain = RainAnalysis.summary(outlook.rain, current.current.time),
                umbrella = outlook.recommendation.umbrella,
            )
        }
    }
}
