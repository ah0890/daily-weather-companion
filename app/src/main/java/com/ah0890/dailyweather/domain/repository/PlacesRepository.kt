package com.ah0890.dailyweather.domain.repository

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.Place
import com.ah0890.dailyweather.domain.model.PlaceSuggestion
import com.ah0890.dailyweather.domain.model.PlaceType
import com.ah0890.dailyweather.domain.model.Places
import com.ah0890.dailyweather.domain.model.WeatherError
import kotlinx.coroutines.flow.Flow

/** The user's saved places. Coordinates are stored at reduced (~1 km) precision. */
interface PlacesRepository {

    /** Emits again whenever a place or role changes. */
    val places: Flow<Places>

    /**
     * Saves a new place. The first Home or Office place also becomes the commute home or office.
     * @return the new place, or null when [Places.MAX_PLACES] are already saved.
     */
    suspend fun add(name: String, type: PlaceType, coordinates: Coordinates, placeName: String?): Place?

    suspend fun update(id: String, name: String, type: PlaceType)

    /** Removes the place and any role it had. */
    suspend fun delete(id: String)

    /** Null clears the role. */
    suspend fun setHome(id: String?)

    suspend fun setOffice(id: String?)

    suspend fun setDefault(id: String?)
}

/** Finds places by name, so the office can be added without being there. */
interface PlaceSearchRepository {
    sealed interface Result {
        data class Success(val suggestions: List<PlaceSuggestion>) : Result
        data class Failure(val error: WeatherError) : Result
    }

    /** Never throws for network or data problems. */
    suspend fun search(query: String): Result
}
