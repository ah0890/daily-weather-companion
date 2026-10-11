package com.ah0890.dailyweather.presentation.weather

import androidx.annotation.StringRes
import com.ah0890.dailyweather.R
import java.util.Locale
import kotlin.math.roundToInt

/** Whole-degree temperature such as "22°". Avoids showing "-0°". */
fun formatTemperature(celsius: Double): String {
    val rounded = celsius.roundToInt()
    return "${if (rounded == 0) 0 else rounded}°"
}

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

/** WHO UV index category. */
@StringRes
fun uvLevelRes(index: Double): Int = when {
    index < 3 -> R.string.uv_low
    index < 6 -> R.string.uv_moderate
    index < 8 -> R.string.uv_high
    index < 11 -> R.string.uv_very_high
    else -> R.string.uv_extreme
}
