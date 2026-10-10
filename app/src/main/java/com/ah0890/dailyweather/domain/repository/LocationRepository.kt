package com.ah0890.dailyweather.domain.repository

import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow

/** Stores the single location the app shows weather for. */
interface LocationRepository {

    /** The saved location, or null if none has been saved. Emits again whenever it changes. */
    val savedLocation: Flow<SavedLocation?>

    /** Replaces the saved location. Coordinates are stored at reduced (~1 km) precision. */
    suspend fun save(location: LocationData)
}
