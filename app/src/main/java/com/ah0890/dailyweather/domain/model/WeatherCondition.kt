package com.ah0890.dailyweather.domain.model

/** Provider-independent weather condition. Each data source maps its own codes onto these. */
enum class WeatherCondition {
    Clear,
    MainlyClear,
    PartlyCloudy,
    Overcast,
    Fog,
    Drizzle,
    FreezingDrizzle,
    Rain,
    FreezingRain,
    RainShowers,
    Snow,
    SnowShowers,
    Thunderstorm,
    Unknown;

    /** True for any condition where liquid or frozen precipitation is falling. */
    val isPrecipitation: Boolean
        get() = when (this) {
            Drizzle, FreezingDrizzle, Rain, FreezingRain, RainShowers, Snow, SnowShowers, Thunderstorm -> true
            Clear, MainlyClear, PartlyCloudy, Overcast, Fog, Unknown -> false
        }
}
