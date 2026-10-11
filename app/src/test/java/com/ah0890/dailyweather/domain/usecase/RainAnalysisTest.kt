package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.RainInformation
import com.ah0890.dailyweather.domain.model.RainPeriod
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.testing.SampleForecasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RainAnalysisTest {

    private val start = LocalDateTime.of(2026, 10, 10, 8, 0)

    /** Builds consecutive hours from 08:00 with the given chances (and optional amounts). */
    private fun hours(vararg chances: Int?, mm: Double? = null): List<HourlyForecast> =
        chances.mapIndexed { i, chance ->
            HourlyForecast(
                time = start.plusHours(i.toLong()),
                condition = WeatherCondition.PartlyCloudy,
                temperatureC = 15.0,
                precipitationProbability = chance,
                precipitationMm = mm,
                windSpeedKmh = 10.0,
                isDay = true,
            )
        }

    private fun at(hour: Int) = start.withHour(hour)

    // --- Umbrella advice across the probability range ---

    @Test
    fun `0 percent means no umbrella`() {
        assertEquals(UmbrellaAdvice.NotNeeded, RainAnalysis.outlook(hours(0, 0, 0)).recommendation.umbrella)
    }

    @Test
    fun `20 percent means no umbrella`() {
        assertEquals(UmbrellaAdvice.NotNeeded, RainAnalysis.outlook(hours(5, 20, 10)).recommendation.umbrella)
    }

    @Test
    fun `50 percent means consider an umbrella`() {
        assertEquals(UmbrellaAdvice.Consider, RainAnalysis.outlook(hours(10, 50, 20)).recommendation.umbrella)
    }

    @Test
    fun `80 percent means umbrella recommended`() {
        assertEquals(UmbrellaAdvice.Recommended, RainAnalysis.outlook(hours(10, 80, 20)).recommendation.umbrella)
    }

    @Test
    fun `100 percent means umbrella recommended`() {
        assertEquals(UmbrellaAdvice.Recommended, RainAnalysis.outlook(hours(100, 100)).recommendation.umbrella)
    }

    @Test
    fun `threshold boundaries are inclusive`() {
        assertEquals(UmbrellaAdvice.NotNeeded, RainAnalysis.outlook(hours(29)).recommendation.umbrella)
        assertEquals(UmbrellaAdvice.Consider, RainAnalysis.outlook(hours(30)).recommendation.umbrella)
        assertEquals(UmbrellaAdvice.Consider, RainAnalysis.outlook(hours(59)).recommendation.umbrella)
        assertEquals(UmbrellaAdvice.Recommended, RainAnalysis.outlook(hours(60)).recommendation.umbrella)
    }

    @Test
    fun `rain falling now recommends an umbrella regardless of later chances`() {
        assertEquals(
            UmbrellaAdvice.Recommended,
            RainAnalysis.outlook(hours(10, 10), rainingNow = true).recommendation.umbrella,
        )
    }

    @Test
    fun `a measurable amount with low chances suggests considering one`() {
        val outlook = RainAnalysis.outlook(hours(20, 20, 20, 20, mm = 0.3))

        assertEquals(1.2, outlook.rain.totalMm!!, 1e-9)
        assertEquals(UmbrellaAdvice.Consider, outlook.recommendation.umbrella)
    }

    @Test
    fun `missing probabilities are treated as no rain, not a crash`() {
        val outlook = RainAnalysis.outlook(hours(null, null))

        assertNull(outlook.rain.maxProbability)
        assertNull(outlook.rain.totalMm)
        assertEquals(UmbrellaAdvice.NotNeeded, outlook.recommendation.umbrella)
    }

    @Test
    fun `no hours at all is handled`() {
        val outlook = RainAnalysis.outlook(emptyList())

        assertTrue(outlook.rain.periods.isEmpty())
        assertEquals(UmbrellaAdvice.NotNeeded, outlook.recommendation.umbrella)
    }

    // --- Rain periods ---

    @Test
    fun `consecutive rainy hours form one period with its peak`() {
        // 08 09 10 11 12 13
        val rain = RainAnalysis.rainInformation(hours(10, 45, 70, 65, 20, 10))

        assertEquals(listOf(RainPeriod(at(9), at(12), 70)), rain.periods)
        assertEquals(70, rain.maxProbability)
        assertEquals(at(10), rain.peakTime)
    }

    @Test
    fun `a single dry hour between rainy hours is bridged`() {
        // 08 09 10 11
        val rain = RainAnalysis.rainInformation(hours(60, 35, 70, 10))

        assertEquals(listOf(RainPeriod(at(8), at(11), 70)), rain.periods)
    }

    @Test
    fun `two or more dry hours split periods`() {
        // 08 09 10 11 12 13
        val rain = RainAnalysis.rainInformation(hours(60, 10, 10, 50, 55, 0))

        assertEquals(
            listOf(RainPeriod(at(8), at(9), 60), RainPeriod(at(11), at(13), 55)),
            rain.periods,
        )
    }

    @Test
    fun `40 percent counts as likely, 39 does not`() {
        assertEquals(1, RainAnalysis.rainInformation(hours(40)).periods.size)
        assertTrue(RainAnalysis.rainInformation(hours(39)).periods.isEmpty())
    }

    // --- Window for the rest of today ---

    @Test
    fun `rest of today ignores past hours and tomorrow`() {
        val forecast = SampleForecasts.forecast(
            start = LocalDateTime.of(2026, 10, 10, 6, 0),
            hourlyRainChances = List(30) { if (it == 1 || it == 26) 90 else 0 }, // 07:00 today, 08:00 tomorrow
        ).let { it.copy(current = it.current.copy(time = LocalDateTime.of(2026, 10, 10, 9, 30), condition = WeatherCondition.Overcast)) }

        val outlook = RainAnalysis.outlookForRestOfToday(forecast)

        assertTrue(outlook.rain.periods.isEmpty())
        assertEquals(UmbrellaAdvice.NotNeeded, outlook.recommendation.umbrella)
    }

    @Test
    fun `late in the evening the window extends into the night`() {
        // Starts 20:00; rain at 01:00 the next day is within the 6-hour minimum window from 22:00.
        val forecast = SampleForecasts.forecast(
            start = LocalDateTime.of(2026, 10, 10, 20, 0),
            hourlyRainChances = List(10) { if (it == 5) 80 else 0 },
        ).let { it.copy(current = it.current.copy(time = LocalDateTime.of(2026, 10, 10, 22, 10), condition = WeatherCondition.Overcast)) }

        val outlook = RainAnalysis.outlookForRestOfToday(forecast)

        assertEquals(UmbrellaAdvice.Recommended, outlook.recommendation.umbrella)
    }

    // --- Summary sentence selection ---

    @Test
    fun `rain already in the current hour reads as now-until`() {
        val rain = RainAnalysis.rainInformation(hours(70, 80, 10))

        assertEquals(RainSummary.NowUntil(at(10)), RainAnalysis.summary(rain, at(8).plusMinutes(20)))
    }

    @Test
    fun `later rain reads as a between range`() {
        val rain = RainAnalysis.rainInformation(hours(0, 0, 0, 0, 0, 0, 70, 80, 75, 10)) // 14:00–17:00

        assertEquals(RainSummary.Between(at(14), at(17)), RainAnalysis.summary(rain, at(8)))
    }

    @Test
    fun `a moderate chance without likely hours reads as chance-around`() {
        val rain = RainAnalysis.rainInformation(hours(10, 35, 20))

        assertEquals(RainSummary.ChanceAround(35, at(9)), RainAnalysis.summary(rain, at(8)))
    }

    @Test
    fun `low chances with some rain read as light rain`() {
        val rain = RainInformation(maxProbability = 15, peakTime = at(9), totalMm = 1.5, periods = emptyList())

        assertEquals(RainSummary.LightRain(1.5), RainAnalysis.summary(rain, at(8)))
    }

    @Test
    fun `dry days read as low`() {
        val rain = RainAnalysis.rainInformation(hours(0, 5, 10, mm = 0.0))

        assertEquals(RainSummary.Low, RainAnalysis.summary(rain, at(8)))
    }
}
