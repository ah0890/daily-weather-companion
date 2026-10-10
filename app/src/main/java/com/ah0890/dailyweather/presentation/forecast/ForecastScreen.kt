package com.ah0890.dailyweather.presentation.forecast

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.presentation.components.PlaceholderScreen

@Composable
fun ForecastScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = R.string.forecast_title,
        body = R.string.forecast_empty,
        modifier = modifier,
    )
}
