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

    private val file by lazy { File(tempFolder.root, "weather_cache.json") }
    private val fetchedAt = Instant.parse("2026-10-10T06:45:00Z")
    private val forecast = SampleForecasts.forecast()

    @Test
    fun `missing file means no cache`() = runTest {
        assertNull(FileWeatherCache(file).load(SampleForecasts.london))
    }

    @Test
    fun `saved forecast is read back unchanged`() = runTest {
        FileWeatherCache(file).save(forecast, fetchedAt)

        assertEquals(CachedForecast(forecast, fetchedAt), FileWeatherCache(file).load(SampleForecasts.london))
    }

    @Test
    fun `saving again replaces the previous forecast`() = runTest {
        val cache = FileWeatherCache(file)
        cache.save(forecast, fetchedAt)
        cache.save(forecast, fetchedAt.plusSeconds(3600))

        assertEquals(fetchedAt.plusSeconds(3600), cache.load(SampleForecasts.london)!!.fetchedAt)
        assertFalse("temporary file must not linger", File(file.parentFile, file.name + ".tmp").exists())
    }

    @Test
    fun `a forecast for another place is not returned`() = runTest {
        FileWeatherCache(file).save(forecast, fetchedAt)

        assertNull(FileWeatherCache(file).load(Coordinates(48.86, 2.35)))
    }

    @Test
    fun `nearby coordinates in the same 1 km cell match`() = runTest {
        FileWeatherCache(file).save(forecast, fetchedAt)

        assertEquals(fetchedAt, FileWeatherCache(file).load(Coordinates(51.5149, -0.1252))!!.fetchedAt)
    }

    @Test
    fun `corrupted file is treated as empty`() = runTest {
        file.writeText("{ not valid json")

        assertNull(FileWeatherCache(file).load(SampleForecasts.london))
    }

    @Test
    fun `file from a different format version is ignored`() = runTest {
        FileWeatherCache(file).save(forecast, fetchedAt)
        file.writeText(file.readText().replace("\"version\":1", "\"version\":99"))

        assertNull(FileWeatherCache(file).load(SampleForecasts.london))
    }

    @Test
    fun `invalid stored values are treated as empty`() = runTest {
        FileWeatherCache(file).save(forecast, fetchedAt)
        file.writeText(file.readText().replace("Europe/London", "Not/AZone"))

        assertNull(FileWeatherCache(file).load(SampleForecasts.london))
    }
}
