package com.ah0890.dailyweather.domain.model

import java.time.LocalDateTime

/** WHO UV index categories. */
enum class UvCategory {
    Low,
    Moderate,
    High,
    VeryHigh,
    Extreme;

    companion object {
        /** 0–2 low, 3–5 moderate, 6–7 high, 8–10 very high, 11+ extreme (values rounded as reported). */
        fun of(index: Double): UvCategory = when {
            index < 2.5 -> Low
            index < 5.5 -> Moderate
            index < 7.5 -> High
            index < 10.5 -> VeryHigh
            else -> Extreme
        }
    }
}

/**
 * Today's UV for the rest of the day.
 *
 * @property peakTime the hour of [peakIndex], or null when only the daily maximum is known.
 * @property highFrom start of the hours rated high (UV 6+), if any; [highUntil] is their end.
 */
data class UvOutlook(
    val peakIndex: Double,
    val peakTime: LocalDateTime?,
    val highFrom: LocalDateTime? = null,
    val highUntil: LocalDateTime? = null,
) {
    val category: UvCategory get() = UvCategory.of(peakIndex)
}
