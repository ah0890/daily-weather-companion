package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.UvCategory
import com.ah0890.dailyweather.domain.model.UvOutlook
import com.ah0890.dailyweather.domain.model.WeatherForecast
import java.time.temporal.ChronoUnit

/** Today's UV status and whether it is worth an alert. */
object UvAnalysis {

    /** WHO "high" starts at 6; sun protection is commonly advised from 3. */
    const val HIGH_INDEX = 5.5

    /** The UV alert thresholds a user can choose: high (6+) or very high (8+). */
    val ALERT_THRESHOLDS = listOf(UvCategory.High, UvCategory.VeryHigh)

    /**
     * Highest UV for the rest of today from the hourly forecast, falling back to the daily maximum
     * (without a time) when hourly UV is missing. Null after dark or without any UV data.
     */
    fun today(forecast: WeatherForecast): UvOutlook? {
        val now = forecast.current.time.truncatedTo(ChronoUnit.HOURS)
        val hours = forecast.hourly.filter {
            it.isDay && !it.time.isBefore(now) && it.time.toLocalDate() == now.toLocalDate() && it.uvIndex != null
        }
        val peak = hours.maxByOrNull { it.uvIndex!! }
        if (peak != null) {
            val high = hours.filter { it.uvIndex!! >= HIGH_INDEX }
            return UvOutlook(
                peakIndex = peak.uvIndex!!,
                peakTime = peak.time,
                highFrom = high.minOfOrNull { it.time },
                highUntil = high.maxOfOrNull { it.time }?.plusHours(1),
            )
        }
        if (forecast.hourly.any { it.uvIndex != null }) return null // after dark: nothing left today
        return forecast.today?.uvIndexMax?.let { UvOutlook(it, peakTime = null) }
    }

    /** Whether today's UV reaches the user's chosen alert level. */
    fun reaches(outlook: UvOutlook?, threshold: UvCategory): Boolean =
        outlook != null && outlook.category >= threshold
}
