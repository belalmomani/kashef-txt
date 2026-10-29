package com.kashef.textsearch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Navy950 = Color(0xFF050B1F)
val Navy900 = Color(0xFF081535)
val Navy800 = Color(0xFF0D2252)
val CardNavy = Color(0xFF0B1A40)
val Blue500 = Color(0xFF1E6BFF)
val Blue400 = Color(0xFF3D8BFF)
val Cyan400 = Color(0xFF14C8F5)
val Gold400 = Color(0xFFE8B84A)
val TextPrimary = Color(0xFFEAF1FF)
val TextSecondary = Color(0xFF9FB3D9)
val Outline = Color(0xFF254A9A)

val ScreenGradient = Brush.verticalGradient(listOf(Navy900, Navy950))

private val KashefColors = darkColorScheme(
    primary = Blue500,
    onPrimary = Color.White,
    secondary = Cyan400,
    background = Navy950,
    onBackground = TextPrimary,
    surface = CardNavy,
    onSurface = TextPrimary,
    outline = Outline,
)

@Composable
fun KashefTheme(content: @Composable () -> Unit) {
    // The app identity is dark navy regardless of system theme.
    MaterialTheme(colorScheme = KashefColors, content = content)
}
