package com.ah0890.dailyweather.presentation.forecast

import com.ah0890.dailyweather.domain.model.DailyForecast
import java.time.DayOfWeek
import java.time.LocalDate

/** How a forecast day is named in the list. */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Tomorrow : DayLabel
    data class Weekday(val day: DayOfWeek) : DayLabel

    companion object {
        /** [today] is the current date at the forecast location, not on the device. */
        fun of(date: LocalDate, today: LocalDate): DayLabel = when (date) {
            today -> Today
            today.plusDays(1) -> Tomorrow
            else -> Weekday(date.dayOfWeek)
        }
    }
}

/** Lowest and highest temperature across all [days], used to scale the range bars. */
data class TemperatureSpan(val min: Double, val max: Double) {

    /** Position of [value] within the span, 0.0–1.0. A zero-width span maps everything to 0.5. */
    fun fractionOf(value: Double): Float {
        if (max <= min) return 0.5f
        return ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        fun of(days: List<DailyForecast>): TemperatureSpan? {
            if (days.isEmpty()) return null
            return TemperatureSpan(days.minOf { it.minTemperatureC }, days.maxOf { it.maxTemperatureC })
        }
    }
}
