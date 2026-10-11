package com.ah0890.dailyweather.presentation.forecast

import com.ah0890.dailyweather.testing.SampleForecasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ForecastFormattingTest {

    private val today = LocalDate.of(2026, 10, 10) // a Saturday

    @Test
    fun `today and tomorrow get relative names`() {
        assertEquals(DayLabel.Today, DayLabel.of(today, today))
        assertEquals(DayLabel.Tomorrow, DayLabel.of(today.plusDays(1), today))
    }

    @Test
    fun `later days use the weekday`() {
        assertEquals(DayLabel.Weekday(DayOfWeek.MONDAY), DayLabel.of(today.plusDays(2), today))
        assertEquals(DayLabel.Weekday(DayOfWeek.FRIDAY), DayLabel.of(today.plusDays(6), today))
    }

    @Test
    fun `temperature span covers the whole week`() {
        val base = SampleForecasts.forecast().daily[0]
        val days = listOf(
            base.copy(minTemperatureC = 9.0, maxTemperatureC = 16.0),
            base.copy(minTemperatureC = 4.0, maxTemperatureC = 12.0),
            base.copy(minTemperatureC = 11.0, maxTemperatureC = 21.0),
        )

        assertEquals(TemperatureSpan(4.0, 21.0), TemperatureSpan.of(days))
    }

    @Test
    fun `span fractions are clamped and handle a flat week`() {
        val span = TemperatureSpan(10.0, 20.0)
        assertEquals(0f, span.fractionOf(10.0))
        assertEquals(0.5f, span.fractionOf(15.0))
        assertEquals(1f, span.fractionOf(25.0))
        assertEquals(0f, span.fractionOf(-5.0))
        assertEquals(0.5f, TemperatureSpan(12.0, 12.0).fractionOf(12.0))
    }

    @Test
    fun `no span without days`() {
        assertNull(TemperatureSpan.of(emptyList()))
    }
}
