package com.coinglass.intel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF071018)
val Surface = Color(0xFF0D1B24)
val Surface2 = Color(0xFF122430)
val Accent = Color(0xFF00E5C3)
val AccentDim = Color(0xFF0A3D38)
val Bull = Color(0xFF3DDC97)
val Bear = Color(0xFFFF5C7A)
val Warn = Color(0xFFFFC14D)
val Mute = Color(0xFF7F96A3)
val Text = Color(0xFFE8F4F2)
val Line = Color(0xFF1C3340)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00211C),
    background = Bg,
    onBackground = Text,
    surface = Surface,
    onSurface = Text,
    surfaceVariant = Surface2,
    onSurfaceVariant = Mute,
    error = Bear,
    outline = Line,
)

private val Type = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 36.sp, color = Text),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Text),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Text),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, color = Text),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Mute),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, letterSpacing = 0.6.sp, color = Mute),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF007A6A),
    onPrimary = Color.White,
    background = Color(0xFFF3F7F6),
    onBackground = Color(0xFF102018),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF102018),
    surfaceVariant = Color(0xFFE4EEEC),
    onSurfaceVariant = Color(0xFF4A5C58),
    error = Bear,
    outline = Color(0xFFC5D4D0),
)

@Composable
fun CoinGlassTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Scheme else LightScheme, typography = Type, content = content)
}
