package com.ah0890.dailyweather.wear

import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.CurrentWeather
import com.ah0890.dailyweather.domain.model.DailyForecast
import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.domain.usecase.RainSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WatchSummaryTest {

    // A Monday, so the default Monday–Friday commute applies.
    private val monday = LocalDate.of(2026, 10, 12)

    private fun forecast(rain: Map<Int, Int> = emptyMap(), stormHour: Int? = null) = WeatherForecast(
        coordinates = Coordinates(51.51, -0.13),
        timeZone = ZoneId.of("Europe/London"),
        current = CurrentWeather(
            time = monday.atTime(7, 0), condition = WeatherCondition.Overcast, temperatureC = 24.0, feelsLikeC = 24.0,
            humidityPercent = null, windSpeedKmh = 10.0, windDirectionDegrees = null, precipitationMm = 0.0,
            isDay = true, uvIndex = null, visibilityMeters = null,
        ),
        hourly = List(30) { h ->
            HourlyForecast(
                time = monday.atStartOfDay().plusHours(h.toLong()),
                condition = if (h == stormHour) WeatherCondition.Thunderstorm else WeatherCondition.Overcast,
                temperatureC = 20.0,
                precipitationProbability = rain[h] ?: 0,
                precipitationMm = 0.0,
                windSpeedKmh = 10.0,
                isDay = h in 7..18,
            )
        },
        daily = listOf(DailyForecast(monday, WeatherCondition.Overcast, 25.0, 15.0, null, null, null, null, null, null)),
    )

    private val now: LocalDateTime = monday.atTime(7, 15)

    @Test
    fun `dry day - temperature, low rain and a fine morning trip`() {
        val summary = WatchSummary.from(forecast(), now)

        assertEquals(24.0, summary.temperatureC, 0.0)
        assertEquals(0, summary.rainChance)
        assertEquals(RainSummary.Low, summary.rain)
        assertEquals(CommuteLegType.Morning, summary.nextTrip!!.leg.type)
        assertEquals(TravelRating.Favorable, summary.nextTrip!!.rating)
        assertNull(summary.alert)
    }

    @Test
    fun `rain on the way home - timing, umbrella and the return trip`() {
        val summary = WatchSummary.from(forecast(rain = mapOf(16 to 70, 17 to 72)), now)

        assertEquals(72, summary.rainChance)
        assertEquals(RainSummary.Between(monday.atTime(16, 0), monday.atTime(18, 0)), summary.rain)
        assertEquals(UmbrellaAdvice.Recommended, summary.umbrella)
        assertEquals(CommuteLegType.Evening, summary.nextTrip!!.leg.type)
    }

    @Test
    fun `a severe alert is passed on`() {
        assertEquals(WeatherHazard.Thunderstorm, WatchSummary.from(forecast(stormHour = 14), now).alert!!.hazard)
    }
}
