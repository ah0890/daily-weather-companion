package com.ah0890.dailyweather.presentation.weather

import androidx.annotation.StringRes
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.Place
import com.ah0890.dailyweather.domain.model.PlaceType

val PlaceType.emoji: String
    get() = when (this) {
        PlaceType.Home -> "🏠"
        PlaceType.Office -> "🏢"
        PlaceType.Parents -> "🏡"
        PlaceType.Other -> "📍"
    }

@get:StringRes
val PlaceType.labelRes: Int
    get() = when (this) {
        PlaceType.Home -> R.string.place_type_home
        PlaceType.Office -> R.string.place_type_office
        PlaceType.Parents -> R.string.place_type_parents
        PlaceType.Other -> R.string.place_type_other
    }

/** e.g. "🏢 Office". */
val Place.title: String get() = "${type.emoji} $name"
