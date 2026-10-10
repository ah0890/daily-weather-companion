package com.ah0890.dailyweather.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ah0890.dailyweather.DailyWeatherApplication
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.domain.repository.LocationRepository
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.LocationResult
import com.ah0890.dailyweather.location.PlaceNameResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.Duration

/**
 * Drives the location part of the Today screen.
 *
 * The saved location is shown immediately. A fresh one-shot lookup only happens when nothing is
 * saved, when the saved location is older than [STALE_AFTER], or when the user asks for it.
 * Permission dialogs live in the UI layer, which reports back through [onPermissionChecked] and
 * [onPermissionResult].
 */
class HomeViewModel(
    private val locationProvider: LocationProvider,
    private val placeNameResolver: PlaceNameResolver,
    private val locationRepository: LocationRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val status = MutableStateFlow<LocationStatus>(LocationStatus.Checking)

    /** Location found during this session; kept in memory even if saving it fails. */
    private val liveLocation = MutableStateFlow<SavedLocation?>(null)

    val locationState: StateFlow<LocationUiState> =
        combine(locationRepository.savedLocation, liveLocation, status) { saved, live, status ->
            LocationUiState(
                location = live ?: saved,
                source = if (live != null) LocationSource.Current else LocationSource.Saved,
                status = status,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LocationUiState())

    private var locateJob: Job? = null

    /** Called whenever the screen becomes visible, including after returning from Settings. */
    fun onPermissionChecked(granted: Boolean) {
        viewModelScope.launch {
            val known = liveLocation.value ?: locationRepository.savedLocation.first()
            val current = status.value
            if (current == LocationStatus.Locating) return@launch

            if (!granted) {
                status.value = when {
                    current is LocationStatus.PermissionRequired -> current
                    // A saved location keeps working without permission; don't nag.
                    known != null -> LocationStatus.Idle
                    else -> LocationStatus.PermissionRequired(PermissionDenial.None)
                }
                return@launch
            }

            val shouldLocate = when (current) {
                LocationStatus.Checking -> known == null || known.isStale()
                // Back from Settings with permission, or location services just switched on.
                is LocationStatus.PermissionRequired -> true
                is LocationStatus.Failed -> current.reason == LocationFailure.ServicesDisabled
                LocationStatus.Idle -> known == null
                LocationStatus.Locating -> false
            }
            when {
                // A background refresh of a stale saved location shouldn't surface errors.
                shouldLocate -> locate(quiet = current == LocationStatus.Checking && known != null)
                current == LocationStatus.Checking -> status.value = LocationStatus.Idle
            }
        }
    }

    /** Result of the system permission dialog. */
    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        if (granted) {
            locate(quiet = false)
        } else {
            val denial = if (canAskAgain) PermissionDenial.Denied else PermissionDenial.Permanent
            status.value = LocationStatus.PermissionRequired(denial)
        }
    }

    /** The user asked to find their location again. */
    fun updateLocation() = locate(quiet = false)

    private fun locate(quiet: Boolean) {
        if (locateJob?.isActive == true) return
        status.value = LocationStatus.Locating
        locateJob = viewModelScope.launch {
            status.value = when (val result = locationProvider.currentLocation()) {
                is LocationResult.Success -> {
                    val location = LocationData(result.coordinates, placeNameResolver.placeName(result.coordinates))
                    liveLocation.value = SavedLocation(location, savedAt = clock.instant())
                    try {
                        locationRepository.save(location)
                    } catch (_: IOException) {
                        // Still usable for this session; it just won't be remembered.
                    }
                    LocationStatus.Idle
                }
                LocationResult.PermissionMissing ->
                    if (quiet) LocationStatus.Idle else LocationStatus.PermissionRequired(PermissionDenial.None)
                LocationResult.LocationDisabled ->
                    if (quiet) LocationStatus.Idle else LocationStatus.Failed(LocationFailure.ServicesDisabled)
                LocationResult.Unavailable ->
                    if (quiet) LocationStatus.Idle else LocationStatus.Failed(LocationFailure.Unavailable)
            }
        }
    }

    private fun SavedLocation.isStale(): Boolean =
        Duration.between(savedAt, clock.instant()) > STALE_AFTER

    companion object {
        val STALE_AFTER: Duration = Duration.ofHours(6)

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DailyWeatherApplication).container
                HomeViewModel(container.locationProvider, container.placeNameResolver, container.locationRepository)
            }
        }
    }
}
