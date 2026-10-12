package com.ah0890.dailyweather.data.remote.openmeteo

import com.ah0890.dailyweather.data.remote.MalformedWeatherDataException
import com.ah0890.dailyweather.domain.model.Coordinates
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class OpenMeteoDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: OpenMeteoDataSource

    private val sampleJson = javaClass.getResource("/openmeteo/forecast_sample.json")!!.readText()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dataSource = OpenMeteoDataSource.create(baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `requests forecast with rounded coordinates and expected fields`() = runTest {
        server.enqueue(MockResponse.Builder().body(sampleJson).build())

        dataSource.fetchForecast(Coordinates(51.507351, -0.127758))

        val url = server.takeRequest().url
        assertEquals("/v1/forecast", url.encodedPath)
        assertEquals("51.51", url.queryParameter("latitude"))
        assertEquals("-0.13", url.queryParameter("longitude"))
        assertEquals("auto", url.queryParameter("timezone"))
        assertEquals("7", url.queryParameter("forecast_days"))
        assertTrue(url.queryParameter("hourly")!!.contains("precipitation_probability"))
        assertTrue(url.queryParameter("daily")!!.contains("precipitation_probability_max"))
    }

    @Test
    fun `successful response is mapped to domain forecast`() = runTest {
        server.enqueue(MockResponse.Builder().body(sampleJson).build())

        val forecast = dataSource.fetchForecast(Coordinates(51.507351, -0.127758))

        assertEquals(Coordinates(51.51, -0.13), forecast.coordinates)
        assertEquals(4, forecast.hourly.size)
        assertEquals(2, forecast.daily.size)
    }

    @Test
    fun `invalid json surfaces as malformed data`() = runTest {
        server.enqueue(MockResponse.Builder().body("{ this is not json").build())

        try {
            dataSource.fetchForecast(Coordinates(51.5, -0.12))
            fail("Expected MalformedWeatherDataException")
        } catch (_: MalformedWeatherDataException) {
        }
    }

    @Test
    fun `rate limiting surfaces as http error`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).body("""{"error":true,"reason":"Too many requests"}""").build())

        try {
            dataSource.fetchForecast(Coordinates(51.5, -0.12))
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(429, e.code())
        }
    }
}
