package com.ah0890.dailyweather.data.remote.openmeteo

import com.ah0890.dailyweather.data.remote.MalformedWeatherDataException
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.WeatherCondition
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class OpenMeteoMapperTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val coordinates = Coordinates(51.5, -0.12)

    private fun sampleResponse(): ForecastResponseDto {
        val text = javaClass.getResource("/openmeteo/forecast_sample.json")!!.readText()
        return json.decodeFromString(text)
    }

    @Test
    fun `sample response maps current conditions`() {
        val forecast = sampleResponse().toDomain(coordinates)

        with(forecast.current) {
            assertEquals(LocalDateTime.of(2026, 10, 10, 8, 15), time)
            assertEquals(WeatherCondition.PartlyCloudy, condition)
            assertEquals(12.4, temperatureC, 0.0)
            assertEquals(10.9, feelsLikeC, 0.0)
            assertEquals(81, humidityPercent)
            assertEquals(14.8, windSpeedKmh!!, 0.0)
            assertEquals(230, windDirectionDegrees)
            assertTrue(isDay)
            assertEquals(1.2, uvIndex!!, 0.0)
            assertEquals(24140.0, visibilityMeters!!, 0.0)
        }
        assertEquals(ZoneId.of("Europe/London"), forecast.timeZone)
        assertEquals(coordinates, forecast.coordinates)
    }

    @Test
    fun `sample response maps hourly series in order`() {
        val hourly = sampleResponse().toDomain(coordinates).hourly

        assertEquals(4, hourly.size)
        assertEquals(LocalDateTime.of(2026, 10, 10, 10, 0), hourly[2].time)
        assertEquals(55, hourly[2].precipitationProbability)
        assertEquals(0.4, hourly[2].precipitationMm!!, 0.0)
        assertEquals(WeatherCondition.Rain, hourly[2].condition)
        assertEquals(listOf(8, 12, 55, 78), hourly.map { it.precipitationProbability })
    }

    @Test
    fun `sample response maps daily series`() {
        val daily = sampleResponse().toDomain(coordinates).daily

        assertEquals(2, daily.size)
        with(daily[0]) {
            assertEquals(LocalDate.of(2026, 10, 10), date)
            assertEquals(WeatherCondition.Rain, condition)
            assertEquals(16.3, maxTemperatureC, 0.0)
            assertEquals(9.8, minTemperatureC, 0.0)
            assertEquals(78, precipitationProbabilityMax)
            assertEquals(6.2, precipitationSumMm!!, 0.0)
            assertEquals(LocalDateTime.of(2026, 10, 10, 7, 15), sunrise)
            assertEquals(LocalDateTime.of(2026, 10, 10, 18, 22), sunset)
        }
    }

    @Test
    fun `hours with missing time or temperature are skipped, not fatal`() {
        val dto = sampleResponse().let {
            it.copy(
                hourly = it.hourly!!.copy(
                    time = listOf("2026-10-10T08:00", "not-a-time", "2026-10-10T10:00", "2026-10-10T11:00"),
                    temperature = listOf(12.1, 13.0, null, 15.1),
                ),
            )
        }

        val hourly = dto.toDomain(coordinates).hourly

        assertEquals(listOf(8, 11), hourly.map { it.time.hour })
    }

    @Test
    fun `optional series shorter than time list produce nulls`() {
        val dto = sampleResponse().let {
            it.copy(hourly = it.hourly!!.copy(precipitationProbability = listOf(8), precipitation = null))
        }

        val hourly = dto.toDomain(coordinates).hourly

        assertEquals(8, hourly[0].precipitationProbability)
        assertNull(hourly[3].precipitationProbability)
        assertNull(hourly[3].precipitationMm)
    }

    @Test
    fun `out of range values are clamped`() {
        val dto = sampleResponse().let {
            it.copy(
                current = it.current!!.copy(relativeHumidity = 140),
                hourly = it.hourly!!.copy(precipitationProbability = listOf(-5, 12, 55, 130)),
            )
        }

        val forecast = dto.toDomain(coordinates)

        assertEquals(100, forecast.current.humidityPercent)
        assertEquals(0, forecast.hourly[0].precipitationProbability)
        assertEquals(100, forecast.hourly[3].precipitationProbability)
    }

    @Test
    fun `missing apparent temperature falls back to actual temperature`() {
        val dto = sampleResponse().let { it.copy(current = it.current!!.copy(apparentTemperature = null)) }

        assertEquals(12.4, dto.toDomain(coordinates).current.feelsLikeC, 0.0)
    }

    @Test
    fun `unknown timezone falls back to utc offset`() {
        val dto = sampleResponse().copy(timezone = "Not/AZone")

        assertEquals(ZoneOffset.ofHours(1), dto.toDomain(coordinates).timeZone)
    }

    @Test(expected = MalformedWeatherDataException::class)
    fun `missing current block is rejected`() {
        sampleResponse().copy(current = null).toDomain(coordinates)
    }

    @Test(expected = MalformedWeatherDataException::class)
    fun `current block without temperature is rejected`() {
        sampleResponse().let { it.copy(current = it.current!!.copy(temperature = null)) }.toDomain(coordinates)
    }

    @Test(expected = MalformedWeatherDataException::class)
    fun `empty hourly series is rejected`() {
        sampleResponse().copy(hourly = HourlyDto(time = emptyList())).toDomain(coordinates)
    }

    @Test(expected = MalformedWeatherDataException::class)
    fun `missing daily block is rejected`() {
        sampleResponse().copy(daily = null).toDomain(coordinates)
    }

    @Test
    fun `wmo codes map to conditions`() {
        val expected = mapOf(
            0 to WeatherCondition.Clear,
            1 to WeatherCondition.MainlyClear,
            2 to WeatherCondition.PartlyCloudy,
            3 to WeatherCondition.Overcast,
            45 to WeatherCondition.Fog,
            48 to WeatherCondition.Fog,
            53 to WeatherCondition.Drizzle,
            57 to WeatherCondition.FreezingDrizzle,
            65 to WeatherCondition.Rain,
            66 to WeatherCondition.FreezingRain,
            77 to WeatherCondition.Snow,
            81 to WeatherCondition.RainShowers,
            86 to WeatherCondition.SnowShowers,
            95 to WeatherCondition.Thunderstorm,
            99 to WeatherCondition.Thunderstorm,
        )
        expected.forEach { (code, condition) -> assertEquals("code $code", condition, wmoCodeToCondition(code)) }
    }

    @Test
    fun `unrecognised or missing wmo codes map to unknown`() {
        assertEquals(WeatherCondition.Unknown, wmoCodeToCondition(null))
        assertEquals(WeatherCondition.Unknown, wmoCodeToCondition(42))
        assertEquals(WeatherCondition.Unknown, wmoCodeToCondition(-1))
    }

    @Test
    fun `precipitation flag matches condition`() {
        assertTrue(WeatherCondition.Rain.isPrecipitation)
        assertTrue(WeatherCondition.Thunderstorm.isPrecipitation)
        assertFalse(WeatherCondition.Overcast.isPrecipitation)
        assertFalse(WeatherCondition.Unknown.isPrecipitation)
    }
}
