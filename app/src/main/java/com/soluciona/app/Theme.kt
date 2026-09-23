package com.soluciona.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF2F6FEB)
val Ink = Color(0xFF101827)
val Muted = Color(0xFF687386)
val AppBg = Color(0xFFF6F8FC)
val SoftBlue = Color(0xFFEFF6FF)
val Success = Color(0xFF119447)

private val SolucionaColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    background = AppBg,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    secondary = Success,
    outline = Color(0xFFD8DEE9)
)

@Composable
fun SolucionaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SolucionaColors, content = content)
}
