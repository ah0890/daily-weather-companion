package com.ah0890.dailyweather

import android.app.Application

class DailyWeatherApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
