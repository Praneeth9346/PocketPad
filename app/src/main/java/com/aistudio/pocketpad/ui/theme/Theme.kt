package com.aistudio.pocketpad.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Modern Automotive HUD Palette
val ElectricCyan = Color(0xFF00E5FF)
val SunsetOrange = Color(0xFFFF5722)
val SunsetYellow = Color(0xFFFFC107)
val MutedGreen = Color(0xFF00C853)
val DarkBackground = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF121212)
val SurfaceCard = Color(0xFF1E1E1E)
val BorderSubtle = Color(0x26FFFFFF) // 15% opacity white
val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFF9E9E9E)

// Aliases to avoid breaking existing code during the transition
val ForzaCyan = ElectricCyan
val ForzaMagenta = SunsetOrange
val ForzaYellow = SunsetYellow
val ForzaGreen = MutedGreen
val BorderCyan = Color(0x4D00E5FF)

// Specific Forza Preset Colors
val ForzaOrange = Color(0xFFFF5B00)
val ForzaDarkBg = Color(0xFF030303)
val ForzaCardBg = Color(0x990A0A0A)
val ForzaBorder = Color(0x80FF5B00)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    secondary = SunsetOrange,
    tertiary = SunsetYellow,
    background = DarkBackground,
    surface = SurfaceDark,
    surfaceVariant = SurfaceCard,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun PocketPadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
