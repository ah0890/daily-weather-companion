package com.ah0890.dailyweather.data.repository

import com.ah0890.dailyweather.data.local.CachedForecast
import com.ah0890.dailyweather.data.local.WeatherCache
import com.ah0890.dailyweather.data.remote.WeatherRemoteDataSource
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.testing.SampleForecasts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WeatherRepositoryCacheTest {

    private val now = Instant.parse("2026-10-10T07:00:00Z")
    private val earlier = now.minusSeconds(6 * 3600)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val forecast = SampleForecasts.forecast()

    private class MemoryCache(var entry: CachedForecast? = null, val failing: Boolean = false) : WeatherCache {
        override suspend fun load(coordinates: Coordinates): CachedForecast? {
            if (failing) throw IOException("unreadable")
            return entry
        }

        override suspend fun save(forecast: WeatherForecast, fetchedAt: Instant) {
            if (failing) throw IOException("disk full")
            entry = CachedForecast(forecast, fetchedAt)
        }
    }

    private class Remote(private val block: () -> WeatherForecast) : WeatherRemoteDataSource {
        override suspend fun fetchForecast(coordinates: Coordinates) = block()
    }

    @Test
    fun `successful download is saved for offline use`() = runTest {
        val cache = MemoryCache()

        DefaultWeatherRepository(Remote { forecast }, cache, clock).getForecast(SampleForecasts.london)

        assertEquals(CachedForecast(forecast, now), cache.entry)
    }

    @Test
    fun `failed download falls back to the cached forecast`() = runTest {
        val cache = MemoryCache(CachedForecast(forecast, earlier))

        val result = DefaultWeatherRepository(Remote { throw UnknownHostException() }, cache, clock)
            .getForecast(SampleForecasts.london)

        assertEquals(WeatherResult.Stale(forecast, earlier, WeatherError.NoConnection), result)
    }

    @Test
    fun `failed download without a cache is a failure`() = runTest {
        val result = DefaultWeatherRepository(Remote { throw UnknownHostException() }, MemoryCache(), clock)
            .getForecast(SampleForecasts.london)

        assertEquals(WeatherResult.Failure(WeatherError.NoConnection), result)
    }

    @Test
    fun `cache problems never break loading`() = runTest {
        val repository = DefaultWeatherRepository(Remote { forecast }, MemoryCache(failing = true), clock)

        assertEquals(WeatherResult.Success(forecast, now), repository.getForecast(SampleForecasts.london))
        assertNull(repository.cachedForecast(SampleForecasts.london))
    }

    @Test
    fun `cached forecast is available without the network`() = runTest {
        val repository = DefaultWeatherRepository(
            Remote { error("network must not be used") },
            MemoryCache(CachedForecast(forecast, earlier)),
            clock,
        )

        assertEquals(WeatherResult.Success(forecast, earlier), repository.cachedForecast(SampleForecasts.london))
    }

    @Test
    fun `listeners hear about saved forecasts only`() = runTest {
        var saved = 0
        DefaultWeatherRepository(Remote { forecast }, MemoryCache(), clock) { saved++ }.getForecast(SampleForecasts.london)
        assertEquals(1, saved)

        DefaultWeatherRepository(Remote { throw UnknownHostException() }, MemoryCache(), clock) { saved++ }
            .getForecast(SampleForecasts.london)
        DefaultWeatherRepository(Remote { forecast }, MemoryCache(failing = true), clock) { saved++ }
            .getForecast(SampleForecasts.london)
        assertEquals(1, saved)
    }
}
