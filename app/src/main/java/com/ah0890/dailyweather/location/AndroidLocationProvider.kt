package com.ah0890.dailyweather.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.ah0890.dailyweather.domain.model.Coordinates
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Requests a single location fix from the platform [LocationManager], falling back to a recent
 * last-known location. Uses only Android framework APIs, so Google Play services is not needed.
 */
class AndroidLocationProvider(private val context: Context) : LocationProvider {

    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)

    override suspend fun currentLocation(): LocationResult {
        val manager = manager ?: return LocationResult.Unavailable
        if (!hasPermission()) return LocationResult.PermissionMissing
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationResult.LocationDisabled

        // Providers are tried in order: with approximate permission the fused provider runs in
        // low-power mode and may get nothing where network location is unavailable, so GPS
        // (Android 12+, coarsened by the system) is the fallback.
        val providers = usableProviders(manager)
        val location = providers.firstNotNullOfOrNull { provider ->
            withTimeoutOrNull(timeoutFor(provider)) { requestFix(manager, provider) }
        } ?: recentLastKnown(manager, providers)

        return location
            ?.let { LocationResult.Success(Coordinates(it.latitude, it.longitude)) }
            ?: LocationResult.Unavailable
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Before Android 12 the GPS provider requires fine location, so only network-based
     * providers are usable with approximate permission there.
     */
    private fun usableProviders(manager: LocationManager): List<String> {
        val preferred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        }
        val enabled = manager.getProviders(true)
        return preferred.filter { it in enabled }
    }

    @SuppressLint("MissingPermission") // Checked in currentLocation().
    private suspend fun requestFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location -> if (continuation.isActive) continuation.resume(location) }
            } catch (_: SecurityException) {
                continuation.resume(null)
            } catch (_: IllegalArgumentException) {
                continuation.resume(null)
            }
        }

    @SuppressLint("MissingPermission") // Checked in currentLocation().
    private fun recentLastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.ageMillis() <= MAX_LAST_KNOWN_AGE.inWholeMilliseconds }
            .maxByOrNull { it.elapsedRealtimeNanos }

    private fun Location.ageMillis(): Long =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000

    private fun timeoutFor(provider: String) =
        if (provider == LocationManager.GPS_PROVIDER) GPS_FIX_TIMEOUT else FIX_TIMEOUT

    private companion object {
        val FIX_TIMEOUT = 10.seconds
        val GPS_FIX_TIMEOUT = 20.seconds
        val MAX_LAST_KNOWN_AGE = 3.hours
    }
}
