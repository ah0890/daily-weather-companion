package com.ah0890.dailyweather.domain.model

/** How strongly to suggest an umbrella. Never phrased as a certainty in the UI. */
enum class UmbrellaAdvice {
    /** Rain is likely (or already falling). */
    Recommended,

    /** Some chance of rain; worth considering. */
    Consider,

    /** Rain chances are low. */
    NotNeeded,
}

/** Decision support derived from a forecast. Commute advice will be added here later. */
data class WeatherRecommendation(
    val umbrella: UmbrellaAdvice,
)

/** Everything the "Today's outlook" card and the morning notification need. */
data class TodayOutlook(
    val rain: RainInformation,
    val recommendation: WeatherRecommendation,
)
