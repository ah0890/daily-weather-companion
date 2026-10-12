package com.ah0890.dailyweather.notifications

import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.LegAdvice
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.usecase.OfficeDayPlanner
import com.ah0890.dailyweather.domain.usecase.RainAnalysis
import com.ah0890.dailyweather.domain.usecase.RainSummary
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Language-independent content of the morning notification.
 *
 * @property commute advice for today's remaining trips, empty when today isn't a workday.
 */
data class DailySummary(
    val locationName: String?,
    val condition: WeatherCondition,
    val highC: Double?,
    val lowC: Double?,
    val rainChance: Int?,
    val rain: RainSummary,
    val umbrella: UmbrellaAdvice,
    val commute: List<LegAdvice> = emptyList(),
) {
    companion object {
        /**
         * Summary of [forecast] (home) as seen at [now] (location-local), for the rest of that day.
         * The evening trip uses [officeForecast] when the office is elsewhere.
         */
        fun from(
            forecast: WeatherForecast,
            locationName: String?,
            now: LocalDateTime,
            times: CommuteTimes = CommuteTimes(),
            mode: TransportMode = TransportMode.Other,
            workdays: Set<DayOfWeek> = emptySet(),
            officeForecast: WeatherForecast = forecast,
        ): DailySummary {
            val current = forecast.advancedTo(now)
            val outlook = RainAnalysis.outlookForRestOfToday(current)
            val today = current.today
            val officeDay = OfficeDayPlanner.plan(current, times, mode, workdays, officeForecast.advancedTo(now))
                ?.takeIf { it.summary.isToday }
            return DailySummary(
                locationName = locationName,
                // The day's overall condition reads better in the morning than the current hour's.
                condition = today?.condition ?: current.current.condition,
                highC = today?.maxTemperatureC,
                lowC = today?.minTemperatureC,
                rainChance = outlook.rain.maxProbability,
                rain = RainAnalysis.summary(outlook.rain, current.current.time),
                umbrella = outlook.recommendation.umbrella,
                commute = officeDay?.advice.orEmpty(),
            )
        }
    }
}

/**
 * Language-independent content of the afternoon "office return" alert.
 *
 * @property rain when rain is most likely between now and the end of the trip home.
 */
data class EveningAlert(
    val locationName: String?,
    val advice: LegAdvice,
    val rain: RainSummary,
) {
    val rainChance: Int? get() = advice.leg.rainChance

    /** Only worth a notification when the trip home calls for an umbrella, care or a change of plan. */
    val worthNotifying: Boolean
        get() = (rainChance ?: 0) >= RainAnalysis.UMBRELLA_CONSIDER_PERCENT ||
            advice.rating != TravelRating.Favorable ||
            advice.tips.isNotEmpty()

    companion object {
        /** @return null when today has no evening trip left (not a workday, or already over). */
        fun from(
            forecast: WeatherForecast,
            locationName: String?,
            now: LocalDateTime,
            times: CommuteTimes,
            mode: TransportMode,
            workdays: Set<DayOfWeek>,
        ): EveningAlert? {
            val current = forecast.advancedTo(now)
            val officeDay = OfficeDayPlanner.plan(current, times, mode, workdays)?.takeIf { it.summary.isToday } ?: return null
            val evening = officeDay.advice.firstOrNull { it.leg.type == CommuteLegType.Evening } ?: return null
            val thisHour = now.truncatedTo(ChronoUnit.HOURS)
            val tripEnd = evening.leg.departure.truncatedTo(ChronoUnit.HOURS).plusHours(2)
            val untilHome = current.hourly.filter { !it.time.isBefore(thisHour) && it.time.isBefore(tripEnd) }
            return EveningAlert(locationName, evening, RainAnalysis.summary(RainAnalysis.rainInformation(untilHome), now))
        }
    }
}
