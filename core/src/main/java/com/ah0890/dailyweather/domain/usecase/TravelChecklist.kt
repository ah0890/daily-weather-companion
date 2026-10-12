package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.ChecklistEntry
import com.ah0890.dailyweather.domain.model.ChecklistItem
import com.ah0890.dailyweather.domain.model.ChecklistLevel
import com.ah0890.dailyweather.domain.model.CommuteRain
import com.ah0890.dailyweather.domain.model.CommuteSummary
import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.TravelTip
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.WeatherCondition

/** "Today's travel checklist": only the things worth taking or thinking about today. */
object TravelChecklist {

    /** Feels-like at departure at or below which a jacket is recommended… */
    const val JACKET_RECOMMENDED_C = 10.0

    /** …and up to which a light one is still useful. */
    const val JACKET_USEFUL_C = 15.0

    /** Riding feels colder because of the airflow. */
    const val MOTORCYCLE_CHILL_C = 3.0

    /** Feels-like temperature during the day from which carrying water is suggested. */
    const val WATER_FEELS_LIKE_C = 32.0

    /** UV index from which sunglasses are useful on a bright trip (WHO "moderate"). */
    const val SUNGLASSES_UV = 3.0

    private val BRIGHT = setOf(WeatherCondition.Clear, WeatherCondition.MainlyClear, WeatherCondition.PartlyCloudy)

    fun entries(summary: CommuteSummary, workdayHours: List<HourlyForecast>, mode: TransportMode): List<ChecklistEntry> {
        val advice = summary.legs.map { TravelAdvisor.advise(it, mode) }
        val tips = advice.flatMap { it.tips }.toSet()

        return buildList {
            if (mode == TransportMode.Motorcycle) {
                val avoid = advice.filter { it.rating == TravelRating.Unfavorable }.map { it.leg.type }
                if (avoid.isNotEmpty()) add(ChecklistEntry(ChecklistItem.AlternativeTransport, ChecklistLevel.Recommended, avoid))
                if (TravelTip.RainGear in tips) {
                    val wet = summary.legs.any { it.rain == CommuteRain.Likely }
                    add(ChecklistEntry(ChecklistItem.RainGear, if (wet) ChecklistLevel.Recommended else ChecklistLevel.Useful))
                }
            } else {
                // Rain at lunchtime matters too, so look at the whole working day as well as the trips.
                val dayUmbrella = RainAnalysis.umbrellaAdvice(RainAnalysis.rainInformation(workdayHours))
                val level = when {
                    TravelTip.Umbrella in tips || dayUmbrella == UmbrellaAdvice.Recommended -> ChecklistLevel.Recommended
                    TravelTip.ConsiderUmbrella in tips || dayUmbrella == UmbrellaAdvice.Consider -> ChecklistLevel.Useful
                    else -> null
                }
                if (level != null) add(ChecklistEntry(ChecklistItem.Umbrella, level))
            }

            val chill = if (mode == TransportMode.Motorcycle) MOTORCYCLE_CHILL_C else 0.0
            val coldest = summary.legs.minOf { it.feelsLikeC } - chill
            when {
                coldest <= JACKET_RECOMMENDED_C -> add(ChecklistEntry(ChecklistItem.Jacket, ChecklistLevel.Recommended))
                coldest <= JACKET_USEFUL_C -> add(ChecklistEntry(ChecklistItem.Jacket, ChecklistLevel.Useful))
            }

            val hottest = (workdayHours.map { it.feelsLikeC ?: it.temperatureC } + summary.legs.map { it.feelsLikeC }).max()
            if (hottest >= WATER_FEELS_LIKE_C) add(ChecklistEntry(ChecklistItem.Water, ChecklistLevel.Recommended))

            // Without UV data a clear daytime sky is reason enough.
            val brightTrip = summary.legs.any {
                it.isDay && it.condition in BRIGHT && (it.uvIndex ?: SUNGLASSES_UV) >= SUNGLASSES_UV
            }
            if (brightTrip) add(ChecklistEntry(ChecklistItem.Sunglasses, ChecklistLevel.Useful))

            val maxUv = workdayHours.filter { it.isDay }.mapNotNull { it.uvIndex }.maxOrNull() ?: 0.0
            if (mode != TransportMode.Car && maxUv >= HazardAnalysis.HIGH_UV) {
                add(ChecklistEntry(ChecklistItem.SunProtection, ChecklistLevel.Useful))
            }
        }
    }
}
