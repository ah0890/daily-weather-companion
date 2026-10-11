package com.ah0890.dailyweather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Whether the app is currently rendering dark. This follows the in-app theme choice rather
 * than the system setting, so weather surfaces should read it instead of isSystemInDarkTheme().
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Background for content cards. Material's "lowest" container is white in light mode but darker
 * than the background in dark mode, which makes cards sink in; dark mode uses a lighter step instead.
 */
val ColorScheme.cardContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) surfaceContainer else surfaceContainerLowest

@Composable
fun DailyWeatherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
