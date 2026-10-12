package com.ah0890.dailyweather.presentation.weather

import android.content.res.Resources
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.domain.usecase.RainSoon
import com.ah0890.dailyweather.domain.usecase.WeatherAlert
import java.time.format.DateTimeFormatter

/**
 * One line for an alert, e.g. "⛈️ Thunderstorms possible during your evening commute." Plain
 * [Resources] so the Today screen and notifications word alerts identically.
 */
fun WeatherAlert.describe(res: Resources, hours: DateTimeFormatter, unit: TemperatureUnit): String {
    var label = res.getString(hazard.labelRes)
    if ((hazard == WeatherHazard.Heat || hazard == WeatherHazard.ExtremeHeat) && peakFeelsLikeC != null) {
        label += " (" + res.getString(R.string.alert_feels_like, formatTemperature(peakFeelsLikeC, unit)) + ")"
    }
    val sentence = when {
        CommuteLegType.Morning in legs && CommuteLegType.Evening in legs -> res.getString(R.string.alert_during_both, label)
        CommuteLegType.Morning in legs -> res.getString(R.string.alert_during_morning, label)
        CommuteLegType.Evening in legs -> res.getString(R.string.alert_during_evening, label)
        end == start.plusHours(1) -> res.getString(R.string.alert_around, label, start.format(hours))
        else -> res.getString(R.string.alert_between, label, start.format(hours), end.format(hours))
    }
    return "${hazard.emoji} $sentence"
}

fun RainSoon.describe(res: Resources): String = when (this) {
    is RainSoon.Ongoing -> res.getString(if (easingSoon) R.string.rain_soon_easing else R.string.rain_soon_ongoing)
    is RainSoon.StartingWithinHour -> res.getString(R.string.rain_soon_starting, percent)
    is RainSoon.Possible -> res.getString(R.string.rain_soon_possible, percent)
    RainSoon.NoneExpected -> res.getString(R.string.rain_soon_none)
}
