package com.ah0890.dailyweather.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.PlaceType
import com.ah0890.dailyweather.domain.model.Places
import com.ah0890.dailyweather.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStorePlacesRepositoryTest {

    private val store = InMemoryPreferencesDataStore()
    private var nextId = 0
    private val repository = DataStorePlacesRepository(store) { "id${++nextId}" }

    private val london = Coordinates(51.507351, -0.127758)
    private val paris = Coordinates(48.8566, 2.3522)

    @Test
    fun `nothing saved means no places and no roles`() = runTest {
        assertEquals(Places(), repository.places.first())
    }

    @Test
    fun `added places are stored with rounded coordinates`() = runTest {
        val home = repository.add("  Home  ", PlaceType.Home, london, "London")!!

        assertEquals("Home", home.name)
        assertEquals(Coordinates(51.51, -0.13), home.coordinates)
        assertEquals(listOf(home), repository.places.first().places)
    }

    @Test
    fun `the first home and office become the commute home and office`() = runTest {
        val home = repository.add("Home", PlaceType.Home, london, "London")!!
        val office = repository.add("Office", PlaceType.Office, paris, "Paris")!!
        repository.add("Second office", PlaceType.Office, Coordinates(52.52, 13.4), "Berlin")

        val places = repository.places.first()
        assertEquals(home, places.home)
        assertEquals(office, places.office)
        assertNull(places.default)
    }

    @Test
    fun `editing changes the name and type only`() = runTest {
        val place = repository.add("Mum", PlaceType.Other, london, "London")!!

        repository.update(place.id, "Parents", PlaceType.Parents)

        assertEquals(place.copy(name = "Parents", type = PlaceType.Parents), repository.places.first().places.single())
    }

    @Test
    fun `an empty name falls back to the town`() = runTest {
        assertEquals("London", repository.add("   ", PlaceType.Other, london, "London")!!.name)
        assertEquals("Other", repository.add("", PlaceType.Other, paris, null)!!.name)
    }

    @Test
    fun `deleting a place also clears its roles`() = runTest {
        val home = repository.add("Home", PlaceType.Home, london, "London")!!
        repository.setDefault(home.id)

        repository.delete(home.id)

        assertEquals(Places(), repository.places.first())
    }

    @Test
    fun `roles can be moved and cleared`() = runTest {
        val a = repository.add("A", PlaceType.Other, london, null)!!
        val b = repository.add("B", PlaceType.Other, paris, null)!!

        repository.setHome(a.id)
        repository.setOffice(b.id)
        repository.setDefault(b.id)
        assertEquals(Triple(a.id, b.id, b.id), repository.places.first().let { Triple(it.homeId, it.officeId, it.defaultId) })

        repository.setDefault(null)
        repository.setOffice("does-not-exist")
        val places = repository.places.first()
        assertNull(places.defaultId)
        assertNull(places.officeId)
    }

    @Test
    fun `no more than the maximum number of places`() = runTest {
        repeat(Places.MAX_PLACES) { repository.add("P$it", PlaceType.Other, Coordinates(it.toDouble(), 0.0), null) }

        assertNull(repository.add("One too many", PlaceType.Other, london, null))
        assertEquals(Places.MAX_PLACES, repository.places.first().places.size)
    }

    @Test
    fun `corrupted data is treated as no places`() = runTest {
        store.edit {
            it[stringPreferencesKey("places")] = "[{ broken"
            it[stringPreferencesKey("home_id")] = "id1"
        }

        assertEquals(Places(), repository.places.first())
    }

    @Test
    fun `entries with invalid coordinates are skipped`() = runTest {
        store.edit {
            it[stringPreferencesKey("places")] =
                """[{"id":"a","name":"Bad","type":"Home","latitude":95.0,"longitude":0.0},""" +
                """{"id":"b","name":"Good","type":"Spaceport","latitude":1.0,"longitude":2.0}]"""
        }

        val place = repository.places.first().places.single()
        assertEquals("b", place.id)
        assertEquals(PlaceType.Other, place.type)
        assertTrue(place.placeName == null)
    }
}
