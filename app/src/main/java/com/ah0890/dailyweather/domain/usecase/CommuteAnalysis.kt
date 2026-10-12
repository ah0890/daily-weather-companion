package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteAdvice
import com.ah0890.dailyweather.domain.model.CommuteLeg
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.CommuteRain
import com.ah0890.dailyweather.domain.model.CommuteSummary
import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.model.WeatherForecast
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Summarises the weather for the user's morning and evening trips. */
object CommuteAnalysis {

    /** Sustained wind at or above this is worth a warning for riders (Beaufort 6, "strong breeze"). */
    const val WINDY_KMH = 39.0

    /** How long a trip is assumed to last when picking hours to look at. */
    private const val TRIP_HOURS = 2L

    /**
     * Commute for the next workday: today until its evening trip is over, otherwise the next day
     * in [workdays]. Legs already in the past are left out. Returns null if no workday is set or
     * the forecast doesn't cover the chosen day.
     */
    fun summary(
        forecast: WeatherForecast,
        times: CommuteTimes,
        workdays: Set<DayOfWeek> = UserPreferences.EVERY_DAY,
    ): CommuteSummary? {
        val now = forecast.current.time
        val today = now.toLocalDate()
        val eveningOver = !now.isBefore(today.atTime(times.evening).plusHours(TRIP_HOURS - 1))
        val first = if (eveningOver) today.plusDays(1) else today
        val date = generateSequence(first) { it.plusDays(1) }.take(7).firstOrNull { it.dayOfWeek in workdays } ?: return null

        val legs = legsOn(forecast, date, times)
            .filter { date != today || it.departure.plusHours(TRIP_HOURS - 1).isAfter(now) }
        if (legs.isEmpty()) return null

        return CommuteSummary(date = date, isToday = date == today, legs = legs, advice = advice(legs))
    }

    /** Both trips on [date], including ones already in the past (for planning views). */
    fun legsOn(forecast: WeatherForecast, date: LocalDate, times: CommuteTimes): List<CommuteLeg> = listOfNotNull(
        leg(forecast, CommuteLegType.Morning, date, times.morning),
        leg(forecast, CommuteLegType.Evening, date, times.evening),
    )

    /**
     * Hours from the morning trip (or from now, once it is over) to the end of the evening trip on
     * [summary]'s day: the "office day" as far as the weather is concerned.
     */
    fun workdayHours(forecast: WeatherForecast, summary: CommuteSummary): List<HourlyForecast> {
        val first = summary.legs.firstOrNull { it.type == CommuteLegType.Morning }?.departure ?: forecast.current.time
        val end = summary.legs.maxOf { it.departure }.truncatedTo(ChronoUnit.HOURS).plusHours(TRIP_HOURS)
        val from = maxOf(first.truncatedTo(ChronoUnit.HOURS), forecast.current.time.truncatedTo(ChronoUnit.HOURS))
        return forecast.hourly.filter { !it.time.isBefore(from) && it.time.isBefore(end) }
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

        val peak = window.filter { it.precipitationProbability != null }.maxByOrNull { it.precipitationProbability!! }
        val chance = peak?.precipitationProbability
        val wind = window.mapNotNull { it.windSpeedKmh }.maxOrNull()
        val gusts = window.mapNotNull { it.windGustsKmh }.maxOrNull()
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
            windGustsKmh = gusts,
            rainPeakTime = peak?.time,
            visibilityMeters = window.mapNotNull { it.visibilityMeters }.minOrNull(),
            feelsLikeC = atDeparture.feelsLikeC ?: atDeparture.temperatureC,
            uvIndex = window.mapNotNull { it.uvIndex }.maxOrNull(),
            hazards = HazardAnalysis.detect(window),
        )
    }

    private fun maxOf(a: LocalDateTime, b: LocalDateTime) = if (a.isAfter(b)) a else b
}
