package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Color

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val DarkColorScheme =
  darkColorScheme(
    primary = SandalPrimaryDark,
    onPrimary = SandalOnPrimaryDark,
    primaryContainer = SandalPrimaryContainerDark,
    onPrimaryContainer = SandalOnPrimaryContainerDark,
    secondary = SandalSecondaryDark,
    onSecondary = SandalSurfaceDark,
    secondaryContainer = SandalSecondaryContainerDark,
    onSecondaryContainer = SandalOnSecondaryContainerDark,
    surface = SandalSurfaceDark,
    onSurface = SandalTextDark,
    surfaceVariant = SandalSurfaceVariantDark,
    onSurfaceVariant = SandalOnSurfaceVariantDark,
    background = SandalBackgroundDark,
    onBackground = SandalTextDark,
    error = SandalErrorDark,
    errorContainer = SandalErrorContainerDark,
    onErrorContainer = SandalTextDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SandalPrimaryLight,
    onPrimary = SandalOnPrimaryLight,
    primaryContainer = SandalPrimaryContainerLight,
    onPrimaryContainer = SandalOnPrimaryContainerLight,
    secondary = SandalSecondaryLight,
    onSecondary = SandalSurfaceLight,
    secondaryContainer = SandalSecondaryContainerLight,
    onSecondaryContainer = SandalOnSecondaryContainerLight,
    surface = SandalSurfaceLight,
    onSurface = SandalTextLight,
    surfaceVariant = SandalSurfaceVariantLight,
    onSurfaceVariant = SandalOnSurfaceVariantLight,
    background = SandalBackgroundLight,
    onBackground = SandalTextLight,
    error = SandalErrorLight,
    errorContainer = SandalErrorContainerLight,
    onErrorContainer = SandalSurfaceLight
  )

private val NordColorScheme = darkColorScheme(
    primary = NordPrimary,
    onPrimary = NordOnPrimary,
    primaryContainer = NordPrimaryContainer,
    onPrimaryContainer = NordOnPrimaryContainer,
    secondary = NordSecondary,
    onSecondary = NordSurface,
    secondaryContainer = NordSecondaryContainer,
    onSecondaryContainer = NordOnSecondaryContainer,
    surface = NordSurface,
    onSurface = NordText,
    surfaceVariant = NordSurfaceVariant,
    onSurfaceVariant = NordOnSurfaceVariant,
    background = NordBackground,
    onBackground = NordText,
    error = NordError,
    errorContainer = NordErrorContainer,
    onErrorContainer = NordText
)

private val OceanColorScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = OceanOnPrimary,
    primaryContainer = OceanPrimaryContainer,
    onPrimaryContainer = OceanOnPrimaryContainer,
    secondary = OceanSecondary,
    onSecondary = OceanSurface,
    secondaryContainer = OceanSecondaryContainer,
    onSecondaryContainer = OceanOnSecondaryContainer,
    surface = OceanSurface,
    onSurface = OceanText,
    surfaceVariant = OceanSurfaceVariant,
    onSurfaceVariant = OceanOnSurfaceVariant,
    background = OceanBackground,
    onBackground = OceanText,
    error = OceanError,
    errorContainer = OceanErrorContainer,
    onErrorContainer = OceanText
)

private val SepiaColorScheme = lightColorScheme(
    primary = SepiaPrimary,
    onPrimary = SepiaOnPrimary,
    primaryContainer = SepiaPrimaryContainer,
    onPrimaryContainer = SepiaOnPrimaryContainer,
    secondary = SepiaSecondary,
    onSecondary = SepiaSurface,
    secondaryContainer = SepiaSecondaryContainer,
    onSecondaryContainer = SepiaOnSecondaryContainer,
    surface = SepiaSurface,
    onSurface = SepiaText,
    surfaceVariant = SepiaSurfaceVariant,
    onSurfaceVariant = SepiaOnSurfaceVariant,
    background = SepiaBackground,
    onBackground = SepiaText,
    error = SepiaError,
    errorContainer = SepiaErrorContainer,
    onErrorContainer = SepiaText
)

private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = AmoledOnPrimary,
    primaryContainer = AmoledPrimaryContainer,
    onPrimaryContainer = AmoledOnPrimaryContainer,
    secondary = AmoledSecondary,
    onSecondary = AmoledSurface,
    secondaryContainer = AmoledSecondaryContainer,
    onSecondaryContainer = AmoledOnSecondaryContainer,
    surface = AmoledSurface,
    onSurface = AmoledText,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = AmoledOnSurfaceVariant,
    background = AmoledBackground,
    onBackground = AmoledText,
    error = AmoledError,
    errorContainer = AmoledErrorContainer,
    onErrorContainer = AmoledText
)

private val ClinicalColorScheme = lightColorScheme(
    primary = ClinicalPrimary,
    onPrimary = ClinicalOnPrimary,
    primaryContainer = ClinicalPrimaryContainer,
    onPrimaryContainer = ClinicalOnPrimaryContainer,
    secondary = ClinicalSecondary,
    onSecondary = ClinicalSurface,
    secondaryContainer = ClinicalSecondaryContainer,
    onSecondaryContainer = ClinicalOnSecondaryContainer,
    surface = ClinicalSurface,
    onSurface = ClinicalText,
    surfaceVariant = ClinicalSurfaceVariant,
    onSurfaceVariant = ClinicalOnSurfaceVariant,
    background = ClinicalBackground,
    onBackground = ClinicalText,
    error = ClinicalError,
    errorContainer = ClinicalErrorContainer,
    onErrorContainer = ClinicalText
)

private val SlateColorScheme = darkColorScheme(
    primary = SlatePrimary,
    onPrimary = SlateOnPrimary,
    primaryContainer = SlatePrimaryContainer,
    onPrimaryContainer = SlateOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateSurface,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
    surface = SlateSurface,
    onSurface = SlateText,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SlateOnSurfaceVariant,
    background = SlateBackground,
    onBackground = SlateText,
    error = SlateError,
    errorContainer = SlateErrorContainer,
    onErrorContainer = SlateText
)

@Composable
fun MyApplicationTheme(
  appTheme: com.example.AppThemeMode = com.example.AppThemeMode.SYSTEM,
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default so our custom Sandal theme shines
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      appTheme == com.example.AppThemeMode.NORD -> NordColorScheme
      appTheme == com.example.AppThemeMode.OCEAN -> OceanColorScheme
      appTheme == com.example.AppThemeMode.LIGHT -> LightColorScheme
      appTheme == com.example.AppThemeMode.DARK -> DarkColorScheme
      appTheme == com.example.AppThemeMode.SEPIA -> SepiaColorScheme
      appTheme == com.example.AppThemeMode.AMOLED -> AmoledColorScheme
      appTheme == com.example.AppThemeMode.CLINICAL -> ClinicalColorScheme
      appTheme == com.example.AppThemeMode.SLATE -> SlateColorScheme
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
    shapes = AppShapes,
    content = content
  )
}
