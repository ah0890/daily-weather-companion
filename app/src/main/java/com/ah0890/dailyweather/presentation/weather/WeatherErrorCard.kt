package com.ah0890.dailyweather.presentation.weather

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.WeatherError

/** Full-size message for a forecast that couldn't be loaded, with a retry button. */
@Composable
fun WeatherErrorCard(error: WeatherError, onRetry: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(R.string.weather_error_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(error.messageRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.weather_retry)) }
        }
    }
}

@get:StringRes
private val WeatherError.messageRes: Int
    get() = when (this) {
        WeatherError.NoConnection -> R.string.weather_error_no_connection
        WeatherError.Timeout -> R.string.weather_error_timeout
        WeatherError.RateLimited -> R.string.weather_error_rate_limited
        WeatherError.ServiceUnavailable -> R.string.weather_error_service
        WeatherError.InvalidData -> R.string.weather_error_invalid
        WeatherError.Unexpected -> R.string.weather_error_unexpected
    }
