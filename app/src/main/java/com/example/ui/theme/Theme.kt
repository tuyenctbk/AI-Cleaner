package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val DarkColorScheme = darkColorScheme(
    primary = DarkBrandPrimary,
    onPrimary = Color.White,
    primaryContainer = DarkBrandPrimaryContainer,
    onPrimaryContainer = DarkBrandOnPrimaryContainer,
    secondary = DarkBrandSecondary,
    onSecondary = DarkBrandOnSecondary,
    secondaryContainer = DarkBrandSecondaryContainer,
    onSecondaryContainer = DarkBrandOnSecondaryContainer,
    tertiary = VividViolet,
    onTertiary = Color.White,
    background = TechDark,
    onBackground = DarkTextPrimary,
    surface = CardDark,
    onSurface = DarkTextPrimary,
    surfaceVariant = TechNavy,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = Color(0xFF283548),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFF451218),
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = PolishPrimary,
    onPrimary = Color.White,
    primaryContainer = PolishPrimaryContainer,
    onPrimaryContainer = PolishOnPrimaryContainer,
    secondary = PolishSecondary,
    onSecondary = Color.White,
    secondaryContainer = PolishSecondaryContainer,
    onSecondaryContainer = PolishOnSecondaryContainer,
    tertiary = VividViolet,
    onTertiary = Color.White,
    background = PolishBackground,
    onBackground = PolishTextPrimary,
    surface = PolishSurface,
    onSurface = PolishTextPrimary,
    surfaceVariant = PolishSurfaceVariant,
    onSurfaceVariant = PolishOnSurfaceVariant,
    outline = PolishOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = CoralRed,
    onError = Color.White,
    errorContainer = SoftRed,
    onErrorContainer = Color(0xFF7F1D1D)
)

@Composable
fun AICleanerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
