package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.DayPreset
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.testing.SampleForecasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class WorkWeekAnalysisTest {

    // Monday 12 Oct 2026 to Sunday 18 Oct.
    private val monday = LocalDate.of(2026, 10, 12)
    private val times = CommuteTimes(LocalTime.of(8, 0), LocalTime.of(17, 0))

    /** A full week of hourly data; [rain] maps (day index, hour) to a chance, everything else is dry. */
    private fun week(rain: Map<Pair<Int, Int>, Int> = emptyMap(), days: Int = 7): WeatherForecast {
        val base = SampleForecasts.forecast(start = monday.atStartOfDay(), hourlyRainChances = List(days * 24) { 0 })
        return base.copy(
            hourly = base.hourly.mapIndexed { i, hour ->
                val chance = rain[(i / 24) to (i % 24)] ?: 0
                hour.copy(
                    temperatureC = 20.0,
                    precipitationProbability = chance,
                    precipitationMm = 0.0,
                    condition = if (chance >= 60) WeatherCondition.Rain else WeatherCondition.Overcast,
                )
            },
            daily = (0 until 7).map { base.daily.first().copy(date = monday.plusDays(it.toLong())) },
        )
    }

    private fun analyse(forecast: WeatherForecast, mode: TransportMode = TransportMode.Motorcycle, workdays: Set<DayOfWeek> = UserPreferences.WEEKDAYS) =
        WorkWeekAnalysis.analyse(forecast, times, mode, workdays)

    @Test
    fun `only office days are rated`() {
        val week = analyse(week())

        assertEquals((0L..4L).map { monday.plusDays(it) }.toSet(), week.days.keys)
    }

    @Test
    fun `statuses follow the trips`() {
        val week = analyse(week(rain = mapOf((1 to 17) to 40, (2 to 8) to 80)))

        assertEquals(CommuteStatus.Good, week.days.getValue(monday).status)
        assertEquals(CommuteStatus.CarryUmbrella, week.days.getValue(monday.plusDays(1)).status)
        assertEquals(CommuteStatus.HighRainRisk, week.days.getValue(monday.plusDays(2)).status)
    }

    @Test
    fun `likely rain is only a high risk when it makes the trip difficult`() {
        val week = analyse(week(rain = mapOf((2 to 8) to 80)), mode = TransportMode.Car)

        assertEquals(CommuteStatus.CarryUmbrella, week.days.getValue(monday.plusDays(2)).status)
    }

    @Test
    fun `best and highest-risk days`() {
        val week = analyse(week(rain = mapOf((1 to 17) to 40, (2 to 8) to 80, (2 to 17) to 90)))

        assertEquals(monday, week.best)
        assertEquals(monday.plusDays(2), week.riskiest)
    }

    @Test
    fun `no best or worst day when every day looks the same`() {
        val week = analyse(week())

        assertNull(week.best)
        assertNull(week.riskiest)
    }

    @Test
    fun `a highest-risk day that is still good is not called risky`() {
        val week = analyse(week(rain = mapOf((3 to 8) to 20)))

        assertEquals(monday, week.best)
        assertNull(week.riskiest)
    }

    @Test
    fun `days without both trips in the forecast are left out`() {
        val short = week(days = 3).let { it.copy(hourly = it.hourly.dropLast(10)) } // Wednesday ends at 13:00

        assertEquals(setOf(monday, monday.plusDays(1)), analyse(short).days.keys)
    }

    @Test
    fun `no office days, no ratings`() {
        assertTrue(analyse(week(), workdays = emptySet()).days.isEmpty())
    }

    @Test
    fun `day presets are recognised`() {
        assertEquals(DayPreset.Weekdays, DayPreset.of(UserPreferences.WEEKDAYS))
        assertEquals(DayPreset.Weekends, DayPreset.of(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
        assertEquals(DayPreset.EveryDay, DayPreset.of(DayOfWeek.entries.toSet()))
        assertEquals(DayPreset.Custom, DayPreset.of(setOf(DayOfWeek.MONDAY)))
        assertEquals(DayPreset.Custom, DayPreset.of(emptySet()))
        assertNull(DayPreset.Custom.days)
    }
}
