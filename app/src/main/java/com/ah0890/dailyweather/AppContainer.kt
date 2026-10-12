package com.ah0890.dailyweather

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ah0890.dailyweather.data.local.DataStoreLocationRepository
import com.ah0890.dailyweather.data.local.DataStorePlacesRepository
import com.ah0890.dailyweather.data.local.DataStorePreferencesRepository
import com.ah0890.dailyweather.data.local.FileWeatherCache
import com.ah0890.dailyweather.data.remote.openmeteo.OpenMeteoDataSource
import com.ah0890.dailyweather.data.remote.openmeteo.OpenMeteoPlaceSearch
import com.ah0890.dailyweather.data.repository.DefaultWeatherRepository
import com.ah0890.dailyweather.domain.repository.LocationRepository
import com.ah0890.dailyweather.domain.repository.PlaceSearchRepository
import com.ah0890.dailyweather.domain.repository.PlacesRepository
import com.ah0890.dailyweather.domain.repository.PreferencesRepository
import com.ah0890.dailyweather.domain.repository.WeatherRepository
import com.ah0890.dailyweather.location.AndroidLocationProvider
import com.ah0890.dailyweather.location.GeocoderPlaceNameResolver
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.PlaceNameResolver
import com.ah0890.dailyweather.notifications.AndroidDailySummaryNotifier
import com.ah0890.dailyweather.notifications.DailySummaryNotifier
import com.ah0890.dailyweather.notifications.NotificationScheduler
import com.ah0890.dailyweather.notifications.WorkManagerNotificationScheduler
import java.io.File

/** App-wide dependencies, created lazily. A hand-written container keeps the build simple. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val locationProvider: LocationProvider by lazy { AndroidLocationProvider(appContext) }
    val placeNameResolver: PlaceNameResolver by lazy { GeocoderPlaceNameResolver(appContext) }
    val weatherRepository: WeatherRepository by lazy {
        DefaultWeatherRepository(
            remote = OpenMeteoDataSource.create(),
            cache = FileWeatherCache(
                directory = File(appContext.filesDir, "weather_cache"),
                legacyFile = File(appContext.filesDir, "weather_cache.json"),
            ),
        )
    }

    val notificationScheduler: NotificationScheduler by lazy { WorkManagerNotificationScheduler(appContext) }
    val dailySummaryNotifier: DailySummaryNotifier by lazy { AndroidDailySummaryNotifier(appContext) }

    // DataStore requires a single instance per file for the whole process.
    val preferencesRepository: PreferencesRepository by lazy {
        DataStorePreferencesRepository(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("user_preferences") },
            ),
        )
    }

    val placesRepository: PlacesRepository by lazy {
        DataStorePlacesRepository(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("places") },
            ),
        )
    }

    val placeSearch: PlaceSearchRepository by lazy { OpenMeteoPlaceSearch.create() }

    val locationRepository: LocationRepository by lazy {
        DataStoreLocationRepository(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("saved_location") },
            ),
        )
    }
}
