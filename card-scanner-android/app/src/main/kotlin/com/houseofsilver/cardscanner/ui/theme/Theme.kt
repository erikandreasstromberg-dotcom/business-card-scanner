package com.houseofsilver.cardscanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WarmGold,
    onPrimary = NavyPrimary,
    primaryContainer = NavyLight,
    onPrimaryContainer = GoldLight,
    secondary = GoldLight,
    onSecondary = NavyPrimary,
    background = BackgroundDark,
    onBackground = Color(0xFFEEEEEE),
    surface = SurfaceDark,
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFBBBBBB),
    error = ErrorRed,
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF444444),
)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = OnPrimary,
    primaryContainer = NavyLight,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = WarmGold,
    onSecondary = NavyPrimary,
    background = BackgroundLight,
    onBackground = NavyPrimary,
    surface = SurfaceLight,
    onSurface = NavyPrimary,
    surfaceVariant = Color(0xFFF0EDE6),
    onSurfaceVariant = Color(0xFF444444),
    error = ErrorRed,
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFCCCCCC),
)

@Composable
fun CardScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
