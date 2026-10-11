package com.ah0890.dailyweather.presentation.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** The only location permission the app uses. */
const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

/** Opens a system settings screen; [withPackage] targets this app's own page. */
fun Context.openSettings(action: String, withPackage: Boolean = false) {
    val intent = Intent(action).apply {
        if (withPackage) data = Uri.fromParts("package", packageName, null)
    }
    // Some devices lack a settings screen for an action; failing quietly is better than crashing.
    runCatching { startActivity(intent) }
}

/** Opens a web page in the user's browser, ignoring devices without one. */
fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
