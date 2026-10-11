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
}
