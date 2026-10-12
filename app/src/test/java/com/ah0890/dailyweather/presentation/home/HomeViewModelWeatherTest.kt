package com.ah0890.dailyweather.presentation.home

import com.ah0890.dailyweather.data.local.DataStorePlacesRepository
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.domain.repository.LocationRepository
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.LocationResult
import com.ah0890.dailyweather.location.PlaceNameResolver
import com.ah0890.dailyweather.testing.FakeWeatherRepository
import com.ah0890.dailyweather.testing.InMemoryPreferencesDataStore
import com.ah0890.dailyweather.testing.MainDispatcherRule
import com.ah0890.dailyweather.testing.SampleForecasts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelWeatherTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val paris = Coordinates(48.86, 2.35)
    private val london = Coordinates(51.51, -0.13)

    /** Clock that tests can move forward. */
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-10T07:00:00Z"))

    private class FakeLocationRepository(initial: SavedLocation?) : LocationRepository {
        override val savedLocation = MutableStateFlow(initial)
        override suspend fun save(location: LocationData) {
            savedLocation.value = SavedLocation(location, Instant.EPOCH)
        }
    }

    private class FixedLocationProvider(private val coordinates: Coordinates) : LocationProvider {
        override suspend fun currentLocation() = LocationResult.Success(coordinates)
    }

    private object NoNames : PlaceNameResolver {
        override suspend fun placeName(coordinates: Coordinates): String? = null
    }

    private fun savedAt(coordinates: Coordinates) =
        SavedLocation(LocationData(coordinates, "Somewhere"), savedAt = clock.now)

    private fun viewModel(
        weather: FakeWeatherRepository,
        saved: SavedLocation? = savedAt(paris),
        liveLocation: Coordinates = london,
    ) = HomeViewModel(
        FixedLocationProvider(liveLocation),
        NoNames,
        FakeLocationRepository(saved),
        weather,
        clock,
        DataStorePlacesRepository(InMemoryPreferencesDataStore()),
    )

    private fun success(at: Instant = clock.now) = { c: Coordinates ->
        WeatherResult.Success(SampleForecasts.forecast(c), at)
    }

    @Test
    fun `no location means nothing is loaded`() = runTest {
        val weather = FakeWeatherRepository()
        val vm = viewModel(weather, saved = null)
        advanceUntilIdle()

        assertEquals(WeatherUiState.Idle, vm.weatherState.value)
        assertTrue(weather.requests.isEmpty())
    }

    @Test
    fun `saved location loads its forecast`() = runTest {
        val weather = FakeWeatherRepository(success())
        val vm = viewModel(weather)
        advanceUntilIdle()

        val state = vm.weatherState.value as WeatherUiState.Loaded
        assertEquals(paris, state.forecast.coordinates)
        assertEquals(listOf(paris), weather.requests)
    }

    @Test
    fun `failure without earlier data shows the error`() = runTest {
        val weather = FakeWeatherRepository { WeatherResult.Failure(WeatherError.NoConnection) }
        val vm = viewModel(weather)
        advanceUntilIdle()

        assertEquals(WeatherUiState.Error(WeatherError.NoConnection), vm.weatherState.value)
    }

    @Test
    fun `retry after an error loads the forecast`() = runTest {
        val weather = FakeWeatherRepository { WeatherResult.Failure(WeatherError.Timeout) }
        val vm = viewModel(weather)
        advanceUntilIdle()

        weather.result = success()
        vm.refreshWeather()
        advanceUntilIdle()

        assertTrue(vm.weatherState.value is WeatherUiState.Loaded)
    }

    @Test
    fun `failed refresh keeps the previous forecast and reports the error`() = runTest {
        val weather = FakeWeatherRepository(success())
        val vm = viewModel(weather)
        advanceUntilIdle()
        val before = vm.weatherState.value as WeatherUiState.Loaded

        weather.result = { WeatherResult.Failure(WeatherError.ServiceUnavailable) }
        vm.refreshWeather()
        advanceUntilIdle()

        val after = vm.weatherState.value as WeatherUiState.Loaded
        assertEquals(before.forecast, after.forecast)
        assertEquals(WeatherError.ServiceUnavailable, after.refreshError)
        assertEquals(false, after.isRefreshing)
    }

    @Test
    fun `refresh shows the old forecast while loading`() = runTest {
        val weather = FakeWeatherRepository(success())
        val vm = viewModel(weather)
        advanceUntilIdle()

        vm.refreshWeather()

        val refreshing = vm.weatherState.value as WeatherUiState.Loaded
        assertTrue(refreshing.isRefreshing)
    }

    @Test
    fun `resuming soon after loading does not fetch again`() = runTest {
        val weather = FakeWeatherRepository(success())
        val vm = viewModel(weather)
        advanceUntilIdle()

        clock.now = clock.now.plus(HomeViewModel.WEATHER_REFRESH_AFTER.minusMinutes(1))
        vm.refreshWeatherIfStale()
        advanceUntilIdle()

        assertEquals(1, weather.requests.size)
    }

    @Test
    fun `resuming after the forecast got old fetches again`() = runTest {
        val weather = FakeWeatherRepository(success())
        val vm = viewModel(weather)
        advanceUntilIdle()

        clock.now = clock.now.plus(HomeViewModel.WEATHER_REFRESH_AFTER.plusMinutes(1))
        vm.refreshWeatherIfStale()
        advanceUntilIdle()

        assertEquals(2, weather.requests.size)
    }

    @Test
    fun `a new location loads a new forecast instead of keeping the old one`() = runTest {
        val weather = FakeWeatherRepository(success())
        val vm = viewModel(weather, liveLocation = london)
        advanceUntilIdle()

        vm.updateLocation()
        advanceUntilIdle()

        assertEquals(listOf(paris, london), weather.requests)
        assertEquals(london, (vm.weatherState.value as WeatherUiState.Loaded).forecast.coordinates)
    }

    @Test
    fun `updating to the same place does not refetch`() = runTest {
        val weather = FakeWeatherRepository(success())
        val vm = viewModel(weather, liveLocation = Coordinates(48.8566, 2.3522))
        advanceUntilIdle()

        vm.updateLocation()
        advanceUntilIdle()

        assertEquals(1, weather.requests.size)
    }

    // --- Offline cache ---

    @Test
    fun `a fresh cached forecast is shown without any network request`() = runTest {
        val weather = FakeWeatherRepository(success())
        weather.cached = WeatherResult.Success(SampleForecasts.forecast(paris), clock.now.minusSeconds(10 * 60))
        val vm = viewModel(weather)
        advanceUntilIdle()

        assertTrue(vm.weatherState.value is WeatherUiState.Loaded)
        assertTrue(weather.requests.isEmpty())
    }

    @Test
    fun `an old cached forecast is shown while a new one loads`() = runTest {
        val weather = FakeWeatherRepository(success())
        val cachedAt = clock.now.minus(HomeViewModel.WEATHER_REFRESH_AFTER.plusMinutes(5))
        weather.cached = WeatherResult.Success(SampleForecasts.forecast(paris), cachedAt)
        val vm = viewModel(weather)
        advanceUntilIdle()

        val state = vm.weatherState.value as WeatherUiState.Loaded
        assertEquals(listOf(paris), weather.requests)
        assertEquals(clock.now, state.fetchedAt)
    }

    @Test
    fun `offline with a cached forecast shows it with the error`() = runTest {
        val cachedAt = clock.now.minusSeconds(5 * 3600)
        val weather = FakeWeatherRepository {
            WeatherResult.Stale(SampleForecasts.forecast(it), cachedAt, WeatherError.NoConnection)
        }
        val vm = viewModel(weather)
        advanceUntilIdle()

        val state = vm.weatherState.value as WeatherUiState.Loaded
        assertEquals(cachedAt, state.fetchedAt)
        assertEquals(WeatherError.NoConnection, state.refreshError)
    }

    @Test
    fun `pull to refresh ignores a fresh cache and downloads`() = runTest {
        val weather = FakeWeatherRepository(success())
        weather.cached = WeatherResult.Success(SampleForecasts.forecast(paris), clock.now)
        val vm = viewModel(weather)
        advanceUntilIdle()

        vm.refreshWeather()
        advanceUntilIdle()

        assertEquals(1, weather.requests.size)
    }

    @Test
    fun `a forecast from yesterday is moved forward to the current hour`() = runTest {
        // Sample forecast starts 08:00 local on Oct 10 (London); the clock says 13:30 local the same day.
        clock.now = Instant.parse("2026-10-10T12:30:00Z")
        val weather = FakeWeatherRepository {
            WeatherResult.Stale(SampleForecasts.forecast(it, hourlyRainChances = List(12) { 0 }), Instant.EPOCH, WeatherError.Timeout)
        }
        val vm = viewModel(weather)
        advanceUntilIdle()

        val forecast = (vm.weatherState.value as WeatherUiState.Loaded).forecast
        assertEquals(13, forecast.current.time.hour)
        assertEquals(13, forecast.upcomingHours().first().time.hour)
    }
}
