package com.ah0890.dailyweather.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ah0890.dailyweather.DailyWeatherApplication
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.LocationResult
import com.ah0890.dailyweather.location.PlaceNameResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the location part of the Today screen. Permission dialogs live in the UI layer; the
 * screen reports what happened through [onPermissionChecked] and [onPermissionResult].
 */
class HomeViewModel(
    private val locationProvider: LocationProvider,
    private val placeNameResolver: PlaceNameResolver,
) : ViewModel() {

    private val _locationState = MutableStateFlow<LocationUiState>(LocationUiState.Checking)
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()

    private var locateJob: Job? = null

    /**
     * Called whenever the screen becomes visible, including after returning from Settings.
     * A location that has already been found is kept, so resuming the app doesn't trigger
     * another location request.
     */
    fun onPermissionChecked(granted: Boolean) {
        val state = _locationState.value
        if (!granted) {
            if (state == LocationUiState.Checking || state is LocationUiState.Failed) {
                _locationState.value = LocationUiState.PermissionRequired(PermissionDenial.None)
            }
            return
        }
        val shouldLocate = when (state) {
            LocationUiState.Checking, is LocationUiState.PermissionRequired -> true
            // The user may have just switched location services on in system settings.
            is LocationUiState.Failed -> state.reason == LocationFailure.ServicesDisabled
            LocationUiState.Locating, is LocationUiState.Located -> false
        }
        if (shouldLocate) locate()
    }

    /** Result of the system permission dialog. */
    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        if (granted) {
            locate()
        } else {
            val denial = if (canAskAgain) PermissionDenial.Denied else PermissionDenial.Permanent
            _locationState.value = LocationUiState.PermissionRequired(denial)
        }
    }

    /** User asked to look up the location again (e.g. after turning location services on). */
    fun retry() = locate()

    private fun locate() {
        if (locateJob?.isActive == true) return
        _locationState.value = LocationUiState.Locating
        locateJob = viewModelScope.launch {
            _locationState.value = when (val result = locationProvider.currentLocation()) {
                is LocationResult.Success -> LocationUiState.Located(
                    LocationData(result.coordinates, placeNameResolver.placeName(result.coordinates)),
                )
                LocationResult.PermissionMissing -> LocationUiState.PermissionRequired(PermissionDenial.None)
                LocationResult.LocationDisabled -> LocationUiState.Failed(LocationFailure.ServicesDisabled)
                LocationResult.Unavailable -> LocationUiState.Failed(LocationFailure.Unavailable)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DailyWeatherApplication).container
                HomeViewModel(container.locationProvider, container.placeNameResolver)
            }
        }
    }
}
