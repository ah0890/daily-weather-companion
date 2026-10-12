package com.ah0890.dailyweather.domain.model

import java.time.Instant

/** Why a forecast could not be loaded, in terms the UI can explain to the user. */
enum class WeatherError {
    /** The device appears to be offline or the provider cannot be reached. */
    NoConnection,

    /** The provider took too long to answer. */
    Timeout,

    /** Too many requests were made; retrying later should succeed. */
    RateLimited,

    /** The provider is down or returned a server error. */
    ServiceUnavailable,

    /** The provider answered with data the app could not use. */
    InvalidData,

    /** Anything else. */
    Unexpected,
}

sealed interface WeatherResult {
    data class Success(val forecast: WeatherForecast, val fetchedAt: Instant) : WeatherResult

    /** Loading failed, but an earlier forecast for the same place was available offline. */
    data class Stale(val forecast: WeatherForecast, val fetchedAt: Instant, val error: WeatherError) : WeatherResult

    /** Loading failed and nothing was cached. */
    data class Failure(val error: WeatherError) : WeatherResult
}
