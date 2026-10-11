package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteAdvice
import com.ah0890.dailyweather.domain.model.CommuteLeg
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.CommuteRain
import com.ah0890.dailyweather.domain.model.CommuteSummary
import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.WeatherForecast
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Summarises the weather for the user's morning and evening trips. */
object CommuteAnalysis {

    /** Sustained wind at or above this is worth a warning for riders (Beaufort 6, "strong breeze"). */
    const val WINDY_KMH = 39.0

    /** How long a trip is assumed to last when picking hours to look at. */
    private const val TRIP_HOURS = 2L

    /**
     * Commute for today, or for tomorrow once today's evening trip is over. Legs already in the
     * past are left out. Returns null if the forecast doesn't cover the chosen day.
     */
    fun summary(forecast: WeatherForecast, times: CommuteTimes): CommuteSummary? {
        val now = forecast.current.time
        val today = now.toLocalDate()
        val eveningOver = !now.isBefore(today.atTime(times.evening).plusHours(TRIP_HOURS - 1))
        val date = if (eveningOver) today.plusDays(1) else today

        val legs = listOfNotNull(
            leg(forecast, CommuteLegType.Morning, date, times.morning),
            leg(forecast, CommuteLegType.Evening, date, times.evening),
        ).filter { date != today || it.departure.plusHours(TRIP_HOURS - 1).isAfter(now) }
        if (legs.isEmpty()) return null

        return CommuteSummary(date = date, isToday = date == today, legs = legs, advice = advice(legs))
    }

    fun advice(legs: List<CommuteLeg>): CommuteAdvice = when {
        legs.any { it.rain == CommuteRain.Likely } -> CommuteAdvice.RainLikely
        legs.any { it.rain == CommuteRain.Possible } -> CommuteAdvice.RainPossible
        legs.any { it.windy } -> CommuteAdvice.Windy
        else -> CommuteAdvice.Dry
    }

    fun rainLevel(chance: Int?): CommuteRain = when {
        chance == null -> CommuteRain.Dry
        chance >= RainAnalysis.UMBRELLA_RECOMMENDED_PERCENT -> CommuteRain.Likely
        chance >= RainAnalysis.UMBRELLA_CONSIDER_PERCENT -> CommuteRain.Possible
        else -> CommuteRain.Dry
    }

    private fun leg(forecast: WeatherForecast, type: CommuteLegType, date: LocalDate, time: LocalTime): CommuteLeg? {
        val departure = date.atTime(time)
        val firstHour = departure.truncatedTo(ChronoUnit.HOURS)
        val window = forecast.hourly.filter {
            !it.time.isBefore(firstHour) && it.time.isBefore(firstHour.plusHours(TRIP_HOURS))
        }
        val atDeparture = window.firstOrNull() ?: return null

        val chance = window.mapNotNull { it.precipitationProbability }.maxOrNull()
        val wind = window.mapNotNull { it.windSpeedKmh }.maxOrNull()
        // Show the wettest condition of the trip so the icon matches the rain chance.
        val shown = window.firstOrNull { it.condition.isPrecipitation } ?: atDeparture

        return CommuteLeg(
            type = type,
            departure = departure,
            condition = shown.condition,
            isDay = shown.isDay,
            temperatureC = atDeparture.temperatureC,
            rainChance = chance,
            windSpeedKmh = wind,
            rain = rainLevel(chance),
            windy = (wind ?: 0.0) >= WINDY_KMH,
        )
    }
}
