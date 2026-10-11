package com.ah0890.dailyweather.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.ah0890.dailyweather.R
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object ForecastRoute

@Serializable
data object SettingsRoute

/** Opened from Settings; the Settings tab stays selected. */
@Serializable
data object PrivacyRoute

enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Today(TodayRoute, R.string.nav_today, Icons.Rounded.Home),
    Forecast(ForecastRoute, R.string.nav_forecast, Icons.Rounded.DateRange),
    Settings(SettingsRoute, R.string.nav_settings, Icons.Rounded.Settings),
}
