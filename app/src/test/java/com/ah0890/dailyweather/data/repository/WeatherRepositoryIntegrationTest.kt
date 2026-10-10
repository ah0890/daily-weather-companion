package com.ah0890.dailyweather.data.repository

import com.ah0890.dailyweather.data.remote.openmeteo.OpenMeteoDataSource
import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.testing.SampleForecasts
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Runs the real Retrofit/OkHttp stack against a local server to check real failure types. */
class WeatherRepositoryIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultWeatherRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = DefaultWeatherRepository(OpenMeteoDataSource.create(baseUrl = server.url("/").toString()))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `valid response loads successfully`() = runTest {
        val body = javaClass.getResource("/openmeteo/forecast_sample.json")!!.readText()
        server.enqueue(MockResponse.Builder().body(body).build())

        assertTrue(repository.getForecast(SampleForecasts.london) is WeatherResult.Success)
    }

    @Test
    fun `server error is reported as service unavailable`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        assertEquals(WeatherResult.Failure(WeatherError.ServiceUnavailable), repository.getForecast(SampleForecasts.london))
    }

    @Test
    fun `garbage body is reported as invalid data`() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>maintenance</html>").build())

        assertEquals(WeatherResult.Failure(WeatherError.InvalidData), repository.getForecast(SampleForecasts.london))
    }

    @Test
    fun `json without required blocks is reported as invalid data`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"timezone":"UTC"}""").build())

        assertEquals(WeatherResult.Failure(WeatherError.InvalidData), repository.getForecast(SampleForecasts.london))
    }

    @Test
    fun `unreachable server is reported as no connection`() = runTest {
        server.close()

        assertEquals(WeatherResult.Failure(WeatherError.NoConnection), repository.getForecast(SampleForecasts.london))
    }
}
