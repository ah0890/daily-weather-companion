package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteLeg
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherHazard
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class AlertLevel {
    /** Worth knowing: poor visibility, a very hot afternoon. */
    Advisory,

    /** Thunderstorms, heavy rain, gales, dense fog, extreme heat, snow or ice. */
    Severe,
}

/**
 * A stretch of hours in which a hazard is forecast.
 *
 * @property end end of the last hour (exclusive).
 * @property legs commute trips the period overlaps.
 * @property peakFeelsLikeC highest feels-like temperature in the period (for heat alerts).
 */
data class WeatherAlert(
    val hazard: WeatherHazard,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val legs: List<CommuteLegType> = emptyList(),
    val peakFeelsLikeC: Double? = null,
) {
    val level: AlertLevel get() = if (hazard in SevereWeatherAnalysis.SEVERE) AlertLevel.Severe else AlertLevel.Advisory
}

/**
 * Finds weather alerts in the next [WINDOW_HOURS] hours using [HazardAnalysis]'s documented
 * thresholds, hour by hour. Nothing is invented: an alert exists only when an hour of the
 * provider's forecast meets a threshold, and the wording stays "possible"/"expected".
 */
object SevereWeatherAnalysis {

    const val WINDOW_HOURS = 24L

    val SEVERE: Set<WeatherHazard> = setOf(
        WeatherHazard.Thunderstorm,
        WeatherHazard.Gale,
        WeatherHazard.DenseFog,
        WeatherHazard.ExtremeHeat,
        WeatherHazard.Wintry,
        WeatherHazard.HeavyRain,
    )

    private val ADVISORY = setOf(WeatherHazard.PoorVisibility, WeatherHazard.Heat)

    /** Two levels of one hazard form one alert, named after the stronger level. */
    private fun family(hazard: WeatherHazard): WeatherHazard = when (hazard) {
        WeatherHazard.ExtremeHeat -> WeatherHazard.Heat
        WeatherHazard.DenseFog -> WeatherHazard.PoorVisibility
        else -> hazard
    }

    /**
     * @param legs today's (or the next) commute trips, so alerts can say "during your evening
     *   commute"; each trip covers its departure hour and the hour after.
     * @return alerts ordered by start time, most serious first when they start together.
     */
    fun alerts(forecast: WeatherForecast, legs: List<CommuteLeg> = emptyList()): List<WeatherAlert> {
        val from = forecast.current.time.truncatedTo(ChronoUnit.HOURS)
        val hours = forecast.hourly.filter { !it.time.isBefore(from) && it.time.isBefore(from.plusHours(WINDOW_HOURS)) }

        val alerts = mutableListOf<WeatherAlert>()
        val open = mutableMapOf<WeatherHazard, WeatherAlert>()
        for (hour in hours) {
            val found = HazardAnalysis.detect(listOf(hour)).filter { it in SEVERE || it in ADVISORY }
            val families = found.associateBy { family(it) }
            // Close periods whose hazard stopped (a single quiet hour ends a period).
            open.keys.filter { it !in families }.forEach { alerts += open.remove(it)!! }
            for ((key, hazard) in families) {
                val feelsLike = hour.feelsLikeC ?: hour.temperatureC
                val current = open[key]
                open[key] = if (current == null) {
                    WeatherAlert(hazard, hour.time, hour.time.plusHours(1), peakFeelsLikeC = feelsLike)
                } else {
                    current.copy(
                        hazard = minOf(current.hazard, hazard),
                        end = hour.time.plusHours(1),
                        peakFeelsLikeC = maxOf(current.peakFeelsLikeC ?: feelsLike, feelsLike),
                    )
                }
            }
        }
        alerts += open.values

        return alerts
            .map { alert -> alert.copy(legs = legs.filter { it.overlaps(alert) }.map { it.type }) }
            .sortedWith(compareBy<WeatherAlert> { it.start }.thenBy { it.hazard.ordinal })
    }

    private fun CommuteLeg.overlaps(alert: WeatherAlert): Boolean {
        val tripStart = departure.truncatedTo(ChronoUnit.HOURS)
        val tripEnd = tripStart.plusHours(2)
        return alert.start.isBefore(tripEnd) && tripStart.isBefore(alert.end)
    }
}

/** What to say about rain in the next hour or two. Hourly data only: no minute-level claims. */
sealed interface RainSoon {
    /** Raining now (by the current conditions or a likely current hour). */
    data class Ongoing(val easingSoon: Boolean) : RainSoon

    /** Dry now, but the next hour looks likely to be wet. */
    data class StartingWithinHour(val percent: Int) : RainSoon

    /** A noticeable chance in the next two hours. */
    data class Possible(val percent: Int) : RainSoon

    data object NoneExpected : RainSoon

    companion object {
        /** Hourly chance at which rain "may begin". */
        const val STARTING_PERCENT = 50

        fun of(forecast: WeatherForecast): RainSoon? {
            val thisHour = forecast.current.time.truncatedTo(ChronoUnit.HOURS)
            val now = forecast.hourly.firstOrNull { it.time == thisHour }
            val next = forecast.hourly.firstOrNull { it.time == thisHour.plusHours(1) } ?: return null
            val after = forecast.hourly.firstOrNull { it.time == thisHour.plusHours(2) }
            val nextChance = next.precipitationProbability ?: return null
            val soonChance = maxOf(nextChance, after?.precipitationProbability ?: 0)

            val rainingNow = forecast.current.condition.isPrecipitation ||
                (now?.precipitationProbability ?: 0) >= RainAnalysis.UMBRELLA_RECOMMENDED_PERCENT
            return when {
                rainingNow -> Ongoing(easingSoon = nextChance < RainAnalysis.UMBRELLA_CONSIDER_PERCENT)
                nextChance >= STARTING_PERCENT -> StartingWithinHour(nextChance)
                soonChance >= RainAnalysis.UMBRELLA_CONSIDER_PERCENT -> Possible(soonChance)
                else -> NoneExpected
            }
        }
    }
}
