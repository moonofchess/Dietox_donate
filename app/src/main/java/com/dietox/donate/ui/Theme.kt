package com.dietox.donate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF6366F1)
private val IndigoDark = Color(0xFF4F46E5)
private val Mint = Color(0xFF10B981)
private val Coral = Color(0xFFEF4444)

private val DarkColors = darkColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Mint,
    error = Coral,
    background = Color(0xFF121215),
    surface = Color(0xFF1B1B20),
    surfaceVariant = Color(0xFF26262D),
)

private val LightColors = lightColorScheme(
    primary = IndigoDark,
    onPrimary = Color.White,
    secondary = Mint,
    error = Coral,
    background = Color(0xFFF7F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFECECF2),
)

@Composable
fun DietoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
