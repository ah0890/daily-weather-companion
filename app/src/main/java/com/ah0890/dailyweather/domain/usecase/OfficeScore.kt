package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteSummary
import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.OfficeWeatherScore
import com.ah0890.dailyweather.domain.model.ScoreBand
import com.ah0890.dailyweather.domain.model.WeatherHazard
import kotlin.math.roundToInt

/**
 * The "Office Weather Score": 100 minus penalties for rain on the commute and for hazards on the
 * trips or during the working day. Deliberately simple and transport-independent so it is easy to
 * understand; it summarises the forecast and is not a safety measurement.
 */
object OfficeScore {

    const val GOOD_FROM = 75
    const val MODERATE_FROM = 45

    /** Each percentage point of rain chance on a trip costs this much. */
    private const val RAIN_CHANCE_WEIGHT = 0.3

    /** Hazards during working hours (between the trips) count this fraction of their penalty. */
    private const val DAYTIME_WEIGHT = 0.5

    /** Rain chance is scored separately, and UV doesn't affect getting to work. */
    private val PENALTIES: Map<WeatherHazard, Int> = mapOf(
        WeatherHazard.Thunderstorm to 25,
        WeatherHazard.Gale to 25,
        WeatherHazard.DenseFog to 20,
        WeatherHazard.ExtremeHeat to 20,
        WeatherHazard.Wintry to 20,
        WeatherHazard.HeavyRain to 15,
        WeatherHazard.StrongWind to 10,
        WeatherHazard.PoorVisibility to 10,
        WeatherHazard.Heat to 10,
    )

    /**
     * @param workdayHours hours from the first trip to the end of the last, see
     *   [CommuteAnalysis.workdayHours].
     */
    fun of(summary: CommuteSummary, workdayHours: List<HourlyForecast>): OfficeWeatherScore {
        val rainPenalty = summary.legs.sumOf { (it.rainChance ?: 0) * RAIN_CHANCE_WEIGHT }
        val tripHazards = summary.legs.flatMap { it.hazards }.toSet()
        val daytimeHazards = HazardAnalysis.detect(workdayHours) - tripHazards
        val hazardPenalty = tripHazards.sumOf { PENALTIES[it] ?: 0 } +
            daytimeHazards.sumOf { (PENALTIES[it] ?: 0) * DAYTIME_WEIGHT }

        val value = (100 - rainPenalty - hazardPenalty).roundToInt().coerceIn(0, 100)
        return OfficeWeatherScore(value, band(value))
    }

    fun band(value: Int): ScoreBand = when {
        value >= GOOD_FROM -> ScoreBand.Good
        value >= MODERATE_FROM -> ScoreBand.Moderate
        else -> ScoreBand.Poor
    }
}
