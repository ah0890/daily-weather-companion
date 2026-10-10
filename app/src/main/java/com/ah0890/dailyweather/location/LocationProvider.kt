package com.ah0890.dailyweather.location

import com.ah0890.dailyweather.domain.model.Coordinates

/** One-shot access to the device's current location. Never tracks location continuously. */
interface LocationProvider {
    suspend fun currentLocation(): LocationResult
}

sealed interface LocationResult {
    data class Success(val coordinates: Coordinates) : LocationResult

    /** Location permission has not been granted. */
    data object PermissionMissing : LocationResult

    /** Location services are switched off in system settings. */
    data object LocationDisabled : LocationResult

    /** No fix could be obtained in time (e.g. indoors with no network location). */
    data object Unavailable : LocationResult
}

/** Turns coordinates into a short place name such as a town or city. */
interface PlaceNameResolver {
    /** @return a place name, or null if none could be found. Never throws. */
    suspend fun placeName(coordinates: Coordinates): String?
}
