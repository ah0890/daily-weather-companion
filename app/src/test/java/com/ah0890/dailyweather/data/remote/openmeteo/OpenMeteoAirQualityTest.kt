package com.ah0890.dailyweather.data.remote.openmeteo

import com.ah0890.dailyweather.domain.model.AqiCategory
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.Pollutant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class OpenMeteoAirQualityTest {

    private val sample = """
        {"current":{"time":"2026-10-12T19:00","us_aqi":72,"pm2_5":21.4,"pm10":30.1,"ozone":24.0,
          "nitrogen_dioxide":31.8,"sulphur_dioxide":1.6,"carbon_monoxide":182.0,
          "us_aqi_pm2_5":72,"us_aqi_pm10":28,"us_aqi_ozone":18,"us_aqi_nitrogen_dioxide":16,
          "us_aqi_sulphur_dioxide":1,"us_aqi_carbon_monoxide":2},
         "hourly":{"time":["2026-10-12T18:00","2026-10-12T19:00","2026-10-12T20:00","2026-10-13T21:00"],
                   "us_aqi":[150,72,120,190]}}
    """.trimIndent()

    private lateinit var server: MockWebServer

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
        override fun instant() = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-12T18:00:00Z"))
    private val repository by lazy { OpenMeteoAirQualityRepository.create(server.url("/").toString(), clock) }
    private val london = Coordinates(51.507, -0.127)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun json(body: String) = MockResponse.Builder().body(body).build()

    @Test
    fun `current air quality is mapped with the main pollutant and the coming peak`() = runTest {
        server.enqueue(json(sample))

        val air = repository.airQuality(london)!!

        assertEquals(72, air.usAqi)
        assertEquals(AqiCategory.Moderate, air.category)
        assertEquals(Pollutant.Pm25, air.mainPollutant)
        assertEquals(21.4, air.readings.first { it.pollutant == Pollutant.Pm25 }.concentration!!, 0.0)
        // 18:00 is past and 21:00 the next day is beyond 24 hours.
        assertEquals(120, air.peakUsAqi)
        assertEquals(LocalDateTime.of(2026, 10, 12, 20, 0), air.peakTime)

        val url = server.takeRequest().url
        assertEquals("/v1/air-quality", url.encodedPath)
        assertEquals("51.51", url.queryParameter("latitude"))
        assertEquals("-0.13", url.queryParameter("longitude"))
    }

    @Test
    fun `categories follow the US EPA bands`() {
        assertEquals(AqiCategory.Good, AqiCategory.of(0))
        assertEquals(AqiCategory.Good, AqiCategory.of(50))
        assertEquals(AqiCategory.Moderate, AqiCategory.of(51))
        assertEquals(AqiCategory.Moderate, AqiCategory.of(100))
        assertEquals(AqiCategory.UnhealthyForSensitiveGroups, AqiCategory.of(101))
        assertEquals(AqiCategory.Unhealthy, AqiCategory.of(151))
        assertEquals(AqiCategory.VeryUnhealthy, AqiCategory.of(201))
        assertEquals(AqiCategory.Hazardous, AqiCategory.of(301))
    }

    @Test
    fun `missing aqi means unavailable, not zero`() = runTest {
        server.enqueue(json("""{"current":{"time":"2026-10-12T19:00","pm2_5":12.0}}"""))

        assertNull(repository.airQuality(london))
    }

    @Test
    fun `malformed and negative values are dropped`() = runTest {
        server.enqueue(json("""{"current":{"time":"2026-10-12T19:00","us_aqi":40,"pm2_5":-3.0,"us_aqi_pm2_5":-1,"ozone":50.0}}"""))

        val air = repository.airQuality(london)!!

        assertEquals(listOf(Pollutant.Ozone), air.readings.map { it.pollutant })
        assertNull(air.mainPollutant)
        assertNull(air.peakUsAqi)

        server.enqueue(json("not json"))
        clock.now = clock.now.plusSeconds(3600)
        assertEquals("a broken response falls back to the recent copy", air, repository.airQuality(london))
    }

    @Test
    fun `a recent copy is reused without downloading`() = runTest {
        server.enqueue(json(sample))
        repository.airQuality(london)
        clock.now = clock.now.plusSeconds(20 * 60)

        assertNotNull(repository.airQuality(london))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `offline with no recent copy is unavailable`() = runTest {
        server.enqueue(json(sample))
        repository.airQuality(london)
        server.close()
        clock.now = clock.now.plusSeconds(4 * 3600)

        assertNull(repository.airQuality(london))
    }
}
