package com.ah0890.dailyweather.widget

import com.ah0890.dailyweather.domain.model.LegAdvice
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.usecase.AlertLevel
import com.ah0890.dailyweather.domain.usecase.OfficeDayPlanner
import com.ah0890.dailyweather.domain.usecase.RainAnalysis
import com.ah0890.dailyweather.domain.usecase.SevereWeatherAnalysis
import com.ah0890.dailyweather.domain.usecase.WeatherAlert
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * Everything the home-screen widget shows, worked out from cached forecasts only. Kept free of
 * Android types so the choice of what to show can be unit-tested.
 *
 * @property commute the next trip worth mentioning: the first one that isn't favourable, else the
 *   next one; null on days off or without forecast coverage.
 * @property alert the first severe alert in the next 24 hours, if any.
 * @property outdated the cached forecast is older than [OUTDATED_AFTER].
 */
data class WidgetSnapshot(
    val locationName: String?,
    val temperatureC: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val rainChance: Int?,
    val umbrella: UmbrellaAdvice,
    val commute: LegAdvice?,
    val alert: WeatherAlert?,
    val outdated: Boolean,
) {
    companion object {
        val OUTDATED_AFTER: Duration = Duration.ofHours(6)

        /**
         * @param forecast the forecast for the widget's place; [homeForecast]/[officeForecast] are
         *   used for the commute when those places are saved.
         * @param now the current instant; the forecast is moved forward to it.
         */
        fun from(
            forecast: WeatherForecast,
            fetchedAt: Instant,
            locationName: String?,
            preferences: UserPreferences,
            now: Instant,
            homeForecast: WeatherForecast? = null,
            officeForecast: WeatherForecast? = null,
        ): WidgetSnapshot {
            fun WeatherForecast.atNow() = advancedTo(LocalDateTime.ofInstant(now, timeZone))
            val current = forecast.atNow()
            val outlook = RainAnalysis.outlookForRestOfToday(current)
            val officeDay = OfficeDayPlanner.plan(
                (homeForecast ?: forecast).atNow(),
                preferences.commuteTimes,
                preferences.transportMode,
                preferences.workdays,
                (officeForecast ?: forecast).atNow(),
            )
            val todayLegs = officeDay?.summary?.takeIf { it.isToday }?.legs.orEmpty()
            return WidgetSnapshot(
                locationName = locationName,
                temperatureC = current.current.temperatureC,
                condition = current.current.condition,
                isDay = current.current.isDay,
                rainChance = outlook.rain.maxProbability,
                umbrella = outlook.recommendation.umbrella,
                commute = officeDay?.advice?.let { advice -> advice.firstOrNull { it.rating != TravelRating.Favorable } ?: advice.first() },
                alert = SevereWeatherAnalysis.alerts(current, todayLegs).firstOrNull { it.level == AlertLevel.Severe },
                outdated = Duration.between(fetchedAt, now) > OUTDATED_AFTER,
            )
        }
    }
}
