package com.ah0890.dailyweather.presentation.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationManagerCompat
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

/** Whether the app may post notifications (runtime permission on Android 13+ and the app switch). */
fun Context.canPostNotifications(): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(this).areNotificationsEnabled()
}

/** True where the notification permission must be requested at runtime. */
val needsNotificationPermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Shows Android's notification permission dialog; does nothing below Android 13, where none exists. */
fun ActivityResultLauncher<String>.requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launch(Manifest.permission.POST_NOTIFICATIONS)
}

fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    runCatching { startActivity(intent) }
}

/** Opens a web page in the user's browser, ignoring devices without one. */
fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
