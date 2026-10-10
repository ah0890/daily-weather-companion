package com.ah0890.dailyweather.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.presentation.components.PlaceholderScreen

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = R.string.settings_title,
        body = R.string.settings_empty,
        modifier = modifier,
    )
}
