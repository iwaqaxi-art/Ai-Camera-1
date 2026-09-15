package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AiCyan,
    onPrimary = CameraBlack,
    primaryContainer = Color(0xFF004D56),
    onPrimaryContainer = AiCyanLight,
    secondary = AiEmerald,
    onSecondary = CameraBlack,
    secondaryContainer = Color(0xFF004D25),
    tertiary = AiAmber,
    onTertiary = CameraBlack,
    background = CameraBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun AiCameraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MyApplicationTheme(darkTheme = darkTheme, content = content)
}
