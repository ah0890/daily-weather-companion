package com.ah0890.dailyweather.notifications

import com.ah0890.dailyweather.data.local.DataStorePreferencesRepository
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.TravelTip
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.model.WeatherError
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
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DailySummaryWorkTest {

    // 07:02 in London on the sample forecast's day; the sample starts at 08:00 local.
    private val zone = ZoneId.of("Europe/London")
    private val now = LocalDateTime.of(2026, 10, 10, 7, 2).atZone(zone).toInstant()
    private val clock = Clock.fixed(now, zone)

    private class FakeNotifier(var allowed: Boolean = true) : DailySummaryNotifier {
        val shown = mutableListOf<Pair<DailySummary, TemperatureUnit>>()
        override fun canNotify() = allowed
        override fun show(summary: DailySummary, unit: TemperatureUnit) {
            shown += summary to unit
        }
        override fun showEveningAlert(alert: EveningAlert, unit: TemperatureUnit) = Unit
    }

    private class FakeLocations(initial: SavedLocation?) : LocationRepository {
        override val savedLocation = MutableStateFlow(initial)
        override suspend fun save(location: LocationData) = Unit
    }

    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val notifier = FakeNotifier()
    private val london = SavedLocation(LocationData(SampleForecasts.london, "London"), Instant.EPOCH)

    private fun work(
        weather: FakeWeatherRepository = FakeWeatherRepository(),
        location: SavedLocation? = london,
        at: Clock = clock,
    ) = DailySummaryWork(preferences, FakeLocations(location), weather, notifier, at)

    private fun rainyForecast() = SampleForecasts.forecast(
        start = LocalDateTime.of(2026, 10, 10, 7, 0),
        hourlyRainChances = List(17) { if (it in 7..9) 80 else 10 }, // 14:00–17:00
    ).let { it.copy(current = it.current.copy(time = LocalDateTime.of(2026, 10, 10, 7, 0))) }

    // The sample day (10 Oct 2026) is a Saturday; most tests are about content, not days.
    @Before
    fun everyDay() = runTest {
        preferences.setDailyNotificationDays(UserPreferences.EVERY_DAY)
    }

    @Test
    fun `weekday-only summaries skip the weekend`() = runTest {
        preferences.setDailyNotificationDays(UserPreferences.WEEKDAYS)
        val weather = FakeWeatherRepository()

        // Saturday 07:02: the last scheduled run was Friday, so this one is far too late.
        assertEquals(Outcome.Skipped(SkipReason.TooLate), work(weather).run(attempt = 0))
        assertTrue(weather.requests.isEmpty())
    }

    @Test
    fun `no commute lines when today is not an office day`() = runTest {
        val weather = FakeWeatherRepository { WeatherResult.Success(rainyForecast(), now) }

        val outcome = work(weather).run(attempt = 0) as Outcome.Notified

        assertTrue(outcome.summary.commute.isEmpty())
    }

    @Test
    fun `on an office day the summary includes the commute for the chosen transport`() = runTest {
        preferences.setWorkdays(UserPreferences.EVERY_DAY)
        preferences.setTransportMode(TransportMode.Motorcycle)
        // Rain 14:00–17:00 in the sample; the evening trip leaves at 17:00 and the hour before is wet.
        preferences.setEveningCommute(LocalTime.of(16, 0))
        val weather = FakeWeatherRepository { WeatherResult.Success(rainyForecast(), now) }

        val outcome = work(weather).run(attempt = 0) as Outcome.Notified

        val (morning, evening) = outcome.summary.commute
        assertEquals(TravelRating.Favorable, morning.rating)
        assertEquals(TravelRating.Unfavorable, evening.rating)
        assertTrue(TravelTip.ConsiderAlternative in evening.tips)
    }

    @Test
    fun `posts a summary with rain timing and umbrella advice`() = runTest {
        val weather = FakeWeatherRepository { WeatherResult.Success(rainyForecast(), now) }

        val outcome = work(weather).run(attempt = 0) as Outcome.Notified

        with(outcome.summary) {
            assertEquals("London", locationName)
            assertEquals(80, rainChance)
            assertEquals(
                RainSummary.Between(LocalDateTime.of(2026, 10, 10, 14, 0), LocalDateTime.of(2026, 10, 10, 17, 0)),
                rain,
            )
            assertEquals(UmbrellaAdvice.Recommended, umbrella)
            assertEquals(16.0, highC!!, 0.0)
        }
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun `uses the user's temperature unit`() = runTest {
        preferences.setTemperatureUnit(TemperatureUnit.Fahrenheit)

        work().run(attempt = 0)

        assertEquals(TemperatureUnit.Fahrenheit, notifier.shown.single().second)
    }

    @Test
    fun `does nothing when the summary is switched off`() = runTest {
        preferences.setDailyNotificationEnabled(false)
        val weather = FakeWeatherRepository()

        assertEquals(Outcome.Skipped(SkipReason.Disabled), work(weather).run(attempt = 0))
        assertTrue(weather.requests.isEmpty())
    }

    @Test
    fun `does not download when notifications are blocked`() = runTest {
        notifier.allowed = false
        val weather = FakeWeatherRepository()

        assertEquals(Outcome.Skipped(SkipReason.Blocked), work(weather).run(attempt = 0))
        assertTrue(weather.requests.isEmpty())
    }

    @Test
    fun `needs a saved location and never looks one up`() = runTest {
        assertEquals(Outcome.Skipped(SkipReason.NoLocation), work(location = null).run(attempt = 0))
    }

    @Test
    fun `skips a summary that would arrive hours late`() = runTest {
        val afternoon = Clock.fixed(LocalDateTime.of(2026, 10, 10, 15, 0).atZone(zone).toInstant(), zone)

        assertEquals(Outcome.Skipped(SkipReason.TooLate), work(at = afternoon).run(attempt = 0))
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `respects a custom notification time`() = runTest {
        preferences.setDailyNotificationTime(LocalTime.of(6, 0))
        val sixOFive = Clock.fixed(LocalDateTime.of(2026, 10, 10, 6, 5).atZone(zone).toInstant(), zone)

        assertTrue(work(at = sixOFive).run(attempt = 0) is Outcome.Notified)
    }

    @Test
    fun `network failure retries, then gives up for the day`() = runTest {
        val weather = FakeWeatherRepository { WeatherResult.Failure(WeatherError.NoConnection) }

        assertEquals(Outcome.Retry, work(weather).run(attempt = 0))
        assertEquals(Outcome.Retry, work(weather).run(attempt = 1))
        assertEquals(Outcome.Skipped(SkipReason.NoData), work(weather).run(attempt = DailySummaryWork.MAX_ATTEMPTS - 1))
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `a recent offline copy is good enough`() = runTest {
        val weather = FakeWeatherRepository {
            WeatherResult.Stale(rainyForecast(), now.minusSeconds(2 * 3600), WeatherError.NoConnection)
        }

        assertTrue(work(weather).run(attempt = 0) is Outcome.Notified)
    }

    @Test
    fun `an old offline copy is not used`() = runTest {
        val weather = FakeWeatherRepository {
            WeatherResult.Stale(rainyForecast(), now.minusSeconds(24 * 3600), WeatherError.NoConnection)
        }

        assertEquals(Outcome.Retry, work(weather).run(attempt = 0))
    }

    @Test
    fun `an unexpected error skips the day instead of failing the job`() = runTest {
        val weather = FakeWeatherRepository { error("bug in a repository") }

        assertEquals(Outcome.Skipped(SkipReason.Error), work(weather).run(attempt = 0))
    }
}
