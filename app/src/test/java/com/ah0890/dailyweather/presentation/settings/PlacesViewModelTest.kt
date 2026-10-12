package com.ah0890.dailyweather.presentation.settings

import com.ah0890.dailyweather.data.local.DataStorePlacesRepository
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.PlaceSuggestion
import com.ah0890.dailyweather.domain.model.PlaceType
import com.ah0890.dailyweather.domain.model.Places
import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.repository.PlaceSearchRepository
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.LocationResult
import com.ah0890.dailyweather.location.PlaceNameResolver
import com.ah0890.dailyweather.testing.InMemoryPreferencesDataStore
import com.ah0890.dailyweather.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val paris = PlaceSuggestion("Paris", "Île-de-France, France", Coordinates(48.85341, 2.3488))

    private class FakeSearch(var result: PlaceSearchRepository.Result) : PlaceSearchRepository {
        override suspend fun search(query: String) = result
    }

    private class FakeProvider(var result: LocationResult) : LocationProvider {
        override suspend fun currentLocation() = result
    }

    private object Names : PlaceNameResolver {
        override suspend fun placeName(coordinates: Coordinates) = "London"
    }

    private val repository = DataStorePlacesRepository(InMemoryPreferencesDataStore())
    private val search = FakeSearch(PlaceSearchRepository.Result.Success(listOf(paris)))
    private val provider = FakeProvider(LocationResult.Success(Coordinates(51.507, -0.127)))
    private val vm by lazy { PlacesViewModel(repository, search, provider, Names) }

    @Test
    fun `search results are shown`() = runTest {
        vm.search("Paris")
        advanceUntilIdle()

        assertEquals(AddPlaceState.Results(listOf(paris)), vm.addState.value)
    }

    @Test
    fun `a failed search says so`() = runTest {
        search.result = PlaceSearchRepository.Result.Failure(WeatherError.NoConnection)

        vm.search("Paris")
        advanceUntilIdle()

        assertEquals(AddPlaceState.SearchFailed(WeatherError.NoConnection), vm.addState.value)
    }

    @Test
    fun `picking a result saves it with the town name`() = runTest {
        vm.addSuggestion("Office", PlaceType.Office, paris)
        advanceUntilIdle()

        assertEquals(AddPlaceState.Added, vm.addState.value)
        val saved = repository.places.first()
        assertEquals("Office", saved.office!!.name)
        assertEquals("Paris", saved.office!!.placeName)
        assertEquals(Coordinates(48.85, 2.35), saved.office!!.coordinates)
    }

    @Test
    fun `the current location can be saved as a place`() = runTest {
        vm.addCurrentLocation("Home", PlaceType.Home)
        advanceUntilIdle()

        assertEquals(AddPlaceState.Added, vm.addState.value)
        assertEquals("London", repository.places.first().home!!.placeName)
    }

    @Test
    fun `no location fix is reported`() = runTest {
        provider.result = LocationResult.Unavailable

        vm.addCurrentLocation("Home", PlaceType.Home)
        advanceUntilIdle()

        assertEquals(AddPlaceState.LocationFailed, vm.addState.value)
    }

    @Test
    fun `a full list is reported`() = runTest {
        repeat(Places.MAX_PLACES) { repository.add("P$it", PlaceType.Other, Coordinates(it.toDouble(), 0.0), null) }

        vm.addSuggestion("Office", PlaceType.Office, paris)
        advanceUntilIdle()

        assertEquals(AddPlaceState.Full, vm.addState.value)
    }

    @Test
    fun `roles, edits and deletes reach the repository`() = runTest {
        val a = repository.add("A", PlaceType.Other, Coordinates(1.0, 1.0), null)!!

        vm.setHome(a.id)
        vm.setDefault(a.id)
        vm.update(a.id, "Flat", PlaceType.Home)
        advanceUntilIdle()
        repository.places.first().let {
            assertEquals("Flat", it.home!!.name)
            assertEquals(a.id, it.defaultId)
        }

        vm.delete(a.id)
        advanceUntilIdle()
        assertEquals(Places(), repository.places.first())
    }
}
