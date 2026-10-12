package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.ChecklistEntry
import com.ah0890.dailyweather.domain.model.ChecklistItem
import com.ah0890.dailyweather.domain.model.ChecklistLevel
import com.ah0890.dailyweather.domain.model.CommuteAdvice
import com.ah0890.dailyweather.domain.model.CommuteLeg
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.CommuteSummary
import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.testing.SampleForecasts.hour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TravelChecklistTest {

    private val day = LocalDate.of(2026, 10, 12)

    private fun leg(
        type: CommuteLegType,
        rain: Int = 0,
        feelsLike: Double = 20.0,
        condition: WeatherCondition = WeatherCondition.Overcast,
        isDay: Boolean = true,
        uv: Double? = null,
    ): CommuteLeg {
        val leg = CommuteLeg(
            type = type,
            departure = day.atTime(if (type == CommuteLegType.Morning) 8 else 17, 0),
            condition = condition,
            isDay = isDay,
            temperatureC = feelsLike,
            rainChance = rain,
            windSpeedKmh = 10.0,
            rain = CommuteAnalysis.rainLevel(rain),
            windy = false,
            feelsLikeC = feelsLike,
            uvIndex = uv,
        )
        val hazards = HazardAnalysis.detect(listOf(hour(rainChance = rain, temperatureC = feelsLike, uv = uv, isDay = isDay)))
        return leg.copy(hazards = hazards)
    }

    private fun entries(
        mode: TransportMode,
        morning: CommuteLeg = leg(CommuteLegType.Morning),
        evening: CommuteLeg = leg(CommuteLegType.Evening),
        workday: List<HourlyForecast> = listOf(hour()),
    ) = TravelChecklist.entries(CommuteSummary(day, true, listOf(morning, evening), CommuteAdvice.Dry), workday, mode)

    @Test
    fun `a mild dry overcast day needs nothing`() {
        TransportMode.entries.forEach { assertEquals(it.name, emptyList<ChecklistEntry>(), entries(it)) }
    }

    @Test
    fun `motorcycle with a wet return suggests the car for the return trip and rain gear`() {
        val result = entries(TransportMode.Motorcycle, evening = leg(CommuteLegType.Evening, rain = 74))

        assertEquals(
            listOf(
                ChecklistEntry(ChecklistItem.AlternativeTransport, ChecklistLevel.Recommended, listOf(CommuteLegType.Evening)),
                ChecklistEntry(ChecklistItem.RainGear, ChecklistLevel.Recommended),
            ),
            result,
        )
    }

    @Test
    fun `motorcycle riders never get an umbrella`() {
        val result = entries(TransportMode.Motorcycle, evening = leg(CommuteLegType.Evening, rain = 40))

        assertEquals(listOf(ChecklistEntry(ChecklistItem.RainGear, ChecklistLevel.Useful)), result)
    }

    @Test
    fun `umbrella level follows the trips and the working day`() {
        assertEquals(
            listOf(ChecklistEntry(ChecklistItem.Umbrella, ChecklistLevel.Recommended)),
            entries(TransportMode.Walking, evening = leg(CommuteLegType.Evening, rain = 74)),
        )
        assertEquals(
            listOf(ChecklistEntry(ChecklistItem.Umbrella, ChecklistLevel.Useful)),
            entries(TransportMode.Car, morning = leg(CommuteLegType.Morning, rain = 35)),
        )
        // Lunchtime rain only.
        assertEquals(
            listOf(ChecklistEntry(ChecklistItem.Umbrella, ChecklistLevel.Recommended)),
            entries(TransportMode.PublicTransport, workday = listOf(hour(rainChance = 80))),
        )
    }

    @Test
    fun `jacket depends on the coldest trip, and riding feels colder`() {
        assertEquals(
            listOf(ChecklistEntry(ChecklistItem.Jacket, ChecklistLevel.Recommended)),
            entries(TransportMode.Car, morning = leg(CommuteLegType.Morning, feelsLike = 10.0)),
        )
        assertEquals(
            listOf(ChecklistEntry(ChecklistItem.Jacket, ChecklistLevel.Useful)),
            entries(TransportMode.Car, morning = leg(CommuteLegType.Morning, feelsLike = 15.0)),
        )
        assertTrue(entries(TransportMode.Car, morning = leg(CommuteLegType.Morning, feelsLike = 16.0)).isEmpty())
        assertEquals(
            listOf(ChecklistEntry(ChecklistItem.Jacket, ChecklistLevel.Useful)),
            entries(TransportMode.Motorcycle, morning = leg(CommuteLegType.Morning, feelsLike = 18.0)),
        )
    }

    @Test
    fun `water on hot days`() {
        val hot = listOf(hour(temperatureC = 31.0, feelsLikeC = 33.0))

        assertTrue(ChecklistEntry(ChecklistItem.Water, ChecklistLevel.Recommended) in entries(TransportMode.Car, workday = hot))
    }

    @Test
    fun `sunglasses for a bright daytime trip`() {
        val sunny = leg(CommuteLegType.Morning, condition = WeatherCondition.Clear, uv = 4.0)
        assertEquals(listOf(ChecklistEntry(ChecklistItem.Sunglasses, ChecklistLevel.Useful)), entries(TransportMode.Car, morning = sunny))

        val lowSun = leg(CommuteLegType.Morning, condition = WeatherCondition.Clear, uv = 1.0)
        assertTrue(entries(TransportMode.Car, morning = lowSun).isEmpty())

        val night = leg(CommuteLegType.Evening, condition = WeatherCondition.Clear, isDay = false)
        assertTrue(entries(TransportMode.Car, evening = night).isEmpty())
    }

    @Test
    fun `sun protection when uv is high during the working day, except by car`() {
        val strongSun = listOf(hour(uv = 7.0))

        assertTrue(ChecklistEntry(ChecklistItem.SunProtection, ChecklistLevel.Useful) in entries(TransportMode.Walking, workday = strongSun))
        assertTrue(entries(TransportMode.Car, workday = strongSun).none { it.item == ChecklistItem.SunProtection })
    }

    @Test
    fun `both trips difficult on a motorcycle lists both legs`() {
        val result = entries(
            TransportMode.Motorcycle,
            morning = leg(CommuteLegType.Morning).copy(hazards = setOf(WeatherHazard.Thunderstorm)),
            evening = leg(CommuteLegType.Evening).copy(hazards = setOf(WeatherHazard.Gale)),
        )

        assertEquals(listOf(CommuteLegType.Morning, CommuteLegType.Evening), result.first().legs)
    }
}
