package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteAdvice
import com.ah0890.dailyweather.domain.model.CommuteLeg
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.CommuteSummary
import com.ah0890.dailyweather.domain.model.ScoreBand
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.testing.SampleForecasts.hour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OfficeScoreTest {

    private val day = LocalDate.of(2026, 10, 12)

    private fun leg(type: CommuteLegType, rain: Int?, vararg hazards: WeatherHazard) = CommuteLeg(
        type = type,
        departure = day.atTime(if (type == CommuteLegType.Morning) 8 else 17, 0),
        condition = WeatherCondition.Overcast,
        isDay = true,
        temperatureC = 20.0,
        rainChance = rain,
        windSpeedKmh = 10.0,
        rain = CommuteAnalysis.rainLevel(rain),
        windy = false,
        hazards = hazards.toSet(),
    )

    private fun summary(vararg legs: CommuteLeg) =
        CommuteSummary(day, isToday = true, legs = legs.toList(), advice = CommuteAdvice.Dry)

    @Test
    fun `dry calm day scores 100 and is good`() {
        val score = OfficeScore.of(summary(leg(CommuteLegType.Morning, 0), leg(CommuteLegType.Evening, 0)), listOf(hour()))

        assertEquals(100, score.value)
        assertEquals(ScoreBand.Good, score.band)
    }

    @Test
    fun `rain chance on the trips lowers the score`() {
        // 12% and 74% → 100 − 3.6 − 22.2 = 74.2
        val score = OfficeScore.of(
            summary(leg(CommuteLegType.Morning, 12), leg(CommuteLegType.Evening, 74, WeatherHazard.RainLikely)),
            emptyList(),
        )

        assertEquals(74, score.value)
        assertEquals(ScoreBand.Moderate, score.band)
    }

    @Test
    fun `unknown rain chance costs nothing`() {
        assertEquals(100, OfficeScore.of(summary(leg(CommuteLegType.Morning, null)), emptyList()).value)
    }

    @Test
    fun `severe weather on a trip makes it poor`() {
        val score = OfficeScore.of(
            summary(
                leg(CommuteLegType.Morning, 20),
                leg(CommuteLegType.Evening, 90, WeatherHazard.Thunderstorm, WeatherHazard.HeavyRain, WeatherHazard.Gale),
            ),
            emptyList(),
        )

        assertEquals(ScoreBand.Poor, score.band)
        assertTrue(score.value < OfficeScore.MODERATE_FROM)
    }

    @Test
    fun `hazards between the trips count half`() {
        val trips = summary(leg(CommuteLegType.Morning, 0), leg(CommuteLegType.Evening, 0))
        val hotAfternoon = listOf(hour(temperatureC = 36.0, feelsLikeC = 42.0))

        // Extreme heat is 20 points on a trip, 10 during the day.
        assertEquals(90, OfficeScore.of(trips, hotAfternoon).value)
    }

    @Test
    fun `a hazard on a trip is not counted twice`() {
        val trips = summary(leg(CommuteLegType.Morning, 0), leg(CommuteLegType.Evening, 0, WeatherHazard.Thunderstorm))
        val stormyDay = listOf(hour(condition = WeatherCondition.Thunderstorm))

        assertEquals(75, OfficeScore.of(trips, stormyDay).value)
    }

    @Test
    fun `score never goes below zero`() {
        val everything = WeatherHazard.entries.toTypedArray()
        val score = OfficeScore.of(
            summary(leg(CommuteLegType.Morning, 100, *everything), leg(CommuteLegType.Evening, 100, *everything)),
            emptyList(),
        )

        assertEquals(0, score.value)
    }

    @Test
    fun `band boundaries`() {
        assertEquals(ScoreBand.Good, OfficeScore.band(75))
        assertEquals(ScoreBand.Moderate, OfficeScore.band(74))
        assertEquals(ScoreBand.Moderate, OfficeScore.band(45))
        assertEquals(ScoreBand.Poor, OfficeScore.band(44))
    }
}
