package com.ah0890.dailyweather.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ah0890.dailyweather.presentation.forecast.ForecastRoute
import com.ah0890.dailyweather.presentation.home.HomeRoute
import com.ah0890.dailyweather.presentation.home.HomeViewModel
import com.ah0890.dailyweather.presentation.settings.SettingsScreen

@Composable
fun DailyWeatherApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Created here (activity scope) so Today and 7 days share one location + forecast state.
    val weatherViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        // The label already names the destination, so the icon stays silent for screen readers.
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> { HomeRoute(weatherViewModel) }
            composable<ForecastRoute> { ForecastRoute(weatherViewModel) }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}
