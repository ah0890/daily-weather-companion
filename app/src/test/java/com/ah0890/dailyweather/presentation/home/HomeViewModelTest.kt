package com.ah0890.dailyweather.presentation.home

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.LocationResult
import com.ah0890.dailyweather.location.PlaceNameResolver
import com.ah0890.dailyweather.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val london = Coordinates(51.51, -0.13)

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

    private fun viewModel(
        provider: FakeLocationProvider = FakeLocationProvider(LocationResult.Success(london)),
        placeName: String? = "London",
    ) = HomeViewModel(provider, FakeResolver(placeName))

    @Test
    fun `starts in checking state`() {
        assertEquals(LocationUiState.Checking, viewModel().locationState.value)
    }

    @Test
    fun `without permission the explanation is shown and no location is requested`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Success(london))
        val vm = viewModel(provider)

        vm.onPermissionChecked(granted = false)
        advanceUntilIdle()

        assertEquals(LocationUiState.PermissionRequired(PermissionDenial.None), vm.locationState.value)
        assertEquals(0, provider.requests)
    }

    @Test
    fun `with permission the location is found and named`() = runTest {
        val vm = viewModel()

        vm.onPermissionChecked(granted = true)
        assertEquals(LocationUiState.Locating, vm.locationState.value)
        advanceUntilIdle()

        assertEquals(LocationUiState.Located(LocationData(london, "London")), vm.locationState.value)
    }

    @Test
    fun `missing place name still yields a location`() = runTest {
        val vm = viewModel(placeName = null)

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(LocationUiState.Located(LocationData(london, null)), vm.locationState.value)
    }

    @Test
    fun `granting permission from the dialog starts locating`() = runTest {
        val vm = viewModel()
        vm.onPermissionChecked(granted = false)

        vm.onPermissionResult(granted = true, canAskAgain = false)
        advanceUntilIdle()

        assertEquals(LocationUiState.Located(LocationData(london, "London")), vm.locationState.value)
    }

    @Test
    fun `first denial allows asking again`() {
        val vm = viewModel()

        vm.onPermissionResult(granted = false, canAskAgain = true)

        assertEquals(LocationUiState.PermissionRequired(PermissionDenial.Denied), vm.locationState.value)
    }

    @Test
    fun `permanent denial points to settings`() {
        val vm = viewModel()

        vm.onPermissionResult(granted = false, canAskAgain = false)

        assertEquals(LocationUiState.PermissionRequired(PermissionDenial.Permanent), vm.locationState.value)
    }

    @Test
    fun `resuming while still denied keeps the denial reason`() {
        val vm = viewModel()
        vm.onPermissionResult(granted = false, canAskAgain = false)

        vm.onPermissionChecked(granted = false)

        assertEquals(LocationUiState.PermissionRequired(PermissionDenial.Permanent), vm.locationState.value)
    }

    @Test
    fun `returning from settings with permission granted starts locating`() = runTest {
        val vm = viewModel()
        vm.onPermissionResult(granted = false, canAskAgain = false)

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(LocationUiState.Located(LocationData(london, "London")), vm.locationState.value)
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

    @Test
    fun `repeated triggers while locating make only one request`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Success(london))
        val vm = viewModel(provider)

        vm.onPermissionChecked(granted = true)
        vm.retry()
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        assertEquals(1, provider.requests)
    }

    @Test
    fun `location services off is reported, and retried on resume`() = runTest {
        val provider = FakeLocationProvider(LocationResult.LocationDisabled)
        val vm = viewModel(provider)
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(LocationUiState.Failed(LocationFailure.ServicesDisabled), vm.locationState.value)

        provider.result = LocationResult.Success(london)
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(LocationUiState.Located(LocationData(london, "London")), vm.locationState.value)
    }

    @Test
    fun `unavailable location is not retried automatically, only on request`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Unavailable)
        val vm = viewModel(provider)
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(LocationUiState.Failed(LocationFailure.Unavailable), vm.locationState.value)

        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(1, provider.requests)

        provider.result = LocationResult.Success(london)
        vm.retry()
        advanceUntilIdle()
        assertEquals(LocationUiState.Located(LocationData(london, "London")), vm.locationState.value)
    }

    @Test
    fun `permission revoked mid-session is detected`() = runTest {
        val vm = viewModel(FakeLocationProvider(LocationResult.PermissionMissing))

        vm.retry()
        advanceUntilIdle()

        assertEquals(LocationUiState.PermissionRequired(PermissionDenial.None), vm.locationState.value)
    }
}
