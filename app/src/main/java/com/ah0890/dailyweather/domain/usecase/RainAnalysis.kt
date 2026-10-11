package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.RainInformation
import com.ah0890.dailyweather.domain.model.RainPeriod
import com.ah0890.dailyweather.domain.model.TodayOutlook
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherRecommendation
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Turns hourly forecasts into rain timing and umbrella advice.
 *
 * All rules use the provider's hourly precipitation probability rather than guessing timing from
 * daily totals. Thresholds are deliberately simple so the advice is predictable.
 */
object RainAnalysis {

    /** An hour at or above this chance of rain counts as "rain likely". */
    const val RAIN_LIKELY_PERCENT = 40

    /** Peak chance at or above which an umbrella is recommended. */
    const val UMBRELLA_RECOMMENDED_PERCENT = 60

    /** Peak chance at or above which an umbrella is worth considering. */
    const val UMBRELLA_CONSIDER_PERCENT = 30

    /** Expected rain (mm) that is worth considering an umbrella for even with low probabilities. */
    const val UMBRELLA_CONSIDER_MM = 1.0

    /** Advice covers at least this many hours ahead, so late-evening advice still means something. */
    const val MIN_WINDOW_HOURS = 6L

    /** Outlook for the rest of the forecast location's current day. */
    fun outlookForRestOfToday(forecast: WeatherForecast): TodayOutlook {
        val now = forecast.current.time.truncatedTo(ChronoUnit.HOURS)
        val endOfDay = now.toLocalDate().plusDays(1).atStartOfDay()
        val until = maxOf(endOfDay, now.plusHours(MIN_WINDOW_HOURS))
        return outlook(
            hours = forecast.hourly.filter { !it.time.isBefore(now) && it.time.isBefore(until) },
            rainingNow = forecast.current.condition.isPrecipitation,
        )
    }

    fun outlook(hours: List<HourlyForecast>, rainingNow: Boolean = false): TodayOutlook {
        val rain = rainInformation(hours)
        return TodayOutlook(rain, WeatherRecommendation(umbrellaAdvice(rain, rainingNow)))
    }

    fun rainInformation(hours: List<HourlyForecast>): RainInformation {
        val sorted = hours.sortedBy { it.time }
        val peak = sorted.filter { it.precipitationProbability != null }.maxByOrNull { it.precipitationProbability!! }
        val amounts = sorted.mapNotNull { it.precipitationMm }
        return RainInformation(
            maxProbability = peak?.precipitationProbability,
            peakTime = peak?.time,
            totalMm = if (amounts.isEmpty()) null else amounts.sum(),
            periods = rainPeriods(sorted),
        )
    }

    fun umbrellaAdvice(rain: RainInformation, rainingNow: Boolean = false): UmbrellaAdvice {
        val peak = rain.maxProbability ?: 0
        val total = rain.totalMm ?: 0.0
        return when {
            rainingNow || peak >= UMBRELLA_RECOMMENDED_PERCENT -> UmbrellaAdvice.Recommended
            peak >= UMBRELLA_CONSIDER_PERCENT || total >= UMBRELLA_CONSIDER_MM -> UmbrellaAdvice.Consider
            else -> UmbrellaAdvice.NotNeeded
        }
    }

    /**
     * Groups rainy hours into periods. A single dry hour between rainy ones is bridged, so a
     * forecast of 60%, 35%, 70% reads as one period rather than two fragments.
     */
    private fun rainPeriods(hours: List<HourlyForecast>): List<RainPeriod> {
        val rainy = hours.filter { (it.precipitationProbability ?: 0) >= RAIN_LIKELY_PERCENT }
        if (rainy.isEmpty()) return emptyList()

        val periods = mutableListOf<RainPeriod>()
        var start = rainy.first().time
        var lastHour = start
        var peak = rainy.first().precipitationProbability!!

        for (hour in rainy.drop(1)) {
            val gapHours = ChronoUnit.HOURS.between(lastHour, hour.time)
            if (gapHours <= 2) {
                lastHour = hour.time
                peak = maxOf(peak, hour.precipitationProbability!!)
            } else {
                periods += RainPeriod(start, lastHour.plusHours(1), peak)
                start = hour.time
                lastHour = hour.time
                peak = hour.precipitationProbability!!
            }
        }
        periods += RainPeriod(start, lastHour.plusHours(1), peak)
        return periods
    }

    /** Picks the single most useful thing to say about rain, as of [now]. */
    fun summary(rain: RainInformation, now: LocalDateTime): RainSummary {
        val thisHour = now.truncatedTo(ChronoUnit.HOURS)
        val first = rain.periods.firstOrNull()
        val peak = rain.maxProbability ?: 0
        return when {
            first != null && !first.start.isAfter(thisHour) -> RainSummary.NowUntil(first.end)
            first != null -> RainSummary.Between(first.start, first.end)
            peak >= UMBRELLA_CONSIDER_PERCENT && rain.peakTime != null -> RainSummary.ChanceAround(peak, rain.peakTime)
            (rain.totalMm ?: 0.0) >= UMBRELLA_CONSIDER_MM -> RainSummary.LightRain(rain.totalMm!!)
            else -> RainSummary.Low
        }
    }

    private fun maxOf(a: LocalDateTime, b: LocalDateTime) = if (a.isAfter(b)) a else b
}

/** What to tell the user about rain timing, independent of wording and language. */
sealed interface RainSummary {
    /** Rain likely from the current hour until [until]. */
    data class NowUntil(val until: LocalDateTime) : RainSummary

    /** Rain most likely in this period (the first one, if several). */
    data class Between(val start: LocalDateTime, val end: LocalDateTime) : RainSummary

    /** No hour reaches "likely", but there is a noticeable chance around [time]. */
    data class ChanceAround(val percent: Int, val time: LocalDateTime) : RainSummary

    /** Low probabilities but a measurable amount forecast. */
    data class LightRain(val totalMm: Double) : RainSummary

    data object Low : RainSummary
}
