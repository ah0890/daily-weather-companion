package com.ah0890.dailyweather.wear

import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.LegAdvice
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.usecase.AlertLevel
import com.ah0890.dailyweather.domain.usecase.OfficeDayPlanner
import com.ah0890.dailyweather.domain.usecase.RainAnalysis
import com.ah0890.dailyweather.domain.usecase.RainSummary
import com.ah0890.dailyweather.domain.usecase.SevereWeatherAnalysis
import com.ah0890.dailyweather.domain.usecase.WeatherAlert
import java.time.LocalDateTime

/**
 * What the watch shows, using the same rules as the phone. The watch has no settings of its own,
 * so the commute uses the phone app's defaults (8:00 and 17:00, Monday to Friday).
 *
 * @property nextTrip the next commute trip, preferring one that isn't favourable.
 */
data class WatchSummary(
    val temperatureC: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val rainChance: Int?,
    val rain: RainSummary,
    val umbrella: UmbrellaAdvice,
    val nextTrip: LegAdvice?,
    val alert: WeatherAlert?,
) {
    companion object {
        fun from(forecast: WeatherForecast, now: LocalDateTime): WatchSummary {
            val current = forecast.advancedTo(now)
            val outlook = RainAnalysis.outlookForRestOfToday(current)
            val officeDay = OfficeDayPlanner.plan(current, CommuteTimes(), TransportMode.Other, UserPreferences.WEEKDAYS)
            val legs = officeDay?.summary?.takeIf { it.isToday }?.legs.orEmpty()
            return WatchSummary(
                temperatureC = current.current.temperatureC,
                condition = current.current.condition,
                isDay = current.current.isDay,
                rainChance = outlook.rain.maxProbability,
                rain = RainAnalysis.summary(outlook.rain, current.current.time),
                umbrella = outlook.recommendation.umbrella,
                nextTrip = officeDay?.advice?.let { advice -> advice.firstOrNull { it.rating != TravelRating.Favorable } ?: advice.first() },
                alert = SevereWeatherAnalysis.alerts(current, legs).firstOrNull { it.level == AlertLevel.Severe },
            )
        }
    }
}
