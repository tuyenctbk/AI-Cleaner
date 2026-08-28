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
  primary = PolishPrimaryContainer,
  onPrimary = PolishOnPrimaryContainer,
  primaryContainer = PolishPrimaryDark,
  onPrimaryContainer = PolishPrimaryContainer,
  secondary = PolishSecondaryContainer,
  onSecondary = PolishOnSecondaryContainer,
  tertiary = PolishOutlineVariant,
  background = TechDark,
  onBackground = PolishSurface,
  surface = CardDark,
  onSurface = PolishSurface,
  surfaceVariant = TechNavy,
  onSurfaceVariant = PolishOutline,
  outline = PolishTextMuted,
  error = CoralRed,
)

private val LightColorScheme = lightColorScheme(
  primary = PolishPrimary,
  onPrimary = PolishSurface,
  primaryContainer = PolishPrimaryContainer,
  onPrimaryContainer = PolishOnPrimaryContainer,
  secondary = PolishSecondary,
  onSecondary = PolishSurface,
  tertiary = PolishOutlineVariant,
  background = PolishBackground,
  onBackground = PolishTextPrimary,
  surface = PolishSurface,
  onSurface = PolishTextPrimary,
  surfaceVariant = PolishSurfaceVariant,
  onSurfaceVariant = PolishTextSecondary,
  outline = PolishOutline,
  error = CoralRed,
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

