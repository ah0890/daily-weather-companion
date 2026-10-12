package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.testing.SampleForecasts.hour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardAnalysisTest {

    private fun detect(vararg hours: HourlyForecast) = HazardAnalysis.detect(hours.toList())

    @Test
    fun `calm dry mild hours have no hazards`() {
        assertEquals(emptySet<WeatherHazard>(), detect(hour(), hour(visibilityM = 20_000.0, uv = 2.0)))
    }

    @Test
    fun `no hours means no hazards`() {
        assertTrue(HazardAnalysis.detect(emptyList()).isEmpty())
    }

    @Test
    fun `rain chance follows the umbrella thresholds`() {
        assertEquals(emptySet<WeatherHazard>(), detect(hour(rainChance = 29)))
        assertEquals(setOf(WeatherHazard.RainPossible), detect(hour(rainChance = 30)))
        assertEquals(setOf(WeatherHazard.RainPossible), detect(hour(rainChance = 59)))
        assertEquals(setOf(WeatherHazard.RainLikely), detect(hour(rainChance = 60)))
    }

    @Test
    fun `heavy rain starts at 4 mm in an hour`() {
        assertTrue(WeatherHazard.HeavyRain !in detect(hour(rainMm = 3.9)))
        assertTrue(WeatherHazard.HeavyRain in detect(hour(rainMm = 4.0)))
    }

    @Test
    fun `thunderstorm and wintry conditions come from the weather code`() {
        assertTrue(WeatherHazard.Thunderstorm in detect(hour(condition = WeatherCondition.Thunderstorm)))
        assertTrue(WeatherHazard.Wintry in detect(hour(condition = WeatherCondition.FreezingRain)))
        assertTrue(WeatherHazard.Wintry in detect(hour(condition = WeatherCondition.Snow)))
        assertTrue(WeatherHazard.Wintry !in detect(hour(condition = WeatherCondition.Rain)))
    }

    @Test
    fun `wind thresholds use sustained wind or gusts, stronger level only`() {
        assertTrue(detect(hour(windKmh = 38.9, gustsKmh = 59.9)).isEmpty())
        assertEquals(setOf(WeatherHazard.StrongWind), detect(hour(windKmh = 39.0)))
        assertEquals(setOf(WeatherHazard.StrongWind), detect(hour(windKmh = 20.0, gustsKmh = 60.0)))
        assertEquals(setOf(WeatherHazard.Gale), detect(hour(windKmh = 62.0)))
        assertEquals(setOf(WeatherHazard.Gale), detect(hour(windKmh = 30.0, gustsKmh = 85.0)))
    }

    @Test
    fun `visibility thresholds`() {
        assertTrue(detect(hour(visibilityM = 1_000.0)).isEmpty())
        assertEquals(setOf(WeatherHazard.PoorVisibility), detect(hour(visibilityM = 999.0)))
        assertEquals(setOf(WeatherHazard.PoorVisibility), detect(hour(visibilityM = 200.0)))
        assertEquals(setOf(WeatherHazard.DenseFog), detect(hour(visibilityM = 199.0)))
        assertEquals("fog code without visibility data", setOf(WeatherHazard.PoorVisibility), detect(hour(condition = WeatherCondition.Fog)))
    }

    @Test
    fun `heat uses the feels-like temperature, falling back to the air temperature`() {
        assertTrue(detect(hour(temperatureC = 33.0, feelsLikeC = 34.9)).isEmpty())
        assertEquals(setOf(WeatherHazard.Heat), detect(hour(temperatureC = 33.0, feelsLikeC = 35.0)))
        assertEquals(setOf(WeatherHazard.ExtremeHeat), detect(hour(temperatureC = 38.0, feelsLikeC = 41.0)))
        assertEquals(setOf(WeatherHazard.Heat), detect(hour(temperatureC = 36.0, feelsLikeC = null)))
    }

    @Test
    fun `high uv counts only in daylight`() {
        assertTrue(detect(hour(uv = 5.9)).isEmpty())
        assertEquals(setOf(WeatherHazard.HighUv), detect(hour(uv = 6.0)))
        assertTrue(detect(hour(uv = 7.0, isDay = false)).isEmpty())
    }

    @Test
    fun `missing data never triggers a hazard`() {
        val unknown = hour(rainChance = null, rainMm = null, windKmh = null, gustsKmh = null, visibilityM = null, uv = null)
        assertTrue(detect(unknown).isEmpty())
    }

    @Test
    fun `several hazards are reported together`() {
        val stormy = hour(condition = WeatherCondition.Thunderstorm, rainChance = 80, rainMm = 6.0, gustsKmh = 70.0, visibilityM = 800.0)

        assertEquals(
            setOf(
                WeatherHazard.Thunderstorm,
                WeatherHazard.HeavyRain,
                WeatherHazard.RainLikely,
                WeatherHazard.StrongWind,
                WeatherHazard.PoorVisibility,
            ),
            detect(stormy),
        )
    }
}
