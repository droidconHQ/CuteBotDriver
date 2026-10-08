package com.example.cutebotandroidsample.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The controller is a single-purpose race instrument used on a show floor under bright
 * lights, so it commits to one high-contrast dark scheme rather than following the system
 * theme or Material You — the colours carry meaning (amber = signalling, red = stop) and
 * must not be recoloured by a wallpaper.
 */
private val RaceColorScheme = darkColorScheme(
    primary = TrackMagenta,
    onPrimary = Color.White,
    secondary = TrackPurple,
    onSecondary = Color.White,
    tertiary = TrackCyan,
    onTertiary = Carbon,
    background = Carbon,
    onBackground = TextPrimary,
    surface = CarbonRaised,
    onSurface = TextPrimary,
    surfaceVariant = CarbonLine,
    onSurfaceVariant = TextMuted,
    error = SignalRed,
    onError = Color.White,
    outline = CarbonLine,
)

@Composable
fun CutebotAndroidSampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RaceColorScheme,
        typography = Typography,
        content = content,
    )
}
