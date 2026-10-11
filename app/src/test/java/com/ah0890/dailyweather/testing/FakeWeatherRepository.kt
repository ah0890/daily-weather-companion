package com.ah0890.dailyweather.testing

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.domain.repository.WeatherRepository
import java.time.Instant

/** Returns [result] (computed per request) and records the coordinates asked for. */
class FakeWeatherRepository(
    var result: (Coordinates) -> WeatherResult = { WeatherResult.Success(SampleForecasts.forecast(it), Instant.EPOCH) },
) : WeatherRepository {
    val requests = mutableListOf<Coordinates>()

    override suspend fun getForecast(coordinates: Coordinates): WeatherResult {
        requests += coordinates
        return result(coordinates)
    }
}
