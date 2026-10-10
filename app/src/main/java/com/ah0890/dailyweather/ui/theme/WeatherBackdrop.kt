package com.ah0890.dailyweather.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Broad visual mood used to tint weather surfaces. Mapped from the forecast's condition. */
enum class WeatherMood { Clear, Sunny, Cloudy, Fog, Rain, Storm, Snow }

/**
 * Gradient and matching content colour for a weather surface such as the hero card.
 *
 * Light-theme backdrops are soft pastels with dark text; dark-theme backdrops are deep tones
 * with light text. Both keep text contrast well above WCAG AA so the screen stays readable
 * outdoors in bright sunlight.
 */
@Immutable
data class WeatherBackdrop(
    val top: Color,
    val bottom: Color,
    val content: Color,
) {
    val brush: Brush get() = Brush.verticalGradient(listOf(top, bottom))

    companion object {
        fun forMood(mood: WeatherMood, darkTheme: Boolean): WeatherBackdrop =
            if (darkTheme) dark.getValue(mood) else light.getValue(mood)

        private val light = mapOf(
            WeatherMood.Clear to WeatherBackdrop(Color(0xFFD9ECFF), Color(0xFFB9DAFB), Color(0xFF0B2A4A)),
            WeatherMood.Sunny to WeatherBackdrop(Color(0xFFFFE9C2), Color(0xFFFFCF94), Color(0xFF3A2200)),
            WeatherMood.Cloudy to WeatherBackdrop(Color(0xFFE6EBF2), Color(0xFFCCD5E1), Color(0xFF1C2633)),
            WeatherMood.Fog to WeatherBackdrop(Color(0xFFECEDEF), Color(0xFFD6D9DE), Color(0xFF23272D)),
            WeatherMood.Rain to WeatherBackdrop(Color(0xFFDCE6FF), Color(0xFFB8C9F2), Color(0xFF0E2350)),
            WeatherMood.Storm to WeatherBackdrop(Color(0xFFDCD8F5), Color(0xFFB9B2E6), Color(0xFF1F1648)),
            WeatherMood.Snow to WeatherBackdrop(Color(0xFFF0F5FB), Color(0xFFDCE7F3), Color(0xFF15263A)),
        )

        private val dark = mapOf(
            WeatherMood.Clear to WeatherBackdrop(Color(0xFF14304F), Color(0xFF0F2238), Color(0xFFDDEBFF)),
            WeatherMood.Sunny to WeatherBackdrop(Color(0xFF4A3218), Color(0xFF2E2014), Color(0xFFFFE7C7)),
            WeatherMood.Cloudy to WeatherBackdrop(Color(0xFF29313D), Color(0xFF1B212B), Color(0xFFE1E6EE)),
            WeatherMood.Fog to WeatherBackdrop(Color(0xFF2D3035), Color(0xFF1E2024), Color(0xFFE4E6E9)),
            WeatherMood.Rain to WeatherBackdrop(Color(0xFF1B2D52), Color(0xFF111D36), Color(0xFFDCE6FF)),
            WeatherMood.Storm to WeatherBackdrop(Color(0xFF2B2152), Color(0xFF181233), Color(0xFFE6E0FF)),
            WeatherMood.Snow to WeatherBackdrop(Color(0xFF263547), Color(0xFF18222F), Color(0xFFE8F0FA)),
        )
    }
}
