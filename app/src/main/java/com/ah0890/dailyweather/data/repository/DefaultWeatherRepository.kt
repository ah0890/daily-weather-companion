package com.ah0890.dailyweather.data.repository

import com.ah0890.dailyweather.data.remote.MalformedWeatherDataException
import com.ah0890.dailyweather.data.remote.WeatherRemoteDataSource
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.domain.repository.WeatherRepository
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

class DefaultWeatherRepository(
    private val remote: WeatherRemoteDataSource,
    private val clock: Clock = Clock.systemUTC(),
) : WeatherRepository {

    override suspend fun getForecast(coordinates: Coordinates): WeatherResult =
        try {
            val forecast = remote.fetchForecast(coordinates)
            WeatherResult.Success(forecast, fetchedAt = clock.instant())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WeatherResult.Failure(e.toWeatherError())
        }
}

internal fun Exception.toWeatherError(): WeatherError = when (this) {
    // Order matters: the first two are themselves IOExceptions.
    is MalformedWeatherDataException -> WeatherError.InvalidData
    // Covers SocketTimeoutException and OkHttp's whole-call timeout.
    is InterruptedIOException -> WeatherError.Timeout
    is IOException -> WeatherError.NoConnection
    is HttpException -> when (code()) {
        429 -> WeatherError.RateLimited
        in 500..599 -> WeatherError.ServiceUnavailable
        else -> WeatherError.Unexpected
    }
    else -> WeatherError.Unexpected
}
