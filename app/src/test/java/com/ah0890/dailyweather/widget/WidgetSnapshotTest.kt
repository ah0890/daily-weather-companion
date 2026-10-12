package com.ah0890.dailyweather.widget

import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.testing.SampleForecasts
import com.ah0890.dailyweather.testing.SampleForecasts.hour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WidgetSnapshotTest {

    private val zone = ZoneId.of("Europe/London")
    private val monday = LocalDate.of(2026, 10, 12)
    private val now = monday.atTime(7, 30).atZone(zone).toInstant()
    private val prefs = UserPreferences(transportMode = TransportMode.Motorcycle)

    /** Monday from midnight, 36 hours; [rain] by hour of day, optional thunder at [stormHour]. */
    private fun forecast(rain: Map<Int, Int> = emptyMap(), stormHour: Int? = null): WeatherForecast {
        val base = SampleForecasts.forecast(start = monday.atStartOfDay())
        return base.copy(
            current = base.current.copy(time = monday.atTime(7, 0), temperatureC = 18.0),
            hourly = List(36) { i ->
                val h = i % 24
                hour(
                    time = monday.atStartOfDay().plusHours(i.toLong()),
                    rainChance = rain[h] ?: 0,
                    condition = if (h == stormHour) WeatherCondition.Thunderstorm else WeatherCondition.Overcast,
                )
            },
        )
    }

    @Test
    fun `a calm day shows the weather, the next trip and nothing alarming`() {
        val snapshot = WidgetSnapshot.from(forecast(), now, "Office", prefs, now)

        assertEquals("Office", snapshot.locationName)
        assertEquals(18.0, snapshot.temperatureC, 0.0)
        assertEquals(CommuteLegType.Morning, snapshot.commute!!.leg.type)
        assertEquals(TravelRating.Favorable, snapshot.commute!!.rating)
        assertNull(snapshot.alert)
        assertEquals(UmbrellaAdvice.NotNeeded, snapshot.umbrella)
        assertFalse(snapshot.outdated)
    }

    @Test
    fun `a difficult return trip is shown instead of a fine morning`() {
        val snapshot = WidgetSnapshot.from(forecast(rain = mapOf(17 to 80)), now, null, prefs, now)

        assertEquals(CommuteLegType.Evening, snapshot.commute!!.leg.type)
        assertEquals(TravelRating.Unfavorable, snapshot.commute!!.rating)
        assertEquals(80, snapshot.rainChance)
        assertEquals(UmbrellaAdvice.Recommended, snapshot.umbrella)
    }

    @Test
    fun `a severe alert is surfaced`() {
        val snapshot = WidgetSnapshot.from(forecast(stormHour = 15), now, null, prefs, now)

        assertEquals(WeatherHazard.Thunderstorm, snapshot.alert!!.hazard)
    }

    @Test
    fun `the office forecast is used for the trip home`() {
        val snapshot = WidgetSnapshot.from(forecast(), now, "Home", prefs, now, officeForecast = forecast(rain = mapOf(17 to 90)))

        assertEquals(CommuteLegType.Evening, snapshot.commute!!.leg.type)
        assertEquals(90, snapshot.commute!!.leg.rainChance)
    }

    @Test
    fun `no commute on a day off`() {
        val snapshot = WidgetSnapshot.from(forecast(), now, null, prefs.copy(workdays = emptySet()), now)

        assertNull(snapshot.commute)
    }

    @Test
    fun `an old cached forecast is marked outdated`() {
        val snapshot = WidgetSnapshot.from(forecast(), now.minusSeconds(7 * 3600), null, prefs, now)

        assertTrue(snapshot.outdated)
    }
}
