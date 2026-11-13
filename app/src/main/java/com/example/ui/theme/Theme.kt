package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CloneCoreColorScheme = lightColorScheme(
    primary = CyanPulse,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = EmeraldMatrix,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = AmberWarn,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    error = CoralAlert,
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = OnCoralContainer,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle
)

val CloneCoreShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun CloneCoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CloneCoreColorScheme,
        typography = CloneCoreTypography,
        shapes = CloneCoreShapes,
        content = content
    )
}
