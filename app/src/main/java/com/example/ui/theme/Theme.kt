package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = OpsPrimary,
    onPrimary = Color.White,
    primaryContainer = OpsSurfaceVariantDark,
    onPrimaryContainer = OpsTextPrimary,
    secondary = OpsAccentCyan,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF004E5A),
    onSecondaryContainer = OpsTextPrimary,
    tertiary = OpsTertiary,
    onTertiary = Color.White,
    background = OpsBackgroundDark,
    onBackground = OpsTextPrimary,
    surface = OpsSurfaceDark,
    onSurface = OpsTextPrimary,
    surfaceVariant = OpsSurfaceVariantDark,
    onSurfaceVariant = OpsTextSecondary,
    outline = OpsCardBorderDark,
    error = OpsError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = OpsPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = OpsBackgroundLight,
    onBackground = OpsTextPrimaryLight,
    surface = OpsSurfaceLight,
    onSurface = OpsTextPrimaryLight,
    surfaceVariant = OpsSurfaceVariantLight,
    onSurfaceVariant = OpsTextSecondaryLight,
    outline = Color(0xFFCBD5E1),
    error = OpsError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
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

