package com.ah0890.dailyweather.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ah0890.dailyweather.data.remote.WeatherRemoteDataSource
import com.ah0890.dailyweather.data.remote.openmeteo.OpenMeteoDataSource
import com.ah0890.dailyweather.location.AndroidLocationProvider
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException

sealed interface WearUiState {
    data object Loading : WearUiState
    data object NeedsPermission : WearUiState
    data object NoLocation : WearUiState
    data object NoConnection : WearUiState
    data class Loaded(val summary: WatchSummary) : WearUiState
}

/**
 * Finds the watch's approximate location once and downloads the forecast with the same client
 * and rounding as the phone app. Loads only when the app is opened or the user retries.
 */
class WearViewModel(application: Application) : AndroidViewModel(application) {

    private val locationProvider: LocationProvider = AndroidLocationProvider(application)
    private val remote: WeatherRemoteDataSource = OpenMeteoDataSource.create()

    private val state = MutableStateFlow<WearUiState>(WearUiState.Loading)
    val uiState: StateFlow<WearUiState> = state.asStateFlow()

    fun load() {
        state.value = WearUiState.Loading
        viewModelScope.launch {
            state.value = when (val location = locationProvider.currentLocation()) {
                is LocationResult.Success -> try {
                    val forecast = remote.fetchForecast(location.coordinates)
                    WearUiState.Loaded(WatchSummary.from(forecast, LocalDateTime.now(forecast.timeZone)))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    WearUiState.NoConnection
                }
                LocationResult.PermissionMissing -> WearUiState.NeedsPermission
                LocationResult.LocationDisabled, LocationResult.Unavailable -> WearUiState.NoLocation
            }
        }
    }
}
