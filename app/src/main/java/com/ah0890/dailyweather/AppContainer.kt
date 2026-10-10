package com.ah0890.dailyweather

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ah0890.dailyweather.data.local.DataStoreLocationRepository
import com.ah0890.dailyweather.domain.repository.LocationRepository
import com.ah0890.dailyweather.location.AndroidLocationProvider
import com.ah0890.dailyweather.location.GeocoderPlaceNameResolver
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.PlaceNameResolver

/** App-wide dependencies, created lazily. A hand-written container keeps the build simple. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val locationProvider: LocationProvider by lazy { AndroidLocationProvider(appContext) }
    val placeNameResolver: PlaceNameResolver by lazy { GeocoderPlaceNameResolver(appContext) }

    // DataStore requires a single instance per file for the whole process.
    val locationRepository: LocationRepository by lazy {
        DataStoreLocationRepository(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("saved_location") },
            ),
        )
    }
}
