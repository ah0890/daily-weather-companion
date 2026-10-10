package com.ah0890.dailyweather.domain.model

import kotlin.math.roundToLong

/** A validated latitude/longitude pair in decimal degrees. */
data class Coordinates(val latitude: Double, val longitude: Double) {

    init {
        require(latitude in -90.0..90.0) { "Latitude out of range" }
        require(longitude in -180.0..180.0) { "Longitude out of range" }
    }

    /**
     * Coordinates rounded to two decimal places (roughly 1 km). Forecast models are far coarser
     * than this, so sending the rounded value loses no accuracy while sharing less precise
     * location data with the weather provider.
     */
    fun approximate(): Coordinates = Coordinates(latitude.round2(), longitude.round2())

    private fun Double.round2(): Double = (this * 100).roundToLong() / 100.0
}
