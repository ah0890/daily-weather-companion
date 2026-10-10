package com.ah0890.dailyweather

import android.content.Context
import com.ah0890.dailyweather.location.AndroidLocationProvider
import com.ah0890.dailyweather.location.GeocoderPlaceNameResolver
import com.ah0890.dailyweather.location.LocationProvider
import com.ah0890.dailyweather.location.PlaceNameResolver

/** App-wide dependencies, created lazily. A hand-written container keeps the build simple. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val locationProvider: LocationProvider by lazy { AndroidLocationProvider(appContext) }
    val placeNameResolver: PlaceNameResolver by lazy { GeocoderPlaceNameResolver(appContext) }
}
