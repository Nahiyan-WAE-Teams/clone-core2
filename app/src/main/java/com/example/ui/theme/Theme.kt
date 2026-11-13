package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CloneCoreColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = ObsidianCanvas,
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = EmeraldMatrix,
    onSecondary = ObsidianCanvas,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = AmberWarn,
    onTertiary = ObsidianCanvas,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    error = CoralAlert,
    onError = ObsidianCanvas,
    errorContainer = CoralContainer,
    onErrorContainer = OnCoralContainer,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle
)

val CloneCoreShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CloneCoreTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CloneCoreColorScheme,
        typography = CloneCoreTypography,
        shapes = CloneCoreShapes,
        content = content
    )
}
