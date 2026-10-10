package com.ah0890.dailyweather.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// A calm sky-blue brand palette with a warm amber accent for sun-related highlights.
// Dynamic (wallpaper) colour is deliberately not used so weather backdrops stay consistent.

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2B5797),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF0A2A5C),
    secondary = Color(0xFF4E6A8E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE6F5),
    onSecondaryContainer = Color(0xFF13253D),
    tertiary = Color(0xFF8F5400),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB5),
    onTertiaryContainer = Color(0xFF311A00),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF141B26),
    surface = Color(0xFFF4F7FB),
    onSurface = Color(0xFF141B26),
    surfaceVariant = Color(0xFFE1E7F0),
    onSurfaceVariant = Color(0xFF434B58),
    outline = Color(0xFF737B88),
    outlineVariant = Color(0xFFC3CAD5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF2F8),
    surfaceContainer = Color(0xFFE8EDF5),
    surfaceContainerHigh = Color(0xFFE2E8F1),
    surfaceContainerHighest = Color(0xFFDCE3ED),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF0A2E66),
    primaryContainer = Color(0xFF1F4580),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFFB6C8E3),
    onSecondary = Color(0xFF20324A),
    secondaryContainer = Color(0xFF374962),
    onSecondaryContainer = Color(0xFFDCE6F5),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6B3D00),
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = Color(0xFF0D131E),
    onBackground = Color(0xFFE1E6EF),
    surface = Color(0xFF0D131E),
    onSurface = Color(0xFFE1E6EF),
    surfaceVariant = Color(0xFF3F4754),
    onSurfaceVariant = Color(0xFFC1C8D4),
    outline = Color(0xFF8B93A0),
    outlineVariant = Color(0xFF3F4754),
    surfaceContainerLowest = Color(0xFF080D16),
    surfaceContainerLow = Color(0xFF151C28),
    surfaceContainer = Color(0xFF19212D),
    surfaceContainerHigh = Color(0xFF232B38),
    surfaceContainerHighest = Color(0xFF2E3643),
)
