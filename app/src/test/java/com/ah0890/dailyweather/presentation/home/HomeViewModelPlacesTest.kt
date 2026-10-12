package com.ah0890.dailyweather.presentation.home

import com.ah0890.dailyweather.data.local.DataStorePlacesRepository
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.PlaceType
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.domain.repository.LocationRepository
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.LocationResult
import com.ah0890.dailyweather.location.PlaceNameResolver
import com.ah0890.dailyweather.testing.FakeAirQualityRepository
import com.ah0890.dailyweather.testing.FakeWeatherRepository
import com.ah0890.dailyweather.testing.InMemoryPreferencesDataStore
import com.ah0890.dailyweather.testing.MainDispatcherRule
import com.ah0890.dailyweather.testing.SampleForecasts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPlacesTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-10T07:00:00Z"), ZoneOffset.UTC)
    private val london = Coordinates(51.51, -0.13)
    private val paris = Coordinates(48.86, 2.35)
    private val sydney = Coordinates(-33.87, 151.21)
    private val berlin = Coordinates(52.52, 13.4)

    private class CountingProvider(private val coordinates: Coordinates) : LocationProvider {
        var calls = 0
        override suspend fun currentLocation(): LocationResult {
            calls++
            return LocationResult.Success(coordinates)
        }
    }

    private class FakeLocationRepository(initial: SavedLocation?) : LocationRepository {
        override val savedLocation = MutableStateFlow(initial)
        override suspend fun save(location: LocationData) {
            savedLocation.value = SavedLocation(location, Instant.EPOCH)
        }
    }

    private object NoNames : PlaceNameResolver {
        override suspend fun placeName(coordinates: Coordinates): String? = null
    }

    private val places = DataStorePlacesRepository(InMemoryPreferencesDataStore())
    private val provider = CountingProvider(london)
    private val weather = FakeWeatherRepository({ c -> WeatherResult.Success(SampleForecasts.forecast(c), clock.instant()) })

    private fun viewModel(saved: SavedLocation? = SavedLocation(LocationData(london, "London"), clock.instant())) =
        HomeViewModel(provider, NoNames, FakeLocationRepository(saved), weather, clock, places, FakeAirQualityRepository())

    @Test
    fun `the default place is shown first without looking up the device location`() = runTest {
        val office = places.add("Office", PlaceType.Office, paris, "Paris")!!
        places.setDefault(office.id)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(LocationSource.Place, vm.locationState.value.source)
        assertEquals(office, vm.locationState.value.place)
        assertEquals(paris, (vm.weatherState.value as WeatherUiState.Loaded).forecast.coordinates)
        assertEquals(0, provider.calls)
        assertEquals("the device location stays available", london, vm.locationState.value.deviceLocation!!.location.coordinates)
    }

    @Test
    fun `without a default place the device location is shown`() = runTest {
        places.add("Parents", PlaceType.Parents, sydney, "Sydney")
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.locationState.value.place)
        assertEquals(london, (vm.weatherState.value as WeatherUiState.Loaded).forecast.coordinates)
    }

    @Test
    fun `selecting a place and going back to the device location`() = runTest {
        val parents = places.add("Parents", PlaceType.Parents, sydney, "Sydney")!!
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectPlace(parents.id)
        advanceUntilIdle()
        assertEquals(sydney, (vm.weatherState.value as WeatherUiState.Loaded).forecast.coordinates)

        vm.selectDeviceLocation()
        advanceUntilIdle()
        assertNull(vm.locationState.value.place)
        assertEquals(london, (vm.weatherState.value as WeatherUiState.Loaded).forecast.coordinates)
    }

    @Test
    fun `a deleted place falls back to the device location`() = runTest {
        val parents = places.add("Parents", PlaceType.Parents, sydney, "Sydney")!!
        val vm = viewModel()
        vm.selectPlace(parents.id)
        advanceUntilIdle()

        places.delete(parents.id)
        advanceUntilIdle()

        assertNull(vm.locationState.value.place)
        assertEquals(london, vm.locationState.value.location!!.location.coordinates)
    }

    @Test
    fun `home and office forecasts are loaded for the commute, other places never`() = runTest {
        places.add("Home", PlaceType.Home, paris, "Paris")
        places.add("Office", PlaceType.Office, berlin, "Berlin")
        places.add("Parents", PlaceType.Parents, sydney, "Sydney")
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(paris, vm.commuteState.value.home!!.coordinates)
        assertEquals(berlin, vm.commuteState.value.office!!.coordinates)
        assertEquals(setOf(london, paris, berlin), weather.requests.toSet())
        assertFalse(sydney in weather.requests)
    }

    @Test
    fun `the forecast on screen is reused for the commute instead of downloading it twice`() = runTest {
        val home = places.add("Home", PlaceType.Home, paris, "Paris")!!
        places.setDefault(home.id)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(paris, vm.commuteState.value.home!!.coordinates)
        assertEquals(listOf(paris), weather.requests)
    }

    @Test
    fun `no saved home or office means no extra forecasts`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CommuteForecasts(), vm.commuteState.value)
        assertEquals(listOf(london), weather.requests)
    }
}
