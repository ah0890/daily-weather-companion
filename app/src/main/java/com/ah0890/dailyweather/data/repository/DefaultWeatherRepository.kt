package com.ah0890.dailyweather.data.repository

import com.ah0890.dailyweather.data.local.CachedForecast
import com.ah0890.dailyweather.data.local.WeatherCache
import com.ah0890.dailyweather.data.remote.MalformedWeatherDataException
import com.ah0890.dailyweather.data.remote.WeatherRemoteDataSource
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.domain.repository.WeatherRepository
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class DefaultWeatherRepository(
    private val remote: WeatherRemoteDataSource,
    private val cache: WeatherCache? = null,
    private val clock: Clock = Clock.systemUTC(),
) : WeatherRepository {

    override suspend fun getForecast(coordinates: Coordinates): WeatherResult {
        val error = try {
            val forecast = remote.fetchForecast(coordinates)
            val fetchedAt = clock.instant()
            saveQuietly(forecast, fetchedAt)
            return WeatherResult.Success(forecast, fetchedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toWeatherError()
        }
        val cached = loadQuietly(coordinates) ?: return WeatherResult.Failure(error)
        return WeatherResult.Stale(cached.forecast, cached.fetchedAt, error)
    }

    override suspend fun cachedForecast(coordinates: Coordinates): WeatherResult.Success? =
        loadQuietly(coordinates)?.let { WeatherResult.Success(it.forecast, it.fetchedAt) }

    // The cache is a convenience: a full disk or unreadable file must never break loading.
    private suspend fun saveQuietly(forecast: WeatherForecast, fetchedAt: Instant) {
        try {
            cache?.save(forecast, fetchedAt)
        } catch (_: IOException) {
        }
    }

    private suspend fun loadQuietly(coordinates: Coordinates): CachedForecast? =
        try {
            cache?.load(coordinates)
        } catch (_: IOException) {
            null
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
