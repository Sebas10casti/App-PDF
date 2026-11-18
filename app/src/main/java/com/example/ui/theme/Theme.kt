package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CoralVermilion,
    onPrimary = Color.White,
    primaryContainer = CoralSoftContainer,
    onPrimaryContainer = CoralOnSoftContainer,
    secondary = DeepSlate,
    onSecondary = Color.White,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = DeepSlate,
    tertiary = ToolSignColor,
    onTertiary = Color.White,
    background = LinenCanvas,
    onBackground = InkPrimary,
    surface = PurePaper,
    onSurface = InkPrimary,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = PaperOutline,
    outlineVariant = PaperOutline,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = CoralVermilionDark,
    onPrimary = Color(0xFF42080E),
    primaryContainer = CoralDarkContainer,
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = Color(0xFFBDC6DA),
    onSecondary = DeepCharcoal,
    secondaryContainer = SlateDarkContainer,
    onSecondaryContainer = LightInk,
    tertiary = Color(0xFF56C9A7),
    onTertiary = Color(0xFF00382A),
    background = DeepCharcoal,
    onBackground = LightInk,
    surface = NightSlate,
    onSurface = LightInk,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = LightInkMuted,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

val FolioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FolioPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FolioTypography,
        shapes = FolioShapes,
        content = content
    )
}
