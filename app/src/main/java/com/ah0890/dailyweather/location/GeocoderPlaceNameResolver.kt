package com.ah0890.dailyweather.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.ah0890.dailyweather.domain.model.Coordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/** Resolves place names with the platform [Geocoder], using approximate coordinates only. */
class GeocoderPlaceNameResolver(private val context: Context) : PlaceNameResolver {

    override suspend fun placeName(coordinates: Coordinates): String? {
        if (!Geocoder.isPresent()) return null
        val approximate = coordinates.approximate()
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = withTimeoutOrNull(TIMEOUT) { lookup(geocoder, approximate) }
        return address?.let { it.locality ?: it.subAdminArea ?: it.adminArea ?: it.countryName }
    }

    private suspend fun lookup(geocoder: Geocoder, coordinates: Coordinates): Address? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        coordinates.latitude,
                        coordinates.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1)?.firstOrNull()
                }
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        val TIMEOUT = 10.seconds
    }
}
