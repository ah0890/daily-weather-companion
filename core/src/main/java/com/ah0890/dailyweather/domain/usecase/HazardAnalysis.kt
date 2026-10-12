package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherHazard

/**
 * Finds hazards in a window of hourly forecasts using fixed, documented thresholds. Only data the
 * provider actually supplied is used; a missing value never triggers a hazard.
 */
object HazardAnalysis {

    /** Met Office definition of heavy rain: more than 4 mm in an hour. */
    const val HEAVY_RAIN_MM_PER_HOUR = 4.0

    /** Sustained wind from Beaufort 6 ("strong breeze"). */
    const val STRONG_WIND_KMH = CommuteAnalysis.WINDY_KMH

    /** Gusts at which riding and walking become noticeably harder. */
    const val STRONG_GUST_KMH = 60.0

    /** Sustained wind from Beaufort 8 ("gale"). */
    const val GALE_KMH = 62.0

    /** Gusts that can bring down branches and make two-wheelers unstable. */
    const val GALE_GUST_KMH = 85.0

    /** Below 1 km visibility counts as fog (WMO). */
    const val POOR_VISIBILITY_M = 1_000.0

    /** Below 200 m is dense fog, when many weather services issue warnings. */
    const val DENSE_FOG_M = 200.0

    /** Feels-like temperature from which heat becomes uncomfortable outdoors. */
    const val HEAT_FEELS_LIKE_C = 35.0

    /** Feels-like temperature in the US National Weather Service "danger" range (105 °F). */
    const val EXTREME_HEAT_FEELS_LIKE_C = 41.0

    /** WHO UV index category "high" starts at 6. */
    const val HIGH_UV = 6.0

    private val WINTRY = setOf(
        WeatherCondition.FreezingDrizzle,
        WeatherCondition.FreezingRain,
        WeatherCondition.Snow,
        WeatherCondition.SnowShowers,
    )

    /** All hazards in [hours]. Where a hazard has two levels only the stronger is included. */
    fun detect(hours: List<HourlyForecast>): Set<WeatherHazard> {
        if (hours.isEmpty()) return emptySet()
        val hazards = mutableSetOf<WeatherHazard>()

        if (hours.any { it.condition == WeatherCondition.Thunderstorm }) hazards += WeatherHazard.Thunderstorm
        if (hours.any { (it.precipitationMm ?: 0.0) >= HEAVY_RAIN_MM_PER_HOUR }) hazards += WeatherHazard.HeavyRain
        if (hours.any { it.condition in WINTRY }) hazards += WeatherHazard.Wintry

        val chance = hours.mapNotNull { it.precipitationProbability }.maxOrNull() ?: 0
        when {
            chance >= RainAnalysis.UMBRELLA_RECOMMENDED_PERCENT -> hazards += WeatherHazard.RainLikely
            chance >= RainAnalysis.UMBRELLA_CONSIDER_PERCENT -> hazards += WeatherHazard.RainPossible
        }

        val wind = hours.mapNotNull { it.windSpeedKmh }.maxOrNull() ?: 0.0
        val gust = hours.mapNotNull { it.windGustsKmh }.maxOrNull() ?: 0.0
        when {
            wind >= GALE_KMH || gust >= GALE_GUST_KMH -> hazards += WeatherHazard.Gale
            wind >= STRONG_WIND_KMH || gust >= STRONG_GUST_KMH -> hazards += WeatherHazard.StrongWind
        }

        val visibility = hours.mapNotNull { it.visibilityMeters }.minOrNull()
        when {
            visibility != null && visibility < DENSE_FOG_M -> hazards += WeatherHazard.DenseFog
            (visibility != null && visibility < POOR_VISIBILITY_M) || hours.any { it.condition == WeatherCondition.Fog } ->
                hazards += WeatherHazard.PoorVisibility
        }

        val feelsLike = hours.maxOf { it.feelsLikeC ?: it.temperatureC }
        when {
            feelsLike >= EXTREME_HEAT_FEELS_LIKE_C -> hazards += WeatherHazard.ExtremeHeat
            feelsLike >= HEAT_FEELS_LIKE_C -> hazards += WeatherHazard.Heat
        }

        val uv = hours.filter { it.isDay }.mapNotNull { it.uvIndex }.maxOrNull() ?: 0.0
        if (uv >= HIGH_UV) hazards += WeatherHazard.HighUv

        return hazards
    }
}
