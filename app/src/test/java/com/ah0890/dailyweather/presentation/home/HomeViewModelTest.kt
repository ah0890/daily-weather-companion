package com.ah0890.dailyweather.presentation.home

import com.ah0890.dailyweather.data.local.DataStorePlacesRepository
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.domain.repository.LocationRepository
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.LocationResult
import com.ah0890.dailyweather.location.PlaceNameResolver
import com.ah0890.dailyweather.testing.FakeAirQualityRepository
import com.ah0890.dailyweather.testing.FakeWeatherRepository
import com.ah0890.dailyweather.testing.InMemoryPreferencesDataStore
import com.ah0890.dailyweather.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now = Instant.parse("2026-10-10T07:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val london = Coordinates(51.51, -0.13)
    private val paris = Coordinates(48.86, 2.35)
    private val londonData = LocationData(london, "London")

    private class FakeLocationProvider(var result: LocationResult) : LocationProvider {
        var requests = 0
        override suspend fun currentLocation(): LocationResult {
            requests++
            return result
        }
    }

    private class FakeResolver(private val name: String?) : PlaceNameResolver {
        override suspend fun placeName(coordinates: Coordinates): String? = name
    }

    private class FakeLocationRepository(initial: SavedLocation? = null, val failOnSave: Boolean = false) :
        LocationRepository {
        override val savedLocation = MutableStateFlow(initial)
        var saves = 0
        override suspend fun save(location: LocationData) {
            saves++
            if (failOnSave) throw IOException("disk full")
            savedLocation.value = SavedLocation(location, Instant.EPOCH)
        }
    }

    private fun saved(coordinates: Coordinates = paris, name: String? = "Paris", age: Duration = Duration.ofHours(1)) =
        SavedLocation(LocationData(coordinates, name), savedAt = now - age)

    private fun viewModel(
        provider: FakeLocationProvider = FakeLocationProvider(LocationResult.Success(london)),
        repository: FakeLocationRepository = FakeLocationRepository(),
        placeName: String? = "London",
    ) = HomeViewModel(
        provider,
        FakeResolver(placeName),
        repository,
        FakeWeatherRepository(),
        clock,
        DataStorePlacesRepository(InMemoryPreferencesDataStore()),
        FakeAirQualityRepository(),
    )

    private val HomeViewModel.state get() = locationState.value

    // --- No saved location ---

    @Test
    fun `starts in checking state`() {
        assertEquals(LocationStatus.Checking, viewModel().state.status)
    }

    @Test
    fun `without permission or saved location the explanation is shown`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Success(london))
        val vm = viewModel(provider)

        vm.onPermissionChecked(granted = false)
        advanceUntilIdle()

        assertEquals(LocationStatus.PermissionRequired(PermissionDenial.None), vm.state.status)
        assertEquals(0, provider.requests)
    }

    @Test
    fun `with permission the location is found, named and saved`() = runTest {
        val repository = FakeLocationRepository()
        val vm = viewModel(repository = repository)

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(LocationStatus.Idle, vm.state.status)
        assertEquals(LocationSource.Current, vm.state.source)
        assertEquals(londonData, vm.state.location!!.location)
        assertEquals(now, vm.state.location!!.savedAt)
        assertEquals(londonData, repository.savedLocation.value!!.location)
    }

    @Test
    fun `missing place name still yields a location`() = runTest {
        val vm = viewModel(placeName = null)

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(LocationData(london, null), vm.state.location!!.location)
    }

    @Test
    fun `a failed save still shows the location for this session`() = runTest {
        val vm = viewModel(repository = FakeLocationRepository(failOnSave = true))

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(londonData, vm.state.location!!.location)
        assertEquals(LocationStatus.Idle, vm.state.status)
    }

    @Test
    fun `first denial allows asking again, second points to settings`() = runTest {
        val vm = viewModel()

        vm.onPermissionResult(granted = false, canAskAgain = true)
        advanceUntilIdle()
        assertEquals(LocationStatus.PermissionRequired(PermissionDenial.Denied), vm.state.status)

        vm.onPermissionResult(granted = false, canAskAgain = false)
        advanceUntilIdle()
        assertEquals(LocationStatus.PermissionRequired(PermissionDenial.Permanent), vm.state.status)
    }

    @Test
    fun `resuming while still denied keeps the denial reason`() = runTest {
        val vm = viewModel()
        vm.onPermissionResult(granted = false, canAskAgain = false)

        vm.onPermissionChecked(granted = false)
        advanceUntilIdle()

        assertEquals(LocationStatus.PermissionRequired(PermissionDenial.Permanent), vm.state.status)
    }

    @Test
    fun `returning from settings with permission granted starts locating`() = runTest {
        val vm = viewModel()
        vm.onPermissionResult(granted = false, canAskAgain = false)

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(londonData, vm.state.location!!.location)
    }

    @Test
    fun `location services off is reported, and retried on resume`() = runTest {
        val provider = FakeLocationProvider(LocationResult.LocationDisabled)
        val vm = viewModel(provider)
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(LocationStatus.Failed(LocationFailure.ServicesDisabled), vm.state.status)

        provider.result = LocationResult.Success(london)
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(londonData, vm.state.location!!.location)
    }

    @Test
    fun `unavailable location is retried only on request`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Unavailable)
        val vm = viewModel(provider)
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(LocationStatus.Failed(LocationFailure.Unavailable), vm.state.status)

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(1, provider.requests)

        provider.result = LocationResult.Success(london)
        vm.updateLocation()
        advanceUntilIdle()
        assertEquals(londonData, vm.state.location!!.location)
    }

    @Test
    fun `repeated triggers while locating make only one request`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Success(london))
        val vm = viewModel(provider)

        vm.onPermissionResult(granted = true, canAskAgain = true)
        vm.updateLocation()
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(1, provider.requests)
    }

    @Test
    fun `resuming after location was found does not request it again`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Success(london))
        val vm = viewModel(provider)
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        vm.onPermissionChecked(granted = true)
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(1, provider.requests)
    }

    // --- With a saved location ---

    @Test
    fun `recent saved location is shown without a new lookup`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Success(london))
        val vm = viewModel(provider, FakeLocationRepository(saved(age = Duration.ofHours(2))))

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(0, provider.requests)
        assertEquals(LocationStatus.Idle, vm.state.status)
        assertEquals(LocationSource.Saved, vm.state.source)
        assertEquals("Paris", vm.state.location!!.location.name)
    }

    @Test
    fun `saved location works without permission and without nagging`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Success(london))
        val vm = viewModel(provider, FakeLocationRepository(saved()))

        vm.onPermissionChecked(granted = false)
        advanceUntilIdle()

        assertEquals(LocationStatus.Idle, vm.state.status)
        assertEquals("Paris", vm.state.location!!.location.name)
        assertEquals(0, provider.requests)
    }

    @Test
    fun `stale saved location is refreshed on open`() = runTest {
        val repository = FakeLocationRepository(saved(age = HomeViewModel.STALE_AFTER.plusMinutes(1)))
        val vm = viewModel(repository = repository)

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(LocationSource.Current, vm.state.source)
        assertEquals(londonData, vm.state.location!!.location)
        assertEquals(1, repository.saves)
    }

    @Test
    fun `failed background refresh keeps saved location silently`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Unavailable)
        val vm = viewModel(provider, FakeLocationRepository(saved(age = Duration.ofDays(3))))

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(1, provider.requests)
        assertEquals(LocationStatus.Idle, vm.state.status)
        assertEquals("Paris", vm.state.location!!.location.name)
    }

    @Test
    fun `manual update failure keeps saved location and explains why`() = runTest {
        val provider = FakeLocationProvider(LocationResult.LocationDisabled)
        val vm = viewModel(provider, FakeLocationRepository(saved()))
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        vm.updateLocation()
        advanceUntilIdle()

        assertEquals(LocationStatus.Failed(LocationFailure.ServicesDisabled), vm.state.status)
        assertEquals("Paris", vm.state.location!!.location.name)
        assertEquals(LocationSource.Saved, vm.state.source)
    }

    @Test
    fun `manual update replaces the saved location`() = runTest {
        val repository = FakeLocationRepository(saved())
        val vm = viewModel(repository = repository)
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        vm.updateLocation()
        advanceUntilIdle()

        assertEquals(londonData, vm.state.location!!.location)
        assertEquals(londonData, repository.savedLocation.value!!.location)
    }

    @Test
    fun `denying permission during update keeps saved location`() = runTest {
        val vm = viewModel(repository = FakeLocationRepository(saved()))
        vm.onPermissionChecked(granted = false)
        advanceUntilIdle()

        vm.onPermissionResult(granted = false, canAskAgain = false)
        advanceUntilIdle()

        assertEquals(LocationStatus.PermissionRequired(PermissionDenial.Permanent), vm.state.status)
        assertEquals("Paris", vm.state.location!!.location.name)
    }

    @Test
    fun `no location is shown before anything is known`() {
        assertNull(viewModel().state.location)
    }
}
