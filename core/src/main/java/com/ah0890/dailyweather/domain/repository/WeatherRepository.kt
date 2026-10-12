package com.ah0890.dailyweather.domain.repository

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.WeatherResult

/** Single entry point for forecast data. The UI and use cases depend on this, never on a provider. */
interface WeatherRepository {

    /**
     * Loads the latest forecast, falling back to the cached one ([WeatherResult.Stale]) if that
     * fails. Never throws for network or data problems.
     */
    suspend fun getForecast(coordinates: Coordinates): WeatherResult

    /** The last forecast saved on the device for these coordinates, without any network access. */
    suspend fun cachedForecast(coordinates: Coordinates): WeatherResult.Success?
}
