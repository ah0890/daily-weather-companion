package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteAdvice
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.CommuteRain
import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.testing.SampleForecasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CommuteAnalysisTest {

    private val day = LocalDate.of(2026, 10, 10)
    private val times = CommuteTimes(morning = LocalTime.of(8, 0), evening = LocalTime.of(17, 0))

    /**
     * Two days of hourly data from midnight. [rain] and [wind] map hour-of-forecast index → value;
     * every other hour is dry and calm.
     */
    private fun forecast(
        now: LocalDateTime,
        rain: Map<Int, Int> = emptyMap(),
        wind: Map<Int, Double> = emptyMap(),
    ): WeatherForecast {
        val base = SampleForecasts.forecast(start = day.atStartOfDay(), hourlyRainChances = List(48) { rain[it] ?: 0 })
        return base.copy(
            current = base.current.copy(time = now, condition = WeatherCondition.Overcast),
            hourly = base.hourly.mapIndexed { i, h -> h.copy(windSpeedKmh = wind[i] ?: 10.0) },
        )
    }

    @Test
    fun `early morning shows both legs for today`() {
        val summary = CommuteAnalysis.summary(forecast(now = day.atTime(6, 30)), times)!!

        assertTrue(summary.isToday)
        assertEquals(listOf(CommuteLegType.Morning, CommuteLegType.Evening), summary.legs.map { it.type })
        assertEquals(CommuteAdvice.Dry, summary.advice)
    }

    @Test
    fun `after the morning trip only the evening leg remains`() {
        val summary = CommuteAnalysis.summary(forecast(now = day.atTime(11, 0)), times)!!

        assertTrue(summary.isToday)
        assertEquals(listOf(CommuteLegType.Evening), summary.legs.map { it.type })
    }

    @Test
    fun `during the morning trip it is still shown`() {
        val summary = CommuteAnalysis.summary(forecast(now = day.atTime(8, 30)), times)!!

        assertEquals(CommuteLegType.Morning, summary.legs.first().type)
    }

    @Test
    fun `after the evening trip tomorrow's commute is shown`() {
        val summary = CommuteAnalysis.summary(forecast(now = day.atTime(19, 0)), times)!!

        assertFalse(summary.isToday)
        assertEquals(day.plusDays(1), summary.date)
        assertEquals(2, summary.legs.size)
    }

    @Test
    fun `rain in the hour after departure counts for the trip`() {
        // 17:00 dry, 18:00 70% → evening leg is wet.
        val summary = CommuteAnalysis.summary(forecast(now = day.atTime(6, 0), rain = mapOf(18 to 70)), times)!!
        val evening = summary.legs.single { it.type == CommuteLegType.Evening }

        assertEquals(70, evening.rainChance)
        assertEquals(CommuteRain.Likely, evening.rain)
        assertEquals(WeatherCondition.Rain, evening.condition)
        assertEquals(CommuteAdvice.RainLikely, summary.advice)
    }

    @Test
    fun `rain outside the trip windows does not affect the commute`() {
        val summary = CommuteAnalysis.summary(forecast(now = day.atTime(6, 0), rain = mapOf(12 to 90, 13 to 90)), times)!!

        assertEquals(CommuteAdvice.Dry, summary.advice)
    }

    @Test
    fun `rain levels follow the umbrella thresholds`() {
        assertEquals(CommuteRain.Dry, CommuteAnalysis.rainLevel(null))
        assertEquals(CommuteRain.Dry, CommuteAnalysis.rainLevel(29))
        assertEquals(CommuteRain.Possible, CommuteAnalysis.rainLevel(30))
        assertEquals(CommuteRain.Likely, CommuteAnalysis.rainLevel(60))
    }

    @Test
    fun `possible rain outranks wind, likely rain outranks possible`() {
        val possibleAndWindy = CommuteAnalysis.summary(
            forecast(now = day.atTime(6, 0), rain = mapOf(8 to 40), wind = mapOf(17 to 45.0)),
            times,
        )!!
        assertEquals(CommuteAdvice.RainPossible, possibleAndWindy.advice)

        val likely = CommuteAnalysis.summary(forecast(now = day.atTime(6, 0), rain = mapOf(8 to 40, 17 to 80)), times)!!
        assertEquals(CommuteAdvice.RainLikely, likely.advice)
    }

    @Test
    fun `strong wind on a dry day gives a wind warning`() {
        val summary = CommuteAnalysis.summary(forecast(now = day.atTime(6, 0), wind = mapOf(8 to 42.0)), times)!!

        assertTrue(summary.legs.first().windy)
        assertEquals(CommuteAdvice.Windy, summary.advice)
    }

    @Test
    fun `departure times that are not on the hour use the surrounding hours`() {
        val summary = CommuteAnalysis.summary(
            forecast(now = day.atTime(6, 0), rain = mapOf(7 to 90)),
            CommuteTimes(morning = LocalTime.of(7, 45), evening = LocalTime.of(17, 30)),
        )!!

        assertEquals(CommuteRain.Likely, summary.legs.first().rain)
    }

    @Test
    fun `no summary when the forecast doesn't cover the commute`() {
        val short = forecast(now = day.atTime(6, 0)).let { it.copy(hourly = it.hourly.take(7)) } // until 06:00

        assertNull(CommuteAnalysis.summary(short, times))
    }
}
