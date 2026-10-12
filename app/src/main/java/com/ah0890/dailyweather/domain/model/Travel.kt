package com.ah0890.dailyweather.domain.model

/** How the user usually gets to work. Advice is tailored to what matters for each. */
enum class TransportMode { Motorcycle, Car, PublicTransport, Walking, Other }

/**
 * A weather condition worth knowing about for a trip or a day, most serious first. Each has a
 * clearly defined threshold in [com.ah0890.dailyweather.domain.usecase.HazardAnalysis]; they
 * describe what the forecast suggests is possible, never a certainty.
 */
enum class WeatherHazard {
    Thunderstorm,
    Gale,
    DenseFog,
    ExtremeHeat,
    Wintry,
    HeavyRain,
    RainLikely,
    StrongWind,
    PoorVisibility,
    Heat,
    RainPossible,
    HighUv,
}

/** How much a hazard matters for a particular way of travelling. */
enum class Impact { None, Minor, Major }

/** Overall verdict for one trip. Phrased cautiously in the UI ("look favourable", "consider"). */
enum class TravelRating { Favorable, Caution, Unfavorable }

/** A practical suggestion for one trip. */
enum class TravelTip {
    /** Motorcycle trip with difficult conditions: the car or public transport may be better. */
    ConsiderAlternative,
    RainGear,
    Umbrella,
    ConsiderUmbrella,
    AllowExtraTime,
    RideCarefully,
    CarryWater,
    SunProtection,
}

/**
 * Advice for one commute leg and transport mode.
 *
 * @property concerns hazards that matter for [mode], most important first.
 */
data class LegAdvice(
    val leg: CommuteLeg,
    val mode: TransportMode,
    val rating: TravelRating,
    val concerns: List<WeatherHazard>,
    val tips: List<TravelTip>,
)

enum class ScoreBand { Good, Moderate, Poor }

/**
 * A simple 0–100 summary of how pleasant the weather is for an office day. It is a rule of thumb
 * built from the forecast, not a safety rating.
 */
data class OfficeWeatherScore(val value: Int, val band: ScoreBand)

enum class ChecklistItem { Umbrella, RainGear, AlternativeTransport, Jacket, Water, Sunglasses, SunProtection }

enum class ChecklistLevel { Recommended, Useful }

/**
 * One line of the travel checklist.
 *
 * @property legs the trips this applies to, when that matters (e.g. "consider the car for the
 *   return trip"); empty when it applies to the whole day.
 */
data class ChecklistEntry(
    val item: ChecklistItem,
    val level: ChecklistLevel,
    val legs: List<CommuteLegType> = emptyList(),
)

/** Everything the app says about the next office day, built by [com.ah0890.dailyweather.domain.usecase.OfficeDayPlanner]. */
data class OfficeDay(
    val summary: CommuteSummary,
    val advice: List<LegAdvice>,
    val score: OfficeWeatherScore,
    val checklist: List<ChecklistEntry>,
)
