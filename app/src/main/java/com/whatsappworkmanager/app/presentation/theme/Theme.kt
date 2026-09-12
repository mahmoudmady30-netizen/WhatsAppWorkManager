package com.whatsappworkmanager.app.presentation.theme

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryTeal,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryGreen,
    onSecondary = OnPrimary,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryAmberDeep,
    onTertiary = OnPrimary,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = UrgentRed,
    onError = OnUrgentRed,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColors = darkColorScheme(
    primary = PrimaryTealLight,
    onPrimary = OnPrimaryContainerDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryContainerLight,
    onSecondary = OnSecondaryContainerDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryContainerLight,
    onTertiary = OnTertiaryContainerDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = UrgentRed,
    onError = OnUrgentRed,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val PremiumColors = darkColorScheme(
    primary = Color(0xFFD4AF37),
    onPrimary = Color(0xFF15120A),
    primaryContainer = Color(0xFF3A2D0A),
    onPrimaryContainer = Color(0xFFFFE8A3),
    secondary = Color(0xFFC8B06A),
    onSecondary = Color(0xFF17140C),
    secondaryContainer = Color(0xFF302811),
    onSecondaryContainer = Color(0xFFEBD9A3),
    tertiary = Color(0xFFE0C06A),
    onTertiary = Color(0xFF17140C),
    tertiaryContainer = Color(0xFF3A2D0A),
    onTertiaryContainer = Color(0xFFFFE8A3),
    background = Color(0xFF080808),
    onBackground = Color(0xFFF3EFE5),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFF3EFE5),
    surfaceVariant = Color(0xFF1C1A16),
    onSurfaceVariant = Color(0xFFC9C1AF),
    outline = Color(0xFF625735),
    outlineVariant = Color(0xFF302B20),
    error = UrgentRed,
    onError = OnUrgentRed,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

enum class ThemeMode { LIGHT, DARK, SYSTEM, PREMIUM }

@Composable
fun WwmTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.PREMIUM -> false
    }
    val colors = when (themeMode) {
        ThemeMode.PREMIUM -> PremiumColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.LIGHT, ThemeMode.SYSTEM -> if (useDark) DarkColors else LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = WwmTypography,
        shapes = WwmShapes,
        content = content
    )
}
