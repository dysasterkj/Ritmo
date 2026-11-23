package com.kjota.ritmo.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

object Pal {
    val Green = Color(0xFF1DB954)
    val Mint = Color(0xFF2EE6C5)
    val Violet = Color(0xFF8B7BFF)
    val Blue = Color(0xFF3B82F6)
    val Pink = Color(0xFFFF5FA2)
    val Orange = Color(0xFFFF8A3D)
    val Ink = Color(0xFF07090C)
    val Dim = Color(0xB3FFFFFF)
}

private val Colors = darkColorScheme(
    primary = Pal.Green,
    onPrimary = Color(0xFF00210B),
    secondary = Color(0xFFC8D6CF),
    background = Pal.Ink,
    onBackground = Color(0xFFF2F5F3),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFF2F5F3),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFC9CDD2),
    outline = Color(0x66FFFFFF),
    outlineVariant = Color(0x33FFFFFF),
    error = Color(0xFFFFB4AB),
)

@Composable
fun RitmoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        CompositionLocalProvider(LocalContentColor provides Colors.onBackground, content = content)
    }
}
