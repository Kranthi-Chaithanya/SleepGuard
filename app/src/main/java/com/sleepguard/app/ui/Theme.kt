package com.sleepguard.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightColors = darkColorScheme(
    primary = Color(0xFF9FA8FF),
    onPrimary = Color(0xFF14154A),
    primaryContainer = Color(0xFF2E3170),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFFFD58A),
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF4A3A10),
    onSecondaryContainer = Color(0xFFFFE7B0),
    tertiary = Color(0xFF7FD8C4),
    background = Color(0xFF0B0D1E),
    onBackground = Color(0xFFE4E4F4),
    surface = Color(0xFF0B0D1E),
    onSurface = Color(0xFFE4E4F4),
    surfaceVariant = Color(0xFF252947),
    onSurfaceVariant = Color(0xFFB4B7D3),
    surfaceContainerLowest = Color(0xFF080A17),
    surfaceContainerLow = Color(0xFF111427),
    surfaceContainer = Color(0xFF15182E),
    surfaceContainerHigh = Color(0xFF1C1F3A),
    surfaceContainerHighest = Color(0xFF242845),
    outline = Color(0xFF4A4D6E),
    outlineVariant = Color(0xFF2E3150),
    error = Color(0xFFFFB4AB),
)

@Composable
fun SleepTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, content = content)
}
