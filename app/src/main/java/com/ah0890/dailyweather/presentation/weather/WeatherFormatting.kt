package com.ah0890.dailyweather.presentation.weather

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.UvCategory
import com.ah0890.dailyweather.presentation.components.LocalUserPreferences
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Whole-degree temperature such as "22°", converted to [unit]. Avoids showing "-0°". Forecasts are
 * always stored in Celsius, so changing the unit never needs a new download.
 */
fun formatTemperature(celsius: Double, unit: TemperatureUnit = TemperatureUnit.Celsius): String {
    val value = when (unit) {
        TemperatureUnit.Celsius -> celsius
        TemperatureUnit.Fahrenheit -> celsius * 9 / 5 + 32
    }
    val rounded = value.roundToInt()
    return "${if (rounded == 0) 0 else rounded}°"
}

/** [formatTemperature] in the user's chosen unit. */
@Composable
fun temperatureText(celsius: Double): String = formatTemperature(celsius, LocalUserPreferences.current.temperatureUnit)

fun formatWindSpeed(kmh: Double): String = "${kmh.roundToInt()} km/h"

/** Eight-point compass direction the wind blows *from*, e.g. 230° → "SW". */
fun compassDirection(degrees: Int): String {
    val points = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = ((degrees % 360) + 360) % 360
    return points[((normalized + 22.5) / 45).toInt() % 8]
}

/** Rain amount: one decimal below 10 mm ("0.4 mm"), whole millimetres above ("12 mm"). */
fun formatPrecipitation(mm: Double): String =
    if (mm < 10) String.format(Locale.getDefault(), "%.1f mm", mm) else "${mm.roundToInt()} mm"

/** Visibility in kilometres; anything from 10 km up is shown as "10+ km". */
fun formatVisibility(meters: Double): String {
    val km = meters / 1000
    return when {
        km >= 10 -> "10+ km"
        km >= 1 -> "${km.roundToInt()} km"
        else -> String.format(Locale.getDefault(), "%.1f km", km)
    }
}

/** WHO UV index category of the index as displayed (rounded), so "3" never reads as "Low". */
@StringRes
fun uvLevelRes(index: Double): Int = UvCategory.of(index).labelRes

@get:StringRes
val UvCategory.labelRes: Int
    get() = when (this) {
        UvCategory.Low -> R.string.uv_low
        UvCategory.Moderate -> R.string.uv_moderate
        UvCategory.High -> R.string.uv_high
        UvCategory.VeryHigh -> R.string.uv_very_high
        UvCategory.Extreme -> R.string.uv_extreme
    }

/** Colour dot for a UV category; the category name is always shown next to it. */
val UvCategory.dot: String
    get() = when (this) {
        UvCategory.Low -> "🟢"
        UvCategory.Moderate -> "🟡"
        UvCategory.High -> "🟠"
        UvCategory.VeryHigh -> "🔴"
        UvCategory.Extreme -> "🟣"
    }
