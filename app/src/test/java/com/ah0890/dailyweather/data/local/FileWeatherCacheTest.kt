package com.ah0890.dailyweather.data.local

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.testing.SampleForecasts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class FileWeatherCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dir by lazy { tempFolder.root }
    private val file by lazy { FileWeatherCache(dir).fileFor(SampleForecasts.london) }
    private val fetchedAt = Instant.parse("2026-10-10T06:45:00Z")
    private val forecast = SampleForecasts.forecast()

    @Test
    fun `missing file means no cache`() = runTest {
        assertNull(FileWeatherCache(dir).load(SampleForecasts.london))
    }

    @Test
    fun `saved forecast is read back unchanged`() = runTest {
        FileWeatherCache(dir).save(forecast, fetchedAt)

        assertEquals(CachedForecast(forecast, fetchedAt), FileWeatherCache(dir).load(SampleForecasts.london))
    }

    @Test
    fun `gusts, visibility and uv per hour are kept`() = runTest {
        val detailed = forecast.copy(
            hourly = forecast.hourly.map { it.copy(windGustsKmh = 48.0, visibilityMeters = 900.0, uvIndex = 6.5) },
        )
        FileWeatherCache(dir).save(detailed, fetchedAt)

        assertEquals(detailed, FileWeatherCache(dir).load(SampleForecasts.london)!!.forecast)
    }

    @Test
    fun `saving again replaces the previous forecast`() = runTest {
        val cache = FileWeatherCache(dir)
        cache.save(forecast, fetchedAt)
        cache.save(forecast, fetchedAt.plusSeconds(3600))

        assertEquals(fetchedAt.plusSeconds(3600), cache.load(SampleForecasts.london)!!.fetchedAt)
        assertFalse("temporary file must not linger", File(file.parentFile, file.name + ".tmp").exists())
    }

    @Test
    fun `a forecast for another place is not returned`() = runTest {
        FileWeatherCache(dir).save(forecast, fetchedAt)

        assertNull(FileWeatherCache(dir).load(Coordinates(48.86, 2.35)))
    }

    @Test
    fun `nearby coordinates in the same 1 km cell match`() = runTest {
        FileWeatherCache(dir).save(forecast, fetchedAt)

        assertEquals(fetchedAt, FileWeatherCache(dir).load(Coordinates(51.5149, -0.1252))!!.fetchedAt)
    }

    @Test
    fun `corrupted file is treated as empty`() = runTest {
        file.writeText("{ not valid json")

        assertNull(FileWeatherCache(dir).load(SampleForecasts.london))
    }

    @Test
    fun `file from a different format version is ignored`() = runTest {
        FileWeatherCache(dir).save(forecast, fetchedAt)
        file.writeText(file.readText().replace("\"version\":1", "\"version\":99"))

        assertNull(FileWeatherCache(dir).load(SampleForecasts.london))
    }

    @Test
    fun `invalid stored values are treated as empty`() = runTest {
        FileWeatherCache(dir).save(forecast, fetchedAt)
        file.writeText(file.readText().replace("Europe/London", "Not/AZone"))

        assertNull(FileWeatherCache(dir).load(SampleForecasts.london))
    }

    @Test
    fun `forecasts for several places are kept side by side`() = runTest {
        val cache = FileWeatherCache(dir)
        val paris = SampleForecasts.forecast(coordinates = Coordinates(48.86, 2.35))
        cache.save(forecast, fetchedAt)
        cache.save(paris, fetchedAt.plusSeconds(60))

        assertEquals(fetchedAt, cache.load(SampleForecasts.london)!!.fetchedAt)
        assertEquals(paris, cache.load(Coordinates(48.86, 2.35))!!.forecast)
    }

    @Test
    fun `only the most recent places are kept`() = runTest {
        val cache = FileWeatherCache(dir, maxEntries = 2)
        val (a, b, c) = listOf(Coordinates(10.0, 10.0), Coordinates(20.0, 20.0), Coordinates(30.0, 30.0))
        cache.save(SampleForecasts.forecast(coordinates = a), fetchedAt)
        cache.fileFor(a).setLastModified(1_000_000)
        cache.save(SampleForecasts.forecast(coordinates = b), fetchedAt)
        cache.fileFor(b).setLastModified(2_000_000)

        cache.save(SampleForecasts.forecast(coordinates = c), fetchedAt)

        assertNull(cache.load(a))
        assertEquals(fetchedAt, cache.load(b)!!.fetchedAt)
        assertEquals(fetchedAt, cache.load(c)!!.fetchedAt)
    }

    @Test
    fun `the old single-file cache is removed`() = runTest {
        val legacy = File(dir, "weather_cache.json").apply { writeText("{}") }

        FileWeatherCache(File(dir, "cache"), legacyFile = legacy).save(forecast, fetchedAt)

        assertFalse(legacy.exists())
    }
}
