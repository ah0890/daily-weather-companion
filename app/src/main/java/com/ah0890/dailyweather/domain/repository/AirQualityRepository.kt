package com.ah0890.dailyweather.domain.repository

import com.ah0890.dailyweather.domain.model.AirQuality
import com.ah0890.dailyweather.domain.model.Coordinates

/** Optional air-quality data. Weather never depends on it. */
interface AirQualityRepository {

    /**
     * @param refresh download even if a recent copy is available.
     * @return the air quality, or null when it is unavailable (offline, provider error or no data
     *   for this place). Never throws for network or data problems.
     */
    suspend fun airQuality(coordinates: Coordinates, refresh: Boolean = false): AirQuality?
}
