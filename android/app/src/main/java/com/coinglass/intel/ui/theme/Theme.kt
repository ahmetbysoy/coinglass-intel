package com.coinglass.intel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Signal colors stay vivid in both themes — never pastelize P/L. */
val Bull = Color(0xFF12B76A)
val Bear = Color(0xFFE11D48)
val Warn = Color(0xFFF59E0B)

/** Chart canvas stays dark in both themes so P/L inks stay readable. */
object ChartInk {
    val Plot = Color(0xFF08141C)
    val Plate = Color(0xCC08141C)
    val PlateSoft = Color(0xB408141C)
    val VaFill = Color(0x1400E5C3)
    val VaLine = Color(0x6600E5C3)
    val Grid = Color(0x12FFFFFF)
    val Divider = Color(0x1FFFFFFF)
    val Axis = Color(0xFFDCE6EB)
    val AxisMute = Color(0x99DCE6EB)
    val EmaFast = Color(0xFF64B5F6)
    val EmaSlow = Color(0xFFFFB74D)
    val HeaderScrim = Color(0x61000000)
    val Edge = Color(0x14FFFFFF)
}

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF00E5C3),
    onPrimary = Color(0xFF00211C),
    primaryContainer = Color(0xFF0A3D38),
    onPrimaryContainer = Color(0xFF00E5C3),
    background = Color(0xFF071018),
    onBackground = Color(0xFFE8F4F2),
    surface = Color(0xFF0D1B24),
    onSurface = Color(0xFFE8F4F2),
    surfaceVariant = Color(0xFF122430),
    onSurfaceVariant = Color(0xFF7F96A3),
    error = Bear,
    outline = Color(0xFF1C3340),
    secondary = Color(0xFF7C9CFF),
    onSecondary = Color(0xFF0A1020),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6B3FA0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9D7F7),
    onPrimaryContainer = Color(0xFF3B1D63),
    background = Color(0xFFF7F1F5),
    onBackground = Color(0xFF1C1220),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1220),
    surfaceVariant = Color(0xFFEDE0E8),
    onSurfaceVariant = Color(0xFF5C4B58),
    error = Bear,
    outline = Color(0xFFD9C8D4),
    secondary = Color(0xFF9A4D7A),
    onSecondary = Color.White,
)

private val Type = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

val Bg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val Surface: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val Surface2: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val Text: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val Mute: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Line: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline
val Accent: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val AccentDim: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

@Composable
fun CoinGlassTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Type,
        content = content,
    )
}
