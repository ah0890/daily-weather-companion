package com.ah0890.dailyweather.data.remote.openmeteo

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.PlaceSuggestion
import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.repository.PlaceSearchRepository.Result
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OpenMeteoPlaceSearchTest {

    private lateinit var server: MockWebServer
    private lateinit var search: OpenMeteoPlaceSearch

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        search = OpenMeteoPlaceSearch.create(baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `results are mapped with region and country`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"results":[
                    {"name":"London","latitude":51.50853,"longitude":-0.12574,"country":"United Kingdom","admin1":"England"},
                    {"name":"London","latitude":42.98339,"longitude":-81.23304,"country":"Canada","admin1":"Ontario"}
                ]}""",
            ).build(),
        )

        val result = search.search("  London ") as Result.Success

        assertEquals(
            listOf(
                PlaceSuggestion("London", "England, United Kingdom", Coordinates(51.50853, -0.12574)),
                PlaceSuggestion("London", "Ontario, Canada", Coordinates(42.98339, -81.23304)),
            ),
            result.suggestions,
        )
        val url = server.takeRequest().url
        assertEquals("/v1/search", url.encodedPath)
        assertEquals("London", url.queryParameter("name"))
    }

    @Test
    fun `no matches is an empty list`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"generationtime_ms":0.3}""").build())

        assertEquals(Result.Success(emptyList()), search.search("Xyzzy"))
    }

    @Test
    fun `malformed entries are skipped`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"results":[
                    {"name":"","latitude":1.0,"longitude":1.0},
                    {"name":"Nowhere","latitude":120.0,"longitude":1.0},
                    {"name":"Paris","latitude":48.85341,"longitude":2.3488}
                ]}""",
            ).build(),
        )

        val result = search.search("Paris") as Result.Success

        assertEquals(listOf("Paris"), result.suggestions.map { it.name })
        assertEquals(null, result.suggestions.single().region)
    }

    @Test
    fun `too short a query doesn't call the service`() = runTest {
        assertEquals(Result.Success(emptyList()), search.search(" a "))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `server errors and bad data are reported, not thrown`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        assertEquals(Result.Failure(WeatherError.ServiceUnavailable), search.search("Paris"))

        server.enqueue(MockResponse.Builder().body("not json").build())
        assertEquals(Result.Failure(WeatherError.InvalidData), search.search("Paris"))
    }
}
