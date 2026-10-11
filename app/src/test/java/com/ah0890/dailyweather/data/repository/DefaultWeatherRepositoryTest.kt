package com.ah0890.dailyweather.data.repository

import com.ah0890.dailyweather.data.remote.MalformedWeatherDataException
import com.ah0890.dailyweather.data.remote.WeatherRemoteDataSource
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.testing.SampleForecasts
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException

class DefaultWeatherRepositoryTest {

    private val now = Instant.parse("2026-10-10T07:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private class FakeRemote(private val result: () -> WeatherForecast) : WeatherRemoteDataSource {
        var requested: Coordinates? = null
        override suspend fun fetchForecast(coordinates: Coordinates): WeatherForecast {
            requested = coordinates
            return result()
        }
    }

    private suspend fun resultWhenRemoteThrows(error: Exception): WeatherResult =
        DefaultWeatherRepository(FakeRemote { throw error }, clock = clock).getForecast(SampleForecasts.london)

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `success returns forecast with fetch time`() = runTest {
        val forecast = SampleForecasts.forecast()
        val remote = FakeRemote { forecast }

        val result = DefaultWeatherRepository(remote, clock = clock).getForecast(SampleForecasts.london)

        assertEquals(WeatherResult.Success(forecast, fetchedAt = now), result)
        assertEquals(SampleForecasts.london, remote.requested)
    }

    @Test
    fun `offline errors map to no connection`() = runTest {
        assertEquals(WeatherResult.Failure(WeatherError.NoConnection), resultWhenRemoteThrows(UnknownHostException()))
        assertEquals(WeatherResult.Failure(WeatherError.NoConnection), resultWhenRemoteThrows(ConnectException()))
        assertEquals(WeatherResult.Failure(WeatherError.NoConnection), resultWhenRemoteThrows(IOException("reset")))
    }

    @Test
    fun `socket and call timeouts map to timeout`() = runTest {
        assertEquals(WeatherResult.Failure(WeatherError.Timeout), resultWhenRemoteThrows(SocketTimeoutException()))
        assertEquals(WeatherResult.Failure(WeatherError.Timeout), resultWhenRemoteThrows(InterruptedIOException("timeout")))
    }

    @Test
    fun `malformed data maps to invalid data, not connection error`() = runTest {
        assertEquals(
            WeatherResult.Failure(WeatherError.InvalidData),
            resultWhenRemoteThrows(MalformedWeatherDataException("no daily data")),
        )
    }

    @Test
    fun `http 429 maps to rate limited`() = runTest {
        assertEquals(WeatherResult.Failure(WeatherError.RateLimited), resultWhenRemoteThrows(httpError(429)))
    }

    @Test
    fun `http 5xx maps to service unavailable`() = runTest {
        assertEquals(WeatherResult.Failure(WeatherError.ServiceUnavailable), resultWhenRemoteThrows(httpError(500)))
        assertEquals(WeatherResult.Failure(WeatherError.ServiceUnavailable), resultWhenRemoteThrows(httpError(503)))
    }

    @Test
    fun `other http errors map to unexpected`() = runTest {
        assertEquals(WeatherResult.Failure(WeatherError.Unexpected), resultWhenRemoteThrows(httpError(400)))
        assertEquals(WeatherResult.Failure(WeatherError.Unexpected), resultWhenRemoteThrows(httpError(404)))
    }

    @Test
    fun `unknown exceptions map to unexpected instead of crashing`() = runTest {
        assertEquals(WeatherResult.Failure(WeatherError.Unexpected), resultWhenRemoteThrows(IllegalStateException()))
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is propagated, not swallowed`() = runTest {
        resultWhenRemoteThrows(CancellationException("screen closed"))
    }
}
