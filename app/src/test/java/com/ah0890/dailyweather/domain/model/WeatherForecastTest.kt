package com.ah0890.dailyweather.domain.model

import com.ah0890.dailyweather.testing.SampleForecasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class WeatherForecastTest {

    @Test
    fun `today is the daily entry matching the current local date`() {
        val base = SampleForecasts.forecast(start = LocalDateTime.of(2026, 10, 10, 8, 0))
        val tomorrow = base.daily[0].copy(date = base.daily[0].date.plusDays(1), maxTemperatureC = 30.0)
        val forecast = base.copy(daily = listOf(tomorrow, base.daily[0]))

        assertEquals(base.daily[0], forecast.today)
    }

    @Test
    fun `today falls back to the first day when dates don't match`() {
        val base = SampleForecasts.forecast()
        val shifted = base.daily.map { it.copy(date = it.date.plusDays(5)) }

        assertEquals(shifted[0], base.copy(daily = shifted).today)
    }

    @Test
    fun `today is null without daily data`() {
        assertNull(SampleForecasts.forecast().copy(daily = emptyList()).today)
    }

    @Test
    fun `upcoming hours start with the current hour`() {
        val start = LocalDateTime.of(2026, 10, 10, 8, 0)
        val forecast = SampleForecasts.forecast(start = start, hourlyRainChances = listOf(1, 2, 3, 4, 5))
            .let { it.copy(current = it.current.copy(time = start.plusHours(2).plusMinutes(15))) }

        val hours = forecast.upcomingHours()

        assertEquals(listOf(10, 11, 12), hours.map { it.time.hour })
    }

    @Test
    fun `upcoming hours are capped at the requested count`() {
        val forecast = SampleForecasts.forecast(hourlyRainChances = List(48) { 10 })

        assertEquals(24, forecast.upcomingHours().size)
        assertEquals(6, forecast.upcomingHours(count = 6).size)
    }

    @Test
    fun `no upcoming hours when the hourly data is all in the past`() {
        val forecast = SampleForecasts.forecast(hourlyRainChances = listOf(10, 20))
            .let { it.copy(current = it.current.copy(time = it.current.time.plusDays(1))) }

        assertEquals(emptyList<HourlyForecast>(), forecast.upcomingHours())
    }

    @Test
    fun `hours on a date only include that local day`() {
        val start = LocalDateTime.of(2026, 10, 10, 22, 0)
        val forecast = SampleForecasts.forecast(start = start, hourlyRainChances = List(5) { 10 })

        assertEquals(listOf(22, 23), forecast.hoursOn(start.toLocalDate()).map { it.time.hour })
        assertEquals(listOf(0, 1, 2), forecast.hoursOn(start.toLocalDate().plusDays(1)).map { it.time.hour })
    }

    @Test
    fun `a fresh forecast is not changed when advanced`() {
        val forecast = SampleForecasts.forecast()

        assertEquals(forecast, forecast.advancedTo(forecast.current.time.plusMinutes(40)))
    }

    @Test
    fun `an old forecast takes current conditions from the hourly entry for now`() {
        val start = LocalDateTime.of(2026, 10, 10, 8, 0)
        val forecast = SampleForecasts.forecast(start = start, hourlyRainChances = listOf(10, 20, 60, 80))
            .let { f -> f.copy(hourly = f.hourly.map { it.copy(feelsLikeC = it.temperatureC - 2) }) }
        val now = start.plusHours(2).plusMinutes(20)

        val advanced = forecast.advancedTo(now)

        assertEquals(now, advanced.current.time)
        assertEquals(WeatherCondition.Rain, advanced.current.condition)
        assertEquals(14.0, advanced.current.temperatureC, 0.0)
        assertEquals(12.0, advanced.current.feelsLikeC, 0.0)
        assertNull("not in hourly data", advanced.current.humidityPercent)
        assertEquals(listOf(10, 11), advanced.upcomingHours().map { it.time.hour })
    }

    @Test
    fun `beyond the hourly data the forecast is left as is`() {
        val forecast = SampleForecasts.forecast()

        assertEquals(forecast, forecast.advancedTo(forecast.current.time.plusDays(30)))
    }
}
