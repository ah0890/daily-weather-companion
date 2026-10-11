package com.ah0890.dailyweather

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.presentation.components.LocalUserPreferences
import com.ah0890.dailyweather.presentation.navigation.DailyWeatherApp
import com.ah0890.dailyweather.ui.theme.DailyWeatherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val preferencesFlow = (application as DailyWeatherApplication).container.preferencesRepository.preferences

        setContent {
            val preferences by preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // Status/navigation bar icons follow the in-app theme, not only the system setting.
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }

            CompositionLocalProvider(LocalUserPreferences provides preferences) {
                DailyWeatherTheme(darkTheme = darkTheme) {
                    DailyWeatherApp()
                }
            }
        }
    }
}
