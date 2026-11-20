package com.syildirim.teslacharge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val TeslaRed = Color(0xFFE31937)
val ChartBlue = Color(0xFF3E6AE1)
val ChartTeal = Color(0xFF1BA39C)
val ChartAmber = Color(0xFFE8A33D)

private val Dark = darkColorScheme(
    primary = Color(0xFFF4F4F4),
    onPrimary = Color(0xFF111214),
    secondary = TeslaRed,
    onSecondary = Color.White,
    tertiary = ChartBlue,
    background = Color(0xFF0B0B0C),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF0B0B0C),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1A1B1E),
    onSurfaceVariant = Color(0xFFA2A3A7),
    surfaceContainer = Color(0xFF151618),
    surfaceContainerHigh = Color(0xFF1C1D20),
    surfaceContainerLow = Color(0xFF121315),
    outline = Color(0xFF3A3B3F),
    outlineVariant = Color(0xFF26272A),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFF171A20),
    onPrimary = Color.White,
    secondary = TeslaRed,
    onSecondary = Color.White,
    tertiary = ChartBlue,
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF171A20),
    surface = Color(0xFFF7F7F8),
    onSurface = Color(0xFF171A20),
    surfaceVariant = Color(0xFFEDEEF0),
    onSurfaceVariant = Color(0xFF5C5E62),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F1F3),
    surfaceContainerLow = Color.White,
    outline = Color(0xFFD0D1D2),
    outlineVariant = Color(0xFFE4E5E7),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Medium),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Medium),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun TeslaChargeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
