package com.ah0890.dailyweather.presentation.home

import com.ah0890.dailyweather.domain.model.LocationData

/** What the Today screen knows about the location it should show weather for. */
sealed interface LocationUiState {

    /** Permission hasn't been checked yet (first frame). */
    data object Checking : LocationUiState

    /** The app needs location permission before it can continue. */
    data class PermissionRequired(val denial: PermissionDenial) : LocationUiState

    data object Locating : LocationUiState

    data class Located(val location: LocationData) : LocationUiState

    data class Failed(val reason: LocationFailure) : LocationUiState
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
