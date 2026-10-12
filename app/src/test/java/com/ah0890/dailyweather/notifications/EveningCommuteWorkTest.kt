package com.ah0890.dailyweather.notifications

import com.ah0890.dailyweather.data.local.DataStorePreferencesRepository
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.TravelTip
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.domain.repository.LocationRepository
import com.ah0890.dailyweather.domain.usecase.RainSummary
import com.ah0890.dailyweather.notifications.DailySummaryWork.Outcome
import com.ah0890.dailyweather.notifications.DailySummaryWork.SkipReason
import com.ah0890.dailyweather.testing.FakeWeatherRepository
import com.ah0890.dailyweather.testing.InMemoryPreferencesDataStore
import com.ah0890.dailyweather.testing.SampleForecasts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class EveningCommuteWorkTest {

    private val zone = ZoneId.of("Europe/London")
    private val monday = LocalDate.of(2026, 10, 12)

    private fun clockAt(date: LocalDate, hour: Int, minute: Int): Clock =
        Clock.fixed(date.atTime(hour, minute).atZone(zone).toInstant(), zone)

    private class FakeNotifier : DailySummaryNotifier {
        val alerts = mutableListOf<EveningAlert>()
        override fun canNotify() = true
        override fun show(summary: DailySummary, unit: TemperatureUnit) = Unit
        override fun showEveningAlert(alert: EveningAlert, unit: TemperatureUnit) {
            alerts += alert
        }
    }

    private class FakeLocations(initial: SavedLocation?) : LocationRepository {
        override val savedLocation = MutableStateFlow(initial)
        override suspend fun save(location: LocationData) = Unit
    }

    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val notifier = FakeNotifier()
    private val office = SavedLocation(LocationData(SampleForecasts.london, "London"), Instant.EPOCH)

    /** Hourly forecast for [date] from midnight, with [rain] chances by hour of day. */
    private fun forecast(date: LocalDate, now: LocalDateTime, rain: Map<Int, Int>) =
        SampleForecasts.forecast(start = date.atStartOfDay(), hourlyRainChances = List(30) { rain[it] ?: 5 })
            .let { it.copy(current = it.current.copy(time = now)) }

    /** Runs at 15:34 by default: the default alert time 15:30, a little late as WorkManager often is. */
    private fun work(rain: Map<Int, Int>, date: LocalDate = monday, hour: Int = 15, minute: Int = 34): DailySummaryWork {
        val clock = clockAt(date, hour, minute)
        val forecast = forecast(date, date.atTime(hour, minute), rain)
        val weather = FakeWeatherRepository { WeatherResult.Success(forecast, clock.instant()) }
        return DailySummaryWork(preferences, FakeLocations(office), weather, notifier, clock, NotificationKind.EveningCommute)
    }

    @Test
    fun `rain on the way home posts an office return alert with timing`() = runTest {
        preferences.setTransportMode(TransportMode.Motorcycle)

        val outcome = work(rain = mapOf(16 to 70, 17 to 75)).run(attempt = 0) as Outcome.Alerted

        with(outcome.alert) {
            assertEquals("London", locationName)
            assertEquals(75, rainChance)
            assertEquals(TravelRating.Unfavorable, advice.rating)
            assertTrue(TravelTip.ConsiderAlternative in advice.tips)
            assertEquals(RainSummary.Between(monday.atTime(16, 0), monday.atTime(18, 0)), rain)
        }
        assertEquals(1, notifier.alerts.size)
    }

    @Test
    fun `a dry trip home is not worth a notification`() = runTest {
        assertEquals(Outcome.Skipped(SkipReason.NothingToReport), work(rain = emptyMap()).run(attempt = 0))
        assertTrue(notifier.alerts.isEmpty())
    }

    @Test
    fun `no alert on a day that isn't an office day`() = runTest {
        preferences.setEveningAlertDays(UserPreferences.EVERY_DAY)
        val saturday = LocalDate.of(2026, 10, 10)

        assertEquals(Outcome.Skipped(SkipReason.NoCommute), work(rain = mapOf(17 to 90), date = saturday).run(attempt = 0))
    }

    @Test
    fun `weekend runs are skipped with the default weekday schedule`() = runTest {
        val saturday = LocalDate.of(2026, 10, 10)

        assertEquals(Outcome.Skipped(SkipReason.TooLate), work(rain = mapOf(17 to 90), date = saturday).run(attempt = 0))
    }

    @Test
    fun `switched off means nothing happens`() = runTest {
        preferences.setEveningAlertEnabled(false)

        assertEquals(Outcome.Skipped(SkipReason.Disabled), work(rain = mapOf(17 to 90)).run(attempt = 0))
    }

    @Test
    fun `a run delayed past the trip home is skipped`() = runTest {
        // 18:20: three hours late is within the limit, but the 17:00 trip is already over.
        preferences.setEveningAlertTime(LocalTime.of(16, 0))

        assertEquals(Outcome.Skipped(SkipReason.NoCommute), work(rain = mapOf(17 to 90), hour = 18, minute = 20).run(attempt = 0))
    }
}
