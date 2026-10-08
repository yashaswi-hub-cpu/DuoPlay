package com.duoplay.video.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// "Chalkboard" look: deep ink-green board, chalk-white text, chalk-yellow accent.
val Ink = Color(0xFF0D1411)
val Slate = Color(0xFF16201B)
val Slate2 = Color(0xFF212E27)
val Chalk = Color(0xFFEDE9DD)
val Amber = Color(0xFFF2C14E)
val Coral = Color(0xFFFF7F66)
val Muted = Color(0xFF9AA79F)

private val scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = Coral,
    background = Ink,
    onBackground = Chalk,
    surface = Slate,
    onSurface = Chalk,
    surfaceVariant = Slate2,
    onSurfaceVariant = Muted
)

@Composable
fun ClassReelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
