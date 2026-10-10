package com.ah0890.dailyweather.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.ui.theme.DailyWeatherTheme
import com.ah0890.dailyweather.ui.theme.LocalDarkTheme
import com.ah0890.dailyweather.ui.theme.WeatherBackdrop
import com.ah0890.dailyweather.ui.theme.WeatherMood
import java.time.LocalTime

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val greeting = remember { greetingFor(LocalTime.now().hour) }
    val backdrop = WeatherBackdrop.forMood(WeatherMood.Clear, LocalDarkTheme.current)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(greeting),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(backdrop.brush)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_setup_title),
                style = MaterialTheme.typography.titleLarge,
                color = backdrop.content,
            )
            Text(
                text = stringResource(R.string.home_setup_body),
                style = MaterialTheme.typography.bodyLarge,
                color = backdrop.content,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DailyWeatherTheme { HomeScreen() }
}
