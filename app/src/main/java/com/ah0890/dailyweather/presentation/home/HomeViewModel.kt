package com.ah0890.dailyweather.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ah0890.dailyweather.DailyWeatherApplication
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.Places
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.domain.repository.LocationRepository
import com.ah0890.dailyweather.domain.repository.PlacesRepository
import com.ah0890.dailyweather.domain.repository.WeatherRepository
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.LocationResult
import com.ah0890.dailyweather.location.PlaceNameResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * Drives the Today screen: which location to show, and the forecast for it.
 *
 * The saved location is shown immediately. A fresh one-shot lookup only happens when nothing is
 * saved, when the saved location is older than [STALE_AFTER], or when the user asks for it.
 * Permission dialogs live in the UI layer, which reports back through [onPermissionChecked] and
 * [onPermissionResult].
 *
 * The forecast is loaded whenever the location changes, on request, and on return to the screen
 * once it is older than [WEATHER_REFRESH_AFTER]; it is never polled.
 *
 * Instead of the device location the user can pick a saved place; the default place is shown
 * first. When home and office places are set, their forecasts are loaded too (cache first) so the
 * commute uses the weather where each trip starts. No other saved place is ever requested.
 */
class HomeViewModel(
    private val locationProvider: LocationProvider,
    private val placeNameResolver: PlaceNameResolver,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val placesRepository: PlacesRepository,
) : ViewModel() {

    private val status = MutableStateFlow<LocationStatus>(LocationStatus.Checking)

    /** Location found during this session; kept in memory even if saving it fails. */
    private val liveLocation = MutableStateFlow<SavedLocation?>(null)

    private val choice = MutableStateFlow<LocationChoice>(LocationChoice.Default)

    val places: StateFlow<Places> = placesRepository.places
        .stateIn(viewModelScope, SharingStarted.Eagerly, Places())

    val locationState: StateFlow<LocationUiState> =
        combine(locationRepository.savedLocation, liveLocation, status, places, choice) { saved, live, status, places, choice ->
            val device = live ?: saved
            val place = when (choice) {
                LocationChoice.Default -> places.default
                LocationChoice.Device -> null
                is LocationChoice.SavedPlace -> places.find(choice.id) ?: places.default
            }
            LocationUiState(
                location = place?.let { SavedLocation(LocationData(it.coordinates, it.name), Instant.EPOCH) } ?: device,
                source = when {
                    place != null -> LocationSource.Place
                    live != null -> LocationSource.Current
                    else -> LocationSource.Saved
                },
                status = status,
                place = place,
                deviceLocation = device,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LocationUiState())

    private val commute = MutableStateFlow(CommuteForecasts())
    val commuteState: StateFlow<CommuteForecasts> = commute.asStateFlow()
    private var commuteJob: Job? = null

    private var locateJob: Job? = null

    private val weather = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val weatherState: StateFlow<WeatherUiState> = weather.asStateFlow()

    private var weatherJob: Job? = null

    /** Coordinates the current [weather] state belongs to. */
    private var weatherCoordinates: Coordinates? = null

    init {
        viewModelScope.launch {
            locationState
                .map { it.location?.location?.coordinates?.approximate() }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { loadWeather(it, useFreshCache = true) }
        }
        viewModelScope.launch {
            places
                .map { it.home?.coordinates to it.office?.coordinates }
                .distinctUntilChanged()
                // Until the forecast on screen has loaded, its own load does this when it finishes,
                // so a place that is both shown and home isn't downloaded twice.
                .collect { if (weather.value is WeatherUiState.Loaded) loadCommute(force = false) }
        }
    }

    /** Shows a saved place instead of the device location (for this session). */
    fun selectPlace(id: String) {
        choice.value = LocationChoice.SavedPlace(id)
        if (status.value != LocationStatus.Locating) status.value = LocationStatus.Idle
    }

    /** Back to the device location; the UI then reports permission through [onPermissionChecked]. */
    fun selectDeviceLocation() {
        choice.value = LocationChoice.Device
        if (status.value != LocationStatus.Locating) status.value = LocationStatus.Idle
    }

    /**
     * Loads the home and office forecasts for the commute. The forecast on screen is reused, and
     * cached copies younger than [WEATHER_REFRESH_AFTER] are used unless [force] is set.
     */
    private fun loadCommute(force: Boolean) {
        commuteJob?.cancel()
        commuteJob = viewModelScope.launch {
            val places = placesRepository.places.first()
            val shown = weather.value as? WeatherUiState.Loaded

            suspend fun forecastAt(coordinates: Coordinates?): WeatherForecast? {
                val cell = coordinates?.approximate() ?: return null
                if (shown != null && cell == weatherCoordinates) return shown.forecast
                val cached = weatherRepository.cachedForecast(cell)
                if (!force && cached != null && !isOld(cached.fetchedAt)) return loaded(cached.forecast, cached.fetchedAt).forecast
                return when (val result = weatherRepository.getForecast(cell)) {
                    is WeatherResult.Success -> loaded(result.forecast, result.fetchedAt).forecast
                    is WeatherResult.Stale -> loaded(result.forecast, result.fetchedAt).forecast
                    is WeatherResult.Failure -> cached?.let { loaded(it.forecast, it.fetchedAt).forecast }
                }
            }

            commute.value = CommuteForecasts(home = forecastAt(places.home?.coordinates), office = forecastAt(places.office?.coordinates))
        }
    }

    /** Pull-to-refresh or retry. */
    fun refreshWeather() {
        weatherCoordinates?.let { loadWeather(it, useFreshCache = false) }
    }

    /** Called when the screen becomes visible; refreshes only if the forecast is getting old. */
    fun refreshWeatherIfStale() {
        val loaded = weather.value as? WeatherUiState.Loaded ?: return
        if (isOld(loaded.fetchedAt)) refreshWeather()
    }

    /**
     * @param useFreshCache when true, a cached forecast younger than [WEATHER_REFRESH_AFTER] is
     *   shown without any network request.
     */
    private fun loadWeather(coordinates: Coordinates, useFreshCache: Boolean) {
        weatherJob?.cancel()
        // Keep showing the previous forecast while refreshing the same place.
        var shown = (weather.value as? WeatherUiState.Loaded)?.takeIf { weatherCoordinates == coordinates }
        weatherCoordinates = coordinates
        weather.value = shown?.copy(isRefreshing = true, refreshError = null) ?: WeatherUiState.Loading
        weatherJob = viewModelScope.launch {
            if (shown == null) {
                // Show the offline copy straight away, then decide whether to refresh it.
                weatherRepository.cachedForecast(coordinates)?.let { cached ->
                    val fromCache = loaded(cached.forecast, cached.fetchedAt)
                    shown = fromCache
                    if (useFreshCache && !isOld(cached.fetchedAt)) {
                        weather.value = fromCache
                        loadCommute(force = false)
                        return@launch
                    }
                    weather.value = fromCache.copy(isRefreshing = true)
                }
            }
            weather.value = when (val result = weatherRepository.getForecast(coordinates)) {
                is WeatherResult.Success -> loaded(result.forecast, result.fetchedAt)
                is WeatherResult.Stale -> loaded(result.forecast, result.fetchedAt).copy(refreshError = result.error)
                is WeatherResult.Failure ->
                    shown?.copy(isRefreshing = false, refreshError = result.error) ?: WeatherUiState.Error(result.error)
            }
            loadCommute(force = !useFreshCache)
        }
    }

    /** Wraps a forecast for display, moved forward to the current time at its location. */
    private fun loaded(forecast: WeatherForecast, fetchedAt: Instant): WeatherUiState.Loaded {
        val localNow = LocalDateTime.ofInstant(clock.instant(), forecast.timeZone)
        return WeatherUiState.Loaded(forecast.advancedTo(localNow), fetchedAt)
    }

    private fun isOld(fetchedAt: Instant) = Duration.between(fetchedAt, clock.instant()) > WEATHER_REFRESH_AFTER

    /** Called whenever the screen becomes visible, including after returning from Settings. */
    fun onPermissionChecked(granted: Boolean) {
        viewModelScope.launch {
            val known = liveLocation.value ?: locationRepository.savedLocation.first()
            val current = status.value
            if (current == LocationStatus.Locating) return@launch
            // A saved place is on screen: the device location isn't needed, so don't look it up.
            if (showsPlace()) {
                if (current == LocationStatus.Checking) status.value = LocationStatus.Idle
                return@launch
            }

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

    private suspend fun showsPlace(): Boolean {
        val places = placesRepository.places.first()
        return when (val choice = choice.value) {
            LocationChoice.Default -> places.default != null
            LocationChoice.Device -> false
            is LocationChoice.SavedPlace -> (places.find(choice.id) ?: places.default) != null
        }
    }

    private fun SavedLocation.isStale(): Boolean =
        Duration.between(savedAt, clock.instant()) > STALE_AFTER

    companion object {
        val STALE_AFTER: Duration = Duration.ofHours(6)
        val WEATHER_REFRESH_AFTER: Duration = Duration.ofMinutes(30)

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DailyWeatherApplication).container
                HomeViewModel(
                    container.locationProvider,
                    container.placeNameResolver,
                    container.locationRepository,
                    container.weatherRepository,
                    placesRepository = container.placesRepository,
                )
            }
        }
    }
}
