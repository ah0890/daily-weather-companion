package com.ah0890.dailyweather.testing

import com.ah0890.dailyweather.domain.model.AirQuality
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.repository.AirQualityRepository

/** Returns [result] and records the places asked for; unavailable by default. */
class FakeAirQualityRepository(var result: AirQuality? = null) : AirQualityRepository {
    val requests = mutableListOf<Coordinates>()

    override suspend fun airQuality(coordinates: Coordinates, refresh: Boolean): AirQuality? {
        requests += coordinates
        return result
    }
}
