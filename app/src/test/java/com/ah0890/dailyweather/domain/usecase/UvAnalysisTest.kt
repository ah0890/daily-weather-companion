package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.UvCategory
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.notifications.DailySummary
import com.ah0890.dailyweather.testing.SampleForecasts
import com.ah0890.dailyweather.testing.SampleForecasts.hour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class UvAnalysisTest {

    private val day = LocalDate.of(2026, 6, 15)

    /** UV by hour of day for [day] (daylight 6–19), evaluated at [now]. */
    private fun forecast(now: LocalDateTime = day.atTime(8, 0), uv: (Int) -> Double? = { 0.0 }): WeatherForecast {
        val base = SampleForecasts.forecast(start = day.atStartOfDay())
        return base.copy(
            current = base.current.copy(time = now),
            hourly = (0 until 48).map { i ->
                val h = i % 24
                hour(time = day.atStartOfDay().plusHours(i.toLong()), isDay = h in 6..19, uv = uv(h))
            },
        )
    }

    private val summer: (Int) -> Double? = { h -> mapOf(10 to 4.0, 11 to 6.2, 12 to 7.8, 13 to 7.1, 14 to 5.0)[h] ?: 0.5 }

    @Test
    fun `who categories using the rounded index`() {
        assertEquals(UvCategory.Low, UvCategory.of(2.4))
        assertEquals(UvCategory.Moderate, UvCategory.of(2.5))
        assertEquals(UvCategory.High, UvCategory.of(5.5))
        assertEquals(UvCategory.VeryHigh, UvCategory.of(7.5))
        assertEquals(UvCategory.Extreme, UvCategory.of(10.5))
    }

    @Test
    fun `today's peak and high hours`() {
        val uv = UvAnalysis.today(forecast(uv = summer))!!

        assertEquals(7.8, uv.peakIndex, 0.0)
        assertEquals(day.atTime(12, 0), uv.peakTime)
        assertEquals(UvCategory.VeryHigh, uv.category)
        assertEquals(day.atTime(11, 0), uv.highFrom)
        assertEquals(day.atTime(14, 0), uv.highUntil)
    }

    @Test
    fun `only the rest of today counts`() {
        val uv = UvAnalysis.today(forecast(now = day.atTime(13, 20), uv = summer))!!

        assertEquals(7.1, uv.peakIndex, 0.0)
        assertEquals(day.atTime(13, 0), uv.peakTime)
    }

    @Test
    fun `nothing after dark`() {
        assertNull(UvAnalysis.today(forecast(now = day.atTime(21, 0), uv = summer)))
    }

    @Test
    fun `daily maximum without a time when hourly uv is missing`() {
        val uv = UvAnalysis.today(forecast(uv = { null }))!!

        assertEquals(2.0, uv.peakIndex, 0.0) // the sample's uvIndexMax
        assertNull(uv.peakTime)
    }

    @Test
    fun `alert thresholds`() {
        val uv = UvAnalysis.today(forecast(uv = summer))
        assertTrue(UvAnalysis.reaches(uv, UvCategory.High))
        assertTrue(UvAnalysis.reaches(uv, UvCategory.VeryHigh))

        val moderate = UvAnalysis.today(forecast(uv = { 5.4 }))
        assertFalse(UvAnalysis.reaches(moderate, UvCategory.High))
        assertFalse(UvAnalysis.reaches(null, UvCategory.High))
    }

    @Test
    fun `the morning summary mentions uv only when it reaches the chosen level`() {
        val f = forecast(uv = summer)
        val now = day.atTime(7, 0)

        assertEquals(7.8, DailySummary.from(f, null, now, uvAlertLevel = UvCategory.High).uv!!.peakIndex, 0.0)
        assertNull(DailySummary.from(f, null, now, uvAlertLevel = null).uv)
        assertNull(DailySummary.from(forecast(uv = { 6.5 }), null, now, uvAlertLevel = UvCategory.VeryHigh).uv)
    }
}
