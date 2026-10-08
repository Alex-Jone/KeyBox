package com.keybox.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KeyBoxColorScheme = lightColorScheme(
    primary = Color(0xFF1A1A2E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E8F0),
    onPrimaryContainer = Color(0xFF1A1A2E),
    secondary = Color(0xFF4A4A6A),
    background = Color(0xFFF7F7FA),
    surface = Color.White,
    error = Color(0xFFB00020)
)

@Composable
fun KeyBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KeyBoxColorScheme,
        content = content
    )
}
