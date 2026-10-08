package com.keybox.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ===== 品牌色（对齐设计稿 keyBox.png 的蓝色系） =====
val BrandBlue = Color(0xFF2D5BF0)
val BrandBlueDark = Color(0xFF1E40C9)
val BrandBlueLight = Color(0xFFE8EFFF)
val BrandBlueGradientTop = Color(0xFF3B6CFF)
val BrandBlueGradientBottom = Color(0xFF2450E0)

private val KeyBoxColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = BrandBlueDark,
    secondary = Color(0xFF5A6B8C),
    secondaryContainer = Color(0xFFEEF2FA),
    onSecondaryContainer = Color(0xFF33415E),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF1A2233),
    surface = Color.White,
    onSurface = Color(0xFF1A2233),
    surfaceVariant = Color(0xFFF0F3F9),
    onSurfaceVariant = Color(0xFF6B7A99),
    error = Color(0xFFE5484D),
    outline = Color(0xFFE1E6F0)
)

private val KeyBoxShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun KeyBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KeyBoxColorScheme,
        shapes = KeyBoxShapes,
        content = content
    )
}
