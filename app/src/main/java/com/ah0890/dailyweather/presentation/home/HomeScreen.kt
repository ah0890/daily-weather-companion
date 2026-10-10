package com.ah0890.dailyweather.presentation.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.ui.theme.DailyWeatherTheme
import com.ah0890.dailyweather.ui.theme.LocalDarkTheme
import com.ah0890.dailyweather.ui.theme.WeatherBackdrop
import com.ah0890.dailyweather.ui.theme.WeatherMood
import java.time.LocalTime

private const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

/** Stateful entry point: wires the ViewModel, the permission dialog and system settings. */
@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
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
        onPauseOrDispose { }
    }

    HomeScreen(
        locationState = locationState,
        onRequestPermission = { permissionLauncher.launch(LOCATION_PERMISSION) },
        onOpenAppSettings = { context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true) },
        onOpenLocationSettings = { context.openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
        onRetry = viewModel::retry,
    )
}

@Composable
fun HomeScreen(
    locationState: LocationUiState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greeting = remember { greetingFor(LocalTime.now().hour) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(greeting),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            LocationLabel(locationState)
        }

        when (locationState) {
            LocationUiState.Checking -> Unit
            is LocationUiState.PermissionRequired -> PermissionCard(
                denial = locationState.denial,
                onRequestPermission = onRequestPermission,
                onOpenAppSettings = onOpenAppSettings,
            )
            LocationUiState.Locating -> LocatingCard()
            is LocationUiState.Located -> LocatedCard(locationState.location)
            is LocationUiState.Failed -> when (locationState.reason) {
                LocationFailure.ServicesDisabled -> MessageCard(
                    title = R.string.location_disabled_title,
                    body = R.string.location_disabled_body,
                    primaryAction = R.string.location_open_location_settings to onOpenLocationSettings,
                    secondaryAction = R.string.location_try_again to onRetry,
                )
                LocationFailure.Unavailable -> MessageCard(
                    title = R.string.location_unavailable_title,
                    body = R.string.location_unavailable_body,
                    primaryAction = R.string.location_try_again to onRetry,
                )
            }
        }
    }
}

@Composable
private fun LocationLabel(state: LocationUiState) {
    val text = when (state) {
        is LocationUiState.Located -> state.location.name ?: stringResource(R.string.location_current)
        // While checking or locating, the card below already says what is happening.
        LocationUiState.Checking, LocationUiState.Locating -> return
        is LocationUiState.PermissionRequired, is LocationUiState.Failed -> stringResource(R.string.location_not_set)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionCard(
    denial: PermissionDenial,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    when (denial) {
        PermissionDenial.None -> MessageCard(
            title = R.string.permission_title,
            body = R.string.permission_body,
            primaryAction = R.string.permission_allow to onRequestPermission,
        )
        PermissionDenial.Denied -> MessageCard(
            title = R.string.permission_denied_title,
            body = R.string.permission_denied_body,
            primaryAction = R.string.location_try_again to onRequestPermission,
        )
        PermissionDenial.Permanent -> MessageCard(
            title = R.string.permission_permanent_title,
            body = R.string.permission_permanent_body,
            primaryAction = R.string.permission_open_settings to onOpenAppSettings,
        )
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

@Composable
private fun LocatedCard(location: LocationData) {
    val backdrop = WeatherBackdrop.forMood(WeatherMood.Clear, LocalDarkTheme.current)
    val name = location.name ?: stringResource(R.string.location_current)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(backdrop.brush)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.home_located_title, name),
            style = MaterialTheme.typography.titleLarge,
            color = backdrop.content,
        )
        Text(
            text = stringResource(R.string.home_located_body),
            style = MaterialTheme.typography.bodyLarge,
            color = backdrop.content,
        )
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

private fun Context.openSettings(action: String, withPackage: Boolean = false) {
    val intent = Intent(action).apply {
        if (withPackage) data = Uri.fromParts("package", packageName, null)
    }
    // Some devices lack a settings screen for an action; failing quietly is better than crashing.
    runCatching { startActivity(intent) }
}

@Preview(showBackground = true)
@Composable
private fun PermissionPreview() {
    DailyWeatherTheme {
        HomeScreen(LocationUiState.PermissionRequired(PermissionDenial.None), {}, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LocatedPreview() {
    DailyWeatherTheme {
        HomeScreen(
            LocationUiState.Located(LocationData(Coordinates(51.51, -0.13), "London")),
            {}, {}, {}, {},
        )
    }
}
