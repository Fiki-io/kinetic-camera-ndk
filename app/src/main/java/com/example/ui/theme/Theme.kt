package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NeutralDarkColorScheme = darkColorScheme(
    primary = SoftOffWhite,
    onPrimary = MatteBlack,
    primaryContainer = SurfaceSlate,
    onPrimaryContainer = SoftOffWhite,
    secondary = NeutralGrey,
    onSecondary = MatteBlack,
    secondaryContainer = SurfaceSlateBorder,
    onSecondaryContainer = SoftOffWhite,
    background = MatteBlack,
    onBackground = SoftOffWhite,
    surface = DeepCharcoal,
    onSurface = SoftOffWhite,
    surfaceVariant = SurfaceSlate,
    onSurfaceVariant = NeutralGrey,
    outline = SurfaceSlateBorder
)

@Composable
fun KineticCameraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeutralDarkColorScheme,
        typography = Typography,
        content = content
    )
}
