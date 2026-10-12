package com.ah0890.dailyweather.data.remote

import com.ah0890.dailyweather.domain.model.WeatherError
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException

/** Maps a network or provider failure to a reason the UI can explain. */
fun Exception.toWeatherError(): WeatherError = when (this) {
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
