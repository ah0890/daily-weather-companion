package com.ah0890.dailyweather.presentation.home

import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.model.WeatherForecast
import java.time.Instant

/** Forecast part of the Today screen. */
sealed interface WeatherUiState {

    /** No location yet, so nothing to load. */
    data object Idle : WeatherUiState

    /** First load for this location; nothing to show yet. */
    data object Loading : WeatherUiState

    /**
     * @property isRefreshing a newer forecast is being fetched in the background.
     * @property refreshError the last refresh failed; [forecast] is the previous successful one.
     */
    data class Loaded(
        val forecast: WeatherForecast,
        val fetchedAt: Instant,
        val isRefreshing: Boolean = false,
        val refreshError: WeatherError? = null,
    ) : WeatherUiState

    /** Loading failed and there is no earlier forecast to fall back on. */
    data class Error(val error: WeatherError) : WeatherUiState
}
