package com.wpinrui.dovora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DovoraColorScheme = darkColorScheme(
    primary = DovoraWhite,
    onPrimary = DovoraBlack,
    secondary = DovoraWhite,
    onSecondary = DovoraBlack,
    tertiary = DovoraWhite,
    onTertiary = DovoraBlack,
    background = DovoraBlack,
    onBackground = DovoraWhite,
    surface = DovoraSurface,
    onSurface = DovoraWhite,
    surfaceVariant = DovoraSurfaceVariant,
    onSurfaceVariant = DovoraOnSurfaceVariant,
    primaryContainer = DovoraSurfaceVariant,
    onPrimaryContainer = DovoraWhite,
    secondaryContainer = DovoraSurfaceVariant,
    onSecondaryContainer = DovoraWhite
)

@Composable
fun DovoraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DovoraColorScheme,
        typography = Typography,
        content = content
    )
}