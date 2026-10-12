package com.ah0890.dailyweather.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ah0890.dailyweather.DailyWeatherApplication
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.PlaceSuggestion
import com.ah0890.dailyweather.domain.model.PlaceType
import com.ah0890.dailyweather.domain.model.Places
import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.repository.PlaceSearchRepository
import com.ah0890.dailyweather.domain.repository.PlacesRepository
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.LocationResult
import com.ah0890.dailyweather.location.PlaceNameResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** State of the "Add place" dialog while it looks something up. */
sealed interface AddPlaceState {
    data object Idle : AddPlaceState
    data object Searching : AddPlaceState
    data class Results(val suggestions: List<PlaceSuggestion>) : AddPlaceState
    data class SearchFailed(val error: WeatherError) : AddPlaceState
    data object Locating : AddPlaceState

    /** The device location couldn't be found (permission, services off or no fix). */
    data object LocationFailed : AddPlaceState

    /** The place was saved; the dialog can close. */
    data object Added : AddPlaceState

    /** [Places.MAX_PLACES] are already saved. */
    data object Full : AddPlaceState

    data object SaveFailed : AddPlaceState
}

/** Manages saved places: adding (by search or current location), editing, roles and deleting. */
class PlacesViewModel(
    private val repository: PlacesRepository,
    private val search: PlaceSearchRepository,
    private val locationProvider: LocationProvider,
    private val placeNameResolver: PlaceNameResolver,
) : ViewModel() {

    val places: StateFlow<Places> = repository.places
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Places())

    private val add = MutableStateFlow<AddPlaceState>(AddPlaceState.Idle)
    val addState: StateFlow<AddPlaceState> = add.asStateFlow()
    private var addJob: Job? = null

    fun search(query: String) {
        addJob?.cancel()
        add.value = AddPlaceState.Searching
        addJob = viewModelScope.launch {
            add.value = when (val result = search.search(query)) {
                is PlaceSearchRepository.Result.Success -> AddPlaceState.Results(result.suggestions)
                is PlaceSearchRepository.Result.Failure -> AddPlaceState.SearchFailed(result.error)
            }
        }
    }

    fun addSuggestion(name: String, type: PlaceType, suggestion: PlaceSuggestion) {
        addJob?.cancel()
        addJob = viewModelScope.launch { save(name, type, suggestion.coordinates, suggestion.name) }
    }

    /** One-shot lookup of where the phone is now, e.g. while at the office. */
    fun addCurrentLocation(name: String, type: PlaceType) {
        addJob?.cancel()
        add.value = AddPlaceState.Locating
        addJob = viewModelScope.launch {
            when (val result = locationProvider.currentLocation()) {
                is LocationResult.Success -> save(name, type, result.coordinates, placeNameResolver.placeName(result.coordinates))
                else -> add.value = AddPlaceState.LocationFailed
            }
        }
    }

    /** The add dialog was closed or opened again. */
    fun resetAdd() {
        addJob?.cancel()
        add.value = AddPlaceState.Idle
    }

    fun update(id: String, name: String, type: PlaceType) = launch { repository.update(id, name, type) }

    fun delete(id: String) = launch { repository.delete(id) }

    fun setHome(id: String?) = launch { repository.setHome(id) }

    fun setOffice(id: String?) = launch { repository.setOffice(id) }

    fun setDefault(id: String?) = launch { repository.setDefault(id) }

    private suspend fun save(name: String, type: PlaceType, coordinates: Coordinates, placeName: String?) {
        add.value = try {
            if (repository.add(name, type, coordinates, placeName) != null) AddPlaceState.Added else AddPlaceState.Full
        } catch (_: IOException) {
            AddPlaceState.SaveFailed
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: IOException) {
                // Storage problem: the list keeps showing the previous state.
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DailyWeatherApplication).container
                PlacesViewModel(
                    container.placesRepository,
                    container.placeSearch,
                    container.locationProvider,
                    container.placeNameResolver,
                )
            }
        }
    }
}
