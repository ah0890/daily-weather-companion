package com.ah0890.dailyweather.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.domain.usecase.AlertLevel
import com.ah0890.dailyweather.domain.usecase.RainSoon
import com.ah0890.dailyweather.domain.usecase.WeatherAlert
import com.ah0890.dailyweather.presentation.components.LocalUserPreferences
import com.ah0890.dailyweather.presentation.weather.describe
import com.ah0890.dailyweather.presentation.weather.rememberHourFormatter
import com.ah0890.dailyweather.ui.theme.cardContainer

/**
 * Hazards in the next 24 hours. Severe ones get a stronger colour and come first; advisories
 * (poor visibility, a very hot afternoon) are shown in a calmer card. Nothing is shown when the
 * forecast meets no threshold.
 */
@Composable
fun WeatherAlertsCard(alerts: List<WeatherAlert>) {
    if (alerts.isEmpty()) return
    val severe = alerts.any { it.level == AlertLevel.Severe }
    val ordered = alerts.sortedBy { if (it.level == AlertLevel.Severe) 0 else 1 }
    val res = LocalResources.current
    val hours = rememberHourFormatter()
    val unit = LocalUserPreferences.current.temperatureUnit
    val (container, content) = with(MaterialTheme.colorScheme) {
        if (severe) errorContainer to onErrorContainer else cardContainer to onSurface
    }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(if (severe) R.string.alerts_title_severe else R.string.alerts_title_advisory),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            ordered.forEach { Text(text = it.describe(res, hours, unit), style = MaterialTheme.typography.bodyLarge) }
            if (alerts.any { it.hazard == WeatherHazard.Heat || it.hazard == WeatherHazard.ExtremeHeat }) {
                Text(text = stringResource(R.string.tip_water), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = stringResource(R.string.alerts_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.75f),
            )
        }
    }
}

/** One calm line about rain in the next hour or two. */
@Composable
fun RainSoonNote(rainSoon: RainSoon) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.cardContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = rainSoon.describe(LocalResources.current),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
