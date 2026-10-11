package com.ah0890.dailyweather.presentation.home

import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.presentation.components.LOCATION_PERMISSION
import com.ah0890.dailyweather.presentation.components.RelativeAge
import com.ah0890.dailyweather.presentation.components.hasLocationPermission
import com.ah0890.dailyweather.presentation.components.openSettings
import com.ah0890.dailyweather.presentation.components.text
import com.ah0890.dailyweather.ui.theme.DailyWeatherTheme
import java.time.Instant
import java.time.LocalTime

private val ScreenPadding = 20.dp

/** Stateful entry point: wires the ViewModel, the permission dialog and system settings. */
@Composable
fun HomeRoute(viewModel: HomeViewModel) {
    val state by viewModel.locationState.collectAsStateWithLifecycle()
    val weather by viewModel.weatherState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val canAskAgain = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, LOCATION_PERMISSION)
        viewModel.onPermissionResult(granted, canAskAgain)
    }

    // Re-checked on every resume so returning from system settings is picked up.
    LifecycleResumeEffect(viewModel) {
        viewModel.onPermissionChecked(context.hasLocationPermission())
        viewModel.refreshWeatherIfStale()
        onPauseOrDispose { }
    }

    HomeScreen(
        state = state,
        weather = weather,
        actions = HomeActions(
            requestPermission = { permissionLauncher.launch(LOCATION_PERMISSION) },
            updateLocation = {
                if (context.hasLocationPermission()) {
                    viewModel.updateLocation()
                } else {
                    permissionLauncher.launch(LOCATION_PERMISSION)
                }
            },
            openAppSettings = { context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true) },
            openLocationSettings = { context.openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
            refreshWeather = viewModel::refreshWeather,
        ),
    )
}

/** User actions the Today screen can trigger. */
class HomeActions(
    val requestPermission: () -> Unit = {},
    val updateLocation: () -> Unit = {},
    val openAppSettings: () -> Unit = {},
    val openLocationSettings: () -> Unit = {},
    val refreshWeather: () -> Unit = {},
)

@Composable
fun HomeScreen(
    state: LocationUiState,
    weather: WeatherUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val greeting = remember { greetingFor(LocalTime.now().hour) }

    PullToRefreshBox(
        isRefreshing = (weather as? WeatherUiState.Loaded)?.isRefreshing == true,
        onRefresh = actions.refreshWeather,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(greeting),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                LocationHeader(state, onUpdateLocation = actions.updateLocation)
            }

            val location = state.location
            if (location != null) {
                SavedLocationNotice(state.status, actions)
                CurrentWeatherSection(weather, edgePadding = ScreenPadding, onRetry = actions.refreshWeather)
            } else {
                NoLocationContent(state.status, actions)
            }
        }
    }
}

@Composable
private fun LocationHeader(state: LocationUiState, onUpdateLocation: () -> Unit) {
    val location = state.location
    if (location == null) {
        // While checking or locating, the card below already says what is happening.
        if (state.status is LocationStatus.PermissionRequired || state.status is LocationStatus.Failed) {
            LocationName(stringResource(R.string.location_not_set))
        }
        return
    }

    val sourceLabel = stringResource(
        if (state.source == LocationSource.Current) R.string.location_current else R.string.location_saved,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LocationName(location.location.name ?: sourceLabel)
            val detail = if (state.status == LocationStatus.Locating) {
                stringResource(R.string.location_updating)
            } else {
                val age = RelativeAge.between(location.savedAt, Instant.now()).text()
                stringResource(R.string.location_source_with_age, sourceLabel, age)
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 22.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        // Fixed-size slot so the layout doesn't jump when the button turns into a spinner.
        Box(modifier = Modifier.size(width = 48.dp, height = 48.dp), contentAlignment = Alignment.Center) {
            if (state.status == LocationStatus.Locating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onUpdateLocation) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.location_update),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationName(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Full-size cards shown before any location is known. */
@Composable
private fun NoLocationContent(status: LocationStatus, actions: HomeActions) {
    when (status) {
        LocationStatus.Checking, LocationStatus.Idle -> Unit
        LocationStatus.Locating -> LocatingCard()
        is LocationStatus.PermissionRequired -> when (status.denial) {
            PermissionDenial.None -> MessageCard(
                title = R.string.permission_title,
                body = R.string.permission_body,
                primaryAction = R.string.permission_allow to actions.requestPermission,
            )
            PermissionDenial.Denied -> MessageCard(
                title = R.string.permission_denied_title,
                body = R.string.permission_denied_body,
                primaryAction = R.string.location_try_again to actions.requestPermission,
            )
            PermissionDenial.Permanent -> MessageCard(
                title = R.string.permission_permanent_title,
                body = R.string.permission_permanent_body,
                primaryAction = R.string.permission_open_settings to actions.openAppSettings,
            )
        }
        is LocationStatus.Failed -> when (status.reason) {
            LocationFailure.ServicesDisabled -> MessageCard(
                title = R.string.location_disabled_title,
                body = R.string.location_disabled_body,
                primaryAction = R.string.location_open_location_settings to actions.openLocationSettings,
                secondaryAction = R.string.location_try_again to actions.updateLocation,
            )
            LocationFailure.Unavailable -> MessageCard(
                title = R.string.location_unavailable_title,
                body = R.string.location_unavailable_body,
                primaryAction = R.string.location_try_again to actions.updateLocation,
            )
        }
    }
}

/** A small, non-blocking note shown when the saved location couldn't be refreshed. */
@Composable
private fun SavedLocationNotice(status: LocationStatus, actions: HomeActions) {
    val (message, action) = when (status) {
        is LocationStatus.PermissionRequired -> R.string.saved_notice_permission to
            if (status.denial == PermissionDenial.Permanent) {
                R.string.permission_open_settings to actions.openAppSettings
            } else {
                R.string.action_allow to actions.requestPermission
            }
        is LocationStatus.Failed -> when (status.reason) {
            LocationFailure.ServicesDisabled ->
                R.string.saved_notice_disabled to (R.string.location_open_location_settings to actions.openLocationSettings)
            LocationFailure.Unavailable ->
                R.string.saved_notice_unavailable to (R.string.location_try_again to actions.updateLocation)
        }
        LocationStatus.Checking, LocationStatus.Idle, LocationStatus.Locating -> return
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = action.second) { Text(stringResource(action.first)) }
        }
    }
}

@Composable
private fun MessageCard(
    @StringRes title: Int,
    @StringRes body: Int,
    primaryAction: Pair<Int, () -> Unit>,
    secondaryAction: Pair<Int, () -> Unit>? = null,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = primaryAction.second) { Text(stringResource(primaryAction.first)) }
                secondaryAction?.let { (label, onClick) ->
                    TextButton(onClick = onClick) { Text(stringResource(label)) }
                }
            }
        }
    }
}

@Composable
private fun LocatingCard() {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(
                text = stringResource(R.string.location_finding),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}


private val previewLocation = SavedLocation(
    LocationData(Coordinates(51.51, -0.13), "London"),
    savedAt = Instant.now().minusSeconds(3 * 3600),
)

@Preview(showBackground = true)
@Composable
private fun PermissionPreview() {
    DailyWeatherTheme {
        HomeScreen(
            LocationUiState(status = LocationStatus.PermissionRequired(PermissionDenial.None)),
            WeatherUiState.Idle,
            HomeActions(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingWeatherPreview() {
    DailyWeatherTheme {
        HomeScreen(
            LocationUiState(previewLocation, LocationSource.Saved, LocationStatus.Idle),
            WeatherUiState.Loading,
            HomeActions(),
        )
    }
}
