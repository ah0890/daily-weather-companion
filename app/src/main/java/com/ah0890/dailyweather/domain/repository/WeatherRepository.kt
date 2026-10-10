package com.ah0890.dailyweather.domain.repository

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.WeatherResult

/** Single entry point for forecast data. The UI and use cases depend on this, never on a provider. */
interface WeatherRepository {

    /** Loads the latest forecast. Never throws for network or data problems; see [WeatherResult.Failure]. */
    suspend fun getForecast(coordinates: Coordinates): WeatherResult
}
