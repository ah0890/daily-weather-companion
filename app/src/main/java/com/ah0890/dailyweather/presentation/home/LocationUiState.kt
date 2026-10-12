package com.ah0890.dailyweather.presentation.home

import com.ah0890.dailyweather.domain.model.Place
import com.ah0890.dailyweather.domain.model.SavedLocation

/**
 * Location shown on the Today screen and what the app is currently doing about it.
 *
 * @property location the location weather is shown for, or null if none is known yet.
 * @property source where [location] comes from.
 * @property place the saved place being shown, when [source] is [LocationSource.Place].
 * @property deviceLocation the device's own location (found now or saved earlier), whatever is shown.
 */
data class LocationUiState(
    val location: SavedLocation? = null,
    val source: LocationSource = LocationSource.Saved,
    val status: LocationStatus = LocationStatus.Checking,
    val place: Place? = null,
    val deviceLocation: SavedLocation? = location.takeIf { place == null },
)

enum class LocationSource {
    /** The device location loaded from storage; may be from an earlier day. */
    Saved,

    /** Found from the device's location during this session. */
    Current,

    /** One of the user's saved places, e.g. "Office". */
    Place,
}

/** Which location the user wants to see on the Today screen. */
sealed interface LocationChoice {
    /** The default saved place if there is one, otherwise the device location. */
    data object Default : LocationChoice

    data object Device : LocationChoice

    data class SavedPlace(val id: String) : LocationChoice
}

sealed interface LocationStatus {

    /** Permission and saved location haven't been checked yet (first frame). */
    data object Checking : LocationStatus

    /** Nothing in progress. */
    data object Idle : LocationStatus

    data object Locating : LocationStatus

    /** The app needs location permission to (re)discover the location. */
    data class PermissionRequired(val denial: PermissionDenial) : LocationStatus

    data class Failed(val reason: LocationFailure) : LocationStatus
}

enum class PermissionDenial {
    /** Never asked in this session: show the explanation and an "Allow" button. */
    None,

    /** Denied, but Android will still show the dialog again if the user asks. */
    Denied,

    /** Denied with "don't ask again" (or twice); only system Settings can grant it now. */
    Permanent,
}

enum class LocationFailure {
    /** Location services are switched off for the whole device. */
    ServicesDisabled,

    /** No location could be found in time. */
    Unavailable,
}
