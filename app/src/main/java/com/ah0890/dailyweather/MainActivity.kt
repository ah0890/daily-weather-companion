package com.ah0890.dailyweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ah0890.dailyweather.presentation.navigation.DailyWeatherApp
import com.ah0890.dailyweather.ui.theme.DailyWeatherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DailyWeatherTheme {
                DailyWeatherApp()
            }
        }
    }
}
