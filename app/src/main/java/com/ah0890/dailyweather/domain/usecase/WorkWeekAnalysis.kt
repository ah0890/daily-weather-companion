package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteSummary
import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.OfficeWeatherScore
import com.ah0890.dailyweather.domain.model.ScoreBand
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherHazard
import java.time.DayOfWeek
import java.time.LocalDate

/** A one-glance verdict for a workday's commute in the 7-day list. */
enum class CommuteStatus { Good, TakeCare, CarryUmbrella, HighRainRisk, Difficult }

data class DayCommute(val date: LocalDate, val score: OfficeWeatherScore, val status: CommuteStatus)

/**
 * Commute outlook for the workdays in the forecast.
 *
 * @property best the workday with the highest score, if at least two days could be compared and
 *   they differ.
 * @property riskiest the workday with the lowest score, under the same conditions and only when
 *   it isn't rated good anyway.
 */
data class WorkWeek(
    val days: Map<LocalDate, DayCommute>,
    val best: LocalDate?,
    val riskiest: LocalDate?,
)

/** Rates each workday of the forecast so the 7-day screen can help plan the week. */
object WorkWeekAnalysis {

    private val WET = setOf(WeatherHazard.RainLikely, WeatherHazard.HeavyRain, WeatherHazard.Thunderstorm)

    fun analyse(
        forecast: WeatherForecast,
        times: CommuteTimes,
        mode: TransportMode,
        workdays: Set<DayOfWeek>,
    ): WorkWeek {
        val days = forecast.daily.map { it.date }
            .filter { it.dayOfWeek in workdays }
            .mapNotNull { date -> analyseDay(forecast, date, times, mode) }
            .associateBy { it.date }

        val ranked = days.values.sortedWith(compareByDescending<DayCommute> { it.score.value }.thenBy { it.date })
        val comparable = ranked.size >= 2 && ranked.first().score.value != ranked.last().score.value
        return WorkWeek(
            days = days,
            best = ranked.firstOrNull()?.takeIf { comparable }?.date,
            riskiest = ranked.lastOrNull()?.takeIf { comparable && it.score.band != ScoreBand.Good }?.date,
        )
    }

    /** Null unless the forecast covers both trips, so partial days aren't compared with full ones. */
    private fun analyseDay(forecast: WeatherForecast, date: LocalDate, times: CommuteTimes, mode: TransportMode): DayCommute? {
        val legs = CommuteAnalysis.legsOn(forecast, date, times)
        if (legs.size < 2) return null
        val summary = CommuteSummary(date, isToday = false, legs = legs, advice = CommuteAnalysis.advice(legs))
        val workday = CommuteAnalysis.workdayHours(forecast, summary)
        val advice = legs.map { TravelAdvisor.advise(it, mode) }
        val hazards = legs.flatMap { it.hazards }.toSet()
        val wet = hazards.any { it in WET }

        val status = when {
            advice.any { it.rating == TravelRating.Unfavorable } -> if (wet) CommuteStatus.HighRainRisk else CommuteStatus.Difficult
            wet || WeatherHazard.RainPossible in hazards -> CommuteStatus.CarryUmbrella
            advice.any { it.rating == TravelRating.Caution } -> CommuteStatus.TakeCare
            else -> CommuteStatus.Good
        }
        return DayCommute(date, OfficeScore.of(summary, workday), status)
    }
}
