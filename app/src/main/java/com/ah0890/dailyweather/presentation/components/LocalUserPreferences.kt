package com.ah0890.dailyweather.presentation.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.ah0890.dailyweather.domain.model.UserPreferences

/**
 * The user's display preferences (units, commute times…) for any composable that formats data.
 * Provided once at the top of the UI so screens don't each need to load them.
 */
val LocalUserPreferences = staticCompositionLocalOf { UserPreferences() }
