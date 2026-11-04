package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanNeon,
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F55),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = AmberGlow,
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF633F00),
    onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = VioletLayerGlow,
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF4F378B),
    onTertiaryContainer = Color(0xFFEADDFF),
    background = AeSlate950,
    onBackground = AeSlate50,
    surface = AeSlate900,
    onSurface = AeSlate50,
    surfaceVariant = AeSlate850,
    onSurfaceVariant = AeSlate200,
    outline = AeSlate700,
    outlineVariant = AeSlate800,
    error = CrimsonReticle,
    onError = Color.White
)

private val LightColorScheme = DarkColorScheme // Video tracking studios are optimal in pro dark mode

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
