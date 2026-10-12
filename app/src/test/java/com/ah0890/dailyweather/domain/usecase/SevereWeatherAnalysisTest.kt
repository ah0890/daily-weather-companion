package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.testing.SampleForecasts
import com.ah0890.dailyweather.testing.SampleForecasts.hour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SevereWeatherAnalysisTest {

    private val day = LocalDate.of(2026, 10, 12)

    /** 30 calm, dry hours from midnight; [change] adjusts hour-of-day entries. */
    private fun forecast(now: LocalDateTime = day.atTime(6, 0), change: (Int, HourlyForecast) -> HourlyForecast = { _, h -> h }): WeatherForecast {
        val base = SampleForecasts.forecast(start = day.atStartOfDay())
        return base.copy(
            current = base.current.copy(time = now, condition = WeatherCondition.Overcast),
            hourly = List(30) { i -> change(i % 24, hour(time = day.atStartOfDay().plusHours(i.toLong()))) },
        )
    }

    @Test
    fun `calm weather has no alerts`() {
        assertTrue(SevereWeatherAnalysis.alerts(forecast()).isEmpty())
    }

    @Test
    fun `consecutive stormy hours form one severe alert`() {
        val stormy = forecast { h, hour -> if (h in 15..17) hour.copy(condition = WeatherCondition.Thunderstorm) else hour }

        val alert = SevereWeatherAnalysis.alerts(stormy).single()

        assertEquals(WeatherHazard.Thunderstorm, alert.hazard)
        assertEquals(AlertLevel.Severe, alert.level)
        assertEquals(day.atTime(15, 0), alert.start)
        assertEquals(day.atTime(18, 0), alert.end)
    }

    @Test
    fun `an alert during a commute trip names the trip`() {
        val stormy = forecast { h, hour -> if (h == 18) hour.copy(condition = WeatherCondition.Thunderstorm) else hour }
        val legs = CommuteAnalysis.legsOn(stormy, day, CommuteTimes(LocalTime.of(8, 0), LocalTime.of(17, 30)))

        val alert = SevereWeatherAnalysis.alerts(stormy, legs).single()

        assertEquals(listOf(CommuteLegType.Evening), alert.legs)
    }

    @Test
    fun `heat levels merge into one alert named after the strongest`() {
        val hot = forecast { h, hour ->
            when (h) {
                13 -> hour.copy(feelsLikeC = 36.0)
                14 -> hour.copy(feelsLikeC = 42.0)
                15 -> hour.copy(feelsLikeC = 37.0)
                else -> hour
            }
        }

        val alert = SevereWeatherAnalysis.alerts(hot).single()

        assertEquals(WeatherHazard.ExtremeHeat, alert.hazard)
        assertEquals(42.0, alert.peakFeelsLikeC!!, 0.0)
        assertEquals(day.atTime(13, 0), alert.start)
        assertEquals(day.atTime(16, 0), alert.end)
    }

    @Test
    fun `poor visibility and plain heat are advisories`() {
        val foggy = forecast { h, hour -> if (h == 7) hour.copy(visibilityMeters = 600.0) else hour }
        val alert = SevereWeatherAnalysis.alerts(foggy).single()

        assertEquals(WeatherHazard.PoorVisibility, alert.hazard)
        assertEquals(AlertLevel.Advisory, alert.level)

        val warm = forecast { h, hour -> if (h == 14) hour.copy(feelsLikeC = 35.0) else hour }
        assertEquals(AlertLevel.Advisory, SevereWeatherAnalysis.alerts(warm).single().level)
    }

    @Test
    fun `ordinary rain and strong wind are not alerts`() {
        val wet = forecast { h, hour -> if (h == 9) hour.copy(precipitationProbability = 90, precipitationMm = 3.9, windSpeedKmh = 45.0) else hour }

        assertTrue(SevereWeatherAnalysis.alerts(wet).isEmpty())
    }

    @Test
    fun `several hazards at once are all reported, most serious first`() {
        val wild = forecast { h, hour ->
            if (h == 16) hour.copy(precipitationMm = 8.0, windGustsKmh = 90.0, condition = WeatherCondition.Thunderstorm) else hour
        }

        assertEquals(
            listOf(WeatherHazard.Thunderstorm, WeatherHazard.Gale, WeatherHazard.HeavyRain),
            SevereWeatherAnalysis.alerts(wild).map { it.hazard },
        )
    }

    @Test
    fun `only the next 24 hours count and past hours are ignored`() {
        val f = forecast(now = day.atTime(10, 30)) { h, hour -> if (h == 5) hour.copy(condition = WeatherCondition.Thunderstorm) else hour }

        // 05:00 today is past; 05:00 tomorrow (index 29) is within 24 h.
        assertEquals(listOf(day.plusDays(1).atTime(5, 0)), SevereWeatherAnalysis.alerts(f).map { it.start })
    }

    @Test
    fun `rain soon`() {
        fun chances(now: Int, next: Int, after: Int, raining: Boolean = false) = forecast(now = day.atTime(9, 10)) { h, hour ->
            when (h) {
                9 -> hour.copy(precipitationProbability = now)
                10 -> hour.copy(precipitationProbability = next)
                11 -> hour.copy(precipitationProbability = after)
                else -> hour
            }
        }.let { if (raining) it.copy(current = it.current.copy(condition = WeatherCondition.Rain)) else it }

        assertEquals(RainSoon.NoneExpected, RainSoon.of(chances(10, 20, 29)))
        assertEquals(RainSoon.Possible(35), RainSoon.of(chances(10, 20, 35)))
        assertEquals(RainSoon.StartingWithinHour(50), RainSoon.of(chances(10, 50, 10)))
        assertEquals(RainSoon.Ongoing(easingSoon = false), RainSoon.of(chances(80, 70, 10)))
        assertEquals(RainSoon.Ongoing(easingSoon = true), RainSoon.of(chances(10, 10, 10, raining = true)))
    }

    @Test
    fun `rain soon needs the next hour's chance`() {
        val unknown = forecast(now = day.atTime(9, 10)) { h, hour -> if (h == 10) hour.copy(precipitationProbability = null) else hour }

        assertNull(RainSoon.of(unknown))
    }
}
