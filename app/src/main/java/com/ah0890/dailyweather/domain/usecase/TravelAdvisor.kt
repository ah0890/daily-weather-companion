package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteLeg
import com.ah0890.dailyweather.domain.model.Impact
import com.ah0890.dailyweather.domain.model.Impact.Major
import com.ah0890.dailyweather.domain.model.Impact.Minor
import com.ah0890.dailyweather.domain.model.Impact.None
import com.ah0890.dailyweather.domain.model.LegAdvice
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.TravelTip
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.domain.model.WeatherHazard.DenseFog
import com.ah0890.dailyweather.domain.model.WeatherHazard.ExtremeHeat
import com.ah0890.dailyweather.domain.model.WeatherHazard.Gale
import com.ah0890.dailyweather.domain.model.WeatherHazard.HeavyRain
import com.ah0890.dailyweather.domain.model.WeatherHazard.Heat
import com.ah0890.dailyweather.domain.model.WeatherHazard.HighUv
import com.ah0890.dailyweather.domain.model.WeatherHazard.PoorVisibility
import com.ah0890.dailyweather.domain.model.WeatherHazard.RainLikely
import com.ah0890.dailyweather.domain.model.WeatherHazard.RainPossible
import com.ah0890.dailyweather.domain.model.WeatherHazard.StrongWind
import com.ah0890.dailyweather.domain.model.WeatherHazard.Thunderstorm
import com.ah0890.dailyweather.domain.model.WeatherHazard.Wintry

/**
 * Turns a commute leg's hazards into advice for the user's way of travelling. A motorcyclist
 * cares about rain and wind, a driver mostly about visibility and severe weather, someone
 * walking about rain, heat and sun. The verdict is a rule of thumb, never a safety guarantee.
 */
object TravelAdvisor {

    fun advise(leg: CommuteLeg, mode: TransportMode): LegAdvice {
        val concerns = leg.hazards
            .filter { impact(it, mode) != None }
            .sortedWith(compareByDescending<WeatherHazard> { impact(it, mode) }.thenBy { it.ordinal })
        val worst = concerns.maxOfOrNull { impact(it, mode) } ?: None
        val rating = when (worst) {
            Major -> TravelRating.Unfavorable
            Minor -> TravelRating.Caution
            None -> TravelRating.Favorable
        }
        return LegAdvice(leg, mode, rating, concerns, tips(leg.hazards, mode, rating))
    }

    /** How much [hazard] matters when travelling by [mode]. */
    fun impact(hazard: WeatherHazard, mode: TransportMode): Impact {
        val (motorcycle, car, publicTransport, walking, other) = IMPACTS.getValue(hazard)
        return when (mode) {
            TransportMode.Motorcycle -> motorcycle
            TransportMode.Car -> car
            TransportMode.PublicTransport -> publicTransport
            TransportMode.Walking -> walking
            TransportMode.Other -> other
        }
    }

    private fun tips(hazards: Set<WeatherHazard>, mode: TransportMode, rating: TravelRating): List<TravelTip> = buildList {
        val wet = hazards.any { it in WET }
        if (mode == TransportMode.Motorcycle) {
            if (rating == TravelRating.Unfavorable) add(TravelTip.ConsiderAlternative)
            if (wet || RainPossible in hazards) add(TravelTip.RainGear)
            if (StrongWind in hazards || Gale in hazards) add(TravelTip.RideCarefully)
        } else {
            // Even drivers walk to and from the car park.
            when {
                wet -> add(TravelTip.Umbrella)
                RainPossible in hazards -> add(TravelTip.ConsiderUmbrella)
            }
            if ((mode == TransportMode.Car || mode == TransportMode.PublicTransport) &&
                hazards.any { it in SLOW_TRAVEL && impact(it, mode) != None }
            ) {
                add(TravelTip.AllowExtraTime)
            }
        }
        if (mode != TransportMode.Car && (Heat in hazards || ExtremeHeat in hazards)) add(TravelTip.CarryWater)
        if (mode == TransportMode.Walking && HighUv in hazards) add(TravelTip.SunProtection)
    }

    private val WET = setOf(Thunderstorm, HeavyRain, RainLikely)

    /** Conditions that tend to slow down traffic and public transport. */
    private val SLOW_TRAVEL = setOf(Thunderstorm, HeavyRain, Wintry, DenseFog, PoorVisibility, Gale)

    private data class Impacts(val motorcycle: Impact, val car: Impact, val publicTransport: Impact, val walking: Impact, val other: Impact)

    // Columns: motorcycle, car, public transport, walking, other.
    private val IMPACTS: Map<WeatherHazard, Impacts> = mapOf(
        Thunderstorm to Impacts(Major, Minor, Minor, Major, Major),
        Gale to Impacts(Major, Minor, Minor, Major, Major),
        DenseFog to Impacts(Major, Major, Minor, None, Minor),
        ExtremeHeat to Impacts(Major, Minor, Minor, Major, Minor),
        Wintry to Impacts(Major, Minor, Minor, Minor, Minor),
        HeavyRain to Impacts(Major, Minor, Minor, Major, Major),
        RainLikely to Impacts(Major, None, Minor, Minor, Minor),
        StrongWind to Impacts(Minor, None, None, Minor, None),
        PoorVisibility to Impacts(Minor, Minor, None, None, Minor),
        Heat to Impacts(Minor, None, Minor, Minor, Minor),
        RainPossible to Impacts(Minor, None, None, Minor, Minor),
        HighUv to Impacts(None, None, None, Minor, None),
    )
}
