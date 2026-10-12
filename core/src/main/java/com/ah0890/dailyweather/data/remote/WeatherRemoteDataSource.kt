package com.ah0890.dailyweather.data.remote

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.WeatherForecast

/**
 * Fetches a forecast from a weather provider. Keeping this behind an interface lets the
 * provider be swapped (or faked in tests) without touching the repository or UI.
 */
interface WeatherRemoteDataSource {

    /**
     * @throws java.io.IOException on network failure or [MalformedWeatherDataException]
     *   when the response is unusable.
     * @throws retrofit2.HttpException when the provider returns an HTTP error.
     */
    suspend fun fetchForecast(coordinates: Coordinates): WeatherForecast
}
