package com.ah0890.dailyweather.presentation.weather

import androidx.annotation.StringRes
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.ChecklistItem
import com.ah0890.dailyweather.domain.model.ChecklistLevel
import com.ah0890.dailyweather.domain.model.ScoreBand
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.TravelTip
import com.ah0890.dailyweather.domain.model.WeatherHazard

// Text and icons for travel advice. Plain resource ids (not composables) so notifications and the
// widget can use them too. Emoji are decorative; the text always carries the meaning.

val TransportMode.emoji: String
    get() = when (this) {
        TransportMode.Motorcycle -> "🏍️"
        TransportMode.Car -> "🚗"
        TransportMode.PublicTransport -> "🚌"
        TransportMode.Walking -> "🚶"
        TransportMode.Other -> "🧭"
    }

@get:StringRes
val TransportMode.labelRes: Int
    get() = when (this) {
        TransportMode.Motorcycle -> R.string.transport_motorcycle
        TransportMode.Car -> R.string.transport_car
        TransportMode.PublicTransport -> R.string.transport_public
        TransportMode.Walking -> R.string.transport_walking
        TransportMode.Other -> R.string.transport_other
    }

/** Traffic-light dot that goes with a rating; always shown next to the rating's text. */
val TravelRating.dot: String
    get() = when (this) {
        TravelRating.Favorable -> "🟢"
        TravelRating.Caution -> "🟡"
        TravelRating.Unfavorable -> "🔴"
    }

/** Headline for a trip, worded for the way the user travels. */
@StringRes
fun headlineRes(mode: TransportMode, rating: TravelRating): Int = when (mode) {
    TransportMode.Motorcycle -> when (rating) {
        TravelRating.Favorable -> R.string.advice_motorcycle_good
        TravelRating.Caution -> R.string.advice_motorcycle_caution
        TravelRating.Unfavorable -> R.string.advice_motorcycle_bad
    }
    TransportMode.Car -> when (rating) {
        TravelRating.Favorable -> R.string.advice_car_good
        TravelRating.Caution -> R.string.advice_car_caution
        TravelRating.Unfavorable -> R.string.advice_car_bad
    }
    TransportMode.PublicTransport -> when (rating) {
        TravelRating.Favorable -> R.string.advice_public_good
        TravelRating.Caution -> R.string.advice_public_caution
        TravelRating.Unfavorable -> R.string.advice_public_bad
    }
    TransportMode.Walking -> when (rating) {
        TravelRating.Favorable -> R.string.advice_walking_good
        TravelRating.Caution -> R.string.advice_walking_caution
        TravelRating.Unfavorable -> R.string.advice_walking_bad
    }
    TransportMode.Other -> when (rating) {
        TravelRating.Favorable -> R.string.advice_other_good
        TravelRating.Caution -> R.string.advice_other_caution
        TravelRating.Unfavorable -> R.string.advice_other_bad
    }
}

val WeatherHazard.emoji: String
    get() = when (this) {
        WeatherHazard.Thunderstorm -> "⛈️"
        WeatherHazard.Gale, WeatherHazard.StrongWind -> "💨"
        WeatherHazard.DenseFog, WeatherHazard.PoorVisibility -> "🌫️"
        WeatherHazard.ExtremeHeat, WeatherHazard.Heat -> "🌡️"
        WeatherHazard.Wintry -> "❄️"
        WeatherHazard.HeavyRain, WeatherHazard.RainLikely, WeatherHazard.RainPossible -> "🌧️"
        WeatherHazard.HighUv -> "☀️"
    }

@get:StringRes
val WeatherHazard.labelRes: Int
    get() = when (this) {
        WeatherHazard.Thunderstorm -> R.string.hazard_thunderstorm
        WeatherHazard.Gale -> R.string.hazard_gale
        WeatherHazard.DenseFog -> R.string.hazard_dense_fog
        WeatherHazard.ExtremeHeat -> R.string.hazard_extreme_heat
        WeatherHazard.Wintry -> R.string.hazard_wintry
        WeatherHazard.HeavyRain -> R.string.hazard_heavy_rain
        WeatherHazard.RainLikely -> R.string.hazard_rain_likely
        WeatherHazard.StrongWind -> R.string.hazard_strong_wind
        WeatherHazard.PoorVisibility -> R.string.hazard_poor_visibility
        WeatherHazard.Heat -> R.string.hazard_heat
        WeatherHazard.RainPossible -> R.string.hazard_rain_possible
        WeatherHazard.HighUv -> R.string.hazard_high_uv
    }

@get:StringRes
val TravelTip.textRes: Int
    get() = when (this) {
        TravelTip.ConsiderAlternative -> R.string.tip_consider_alternative
        TravelTip.RainGear -> R.string.tip_rain_gear
        TravelTip.Umbrella -> R.string.tip_umbrella
        TravelTip.ConsiderUmbrella -> R.string.tip_consider_umbrella
        TravelTip.AllowExtraTime -> R.string.tip_extra_time
        TravelTip.RideCarefully -> R.string.tip_ride_carefully
        TravelTip.CarryWater -> R.string.tip_water
        TravelTip.SunProtection -> R.string.tip_sun_protection
    }

@get:StringRes
val ScoreBand.labelRes: Int
    get() = when (this) {
        ScoreBand.Good -> R.string.score_good
        ScoreBand.Moderate -> R.string.score_moderate
        ScoreBand.Poor -> R.string.score_poor
    }

val ChecklistItem.emoji: String
    get() = when (this) {
        ChecklistItem.Umbrella -> "☔"
        ChecklistItem.RainGear -> "🌧️"
        ChecklistItem.AlternativeTransport -> "🏍️"
        ChecklistItem.Jacket -> "🧥"
        ChecklistItem.Water -> "💧"
        ChecklistItem.Sunglasses -> "🕶️"
        ChecklistItem.SunProtection -> "🧴"
    }

@get:StringRes
val ChecklistItem.labelRes: Int
    get() = when (this) {
        ChecklistItem.Umbrella -> R.string.checklist_umbrella
        ChecklistItem.RainGear -> R.string.checklist_rain_gear
        ChecklistItem.AlternativeTransport -> R.string.checklist_motorcycle
        ChecklistItem.Jacket -> R.string.checklist_jacket
        ChecklistItem.Water -> R.string.checklist_water
        ChecklistItem.Sunglasses -> R.string.checklist_sunglasses
        ChecklistItem.SunProtection -> R.string.checklist_sun_protection
    }

@get:StringRes
val ChecklistLevel.labelRes: Int
    get() = when (this) {
        ChecklistLevel.Recommended -> R.string.checklist_recommended
        ChecklistLevel.Useful -> R.string.checklist_useful
    }
