package io.github.sakuya121212.notificationcleaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CleanerBlueLight,
    onPrimary = Color(0xFF003258),
    primaryContainer = CleanerBlueDark,
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = CleanGreenLight,
    onSecondary = DarkBackground,
    secondaryContainer = CleanGreenDark,
    onSecondaryContainer = CleanGreenLight,
    tertiary = AlertRed,
    onTertiary = Color.White,
    error = AlertRed,
    onError = Color.White,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = CleanerBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = CleanerBlueDark,
    secondary = CleanGreenDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9F6CA),
    onSecondaryContainer = CleanGreenDark,
    tertiary = AlertRed,
    onTertiary = Color.White,
    error = AlertRed,
    onError = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

@Composable
fun NotificationCleanerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
