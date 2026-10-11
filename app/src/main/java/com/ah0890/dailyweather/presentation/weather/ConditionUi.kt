package com.ah0890.dailyweather.presentation.weather

import androidx.annotation.StringRes
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.ui.theme.WeatherMood

/** Background mood for a condition. Clear skies are warm by day and cool sky-blue at night. */
fun WeatherCondition.mood(isDay: Boolean): WeatherMood = when (this) {
    WeatherCondition.Clear, WeatherCondition.MainlyClear -> if (isDay) WeatherMood.Sunny else WeatherMood.Clear
    WeatherCondition.PartlyCloudy -> WeatherMood.Clear
    WeatherCondition.Overcast, WeatherCondition.Unknown -> WeatherMood.Cloudy
    WeatherCondition.Fog -> WeatherMood.Fog
    WeatherCondition.Drizzle,
    WeatherCondition.FreezingDrizzle,
    WeatherCondition.Rain,
    WeatherCondition.FreezingRain,
    WeatherCondition.RainShowers,
    -> WeatherMood.Rain
    WeatherCondition.Snow, WeatherCondition.SnowShowers -> WeatherMood.Snow
    WeatherCondition.Thunderstorm -> WeatherMood.Storm
}

/** Emoji icon. Always paired with [labelRes] text, so meaning never relies on the icon alone. */
fun WeatherCondition.emoji(isDay: Boolean): String = when (this) {
    WeatherCondition.Clear -> if (isDay) "☀️" else "🌙"
    WeatherCondition.MainlyClear -> if (isDay) "🌤️" else "🌙"
    // The sun-and-cloud emoji would show a sun at night, so night uses sun-free variants.
    WeatherCondition.PartlyCloudy -> if (isDay) "⛅" else "☁️"
    WeatherCondition.Overcast -> "☁️"
    WeatherCondition.Fog -> "🌫️"
    WeatherCondition.Drizzle, WeatherCondition.RainShowers -> if (isDay) "🌦️" else "🌧️"
    WeatherCondition.FreezingDrizzle, WeatherCondition.Rain, WeatherCondition.FreezingRain -> "🌧️"
    WeatherCondition.Snow, WeatherCondition.SnowShowers -> "🌨️"
    WeatherCondition.Thunderstorm -> "⛈️"
    WeatherCondition.Unknown -> "🌡️"
}

@get:StringRes
val WeatherCondition.labelRes: Int
    get() = when (this) {
        WeatherCondition.Clear -> R.string.condition_clear
        WeatherCondition.MainlyClear -> R.string.condition_mainly_clear
        WeatherCondition.PartlyCloudy -> R.string.condition_partly_cloudy
        WeatherCondition.Overcast -> R.string.condition_overcast
        WeatherCondition.Fog -> R.string.condition_fog
        WeatherCondition.Drizzle -> R.string.condition_drizzle
        WeatherCondition.FreezingDrizzle -> R.string.condition_freezing_drizzle
        WeatherCondition.Rain -> R.string.condition_rain
        WeatherCondition.FreezingRain -> R.string.condition_freezing_rain
        WeatherCondition.RainShowers -> R.string.condition_rain_showers
        WeatherCondition.Snow -> R.string.condition_snow
        WeatherCondition.SnowShowers -> R.string.condition_snow_showers
        WeatherCondition.Thunderstorm -> R.string.condition_thunderstorm
        WeatherCondition.Unknown -> R.string.condition_unknown
    }
