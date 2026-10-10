package com.ah0890.dailyweather.domain.model

/**
 * A place the forecast is shown for.
 *
 * @property name human-readable place name, or null when it could not be resolved.
 */
data class LocationData(
    val coordinates: Coordinates,
    val name: String?,
)
