package com.kawrexam.student.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8), onPrimary = Color(0xFF04111F),
    secondary = Color(0xFF60A5FA), background = Color(0xFF06101D), surface = Color(0xFF0F2038),
    onBackground = Color(0xFFE8F1FC), onSurface = Color(0xFFE8F1FC), onSurfaceVariant = Color(0xFF9BB2CC),
    error = Color(0xFFF87171), outline = Color(0x338CC3FF),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7), onPrimary = Color.White,
    secondary = Color(0xFF1D4ED8), background = Color(0xFFEEF5FB), surface = Color.White,
    onBackground = Color(0xFF0A1A2F), onSurface = Color(0xFF0A1A2F), onSurfaceVariant = Color(0xFF4A5F78),
    error = Color(0xFFB91C1C), outline = Color(0x1F0F2850),
)

@Composable
fun KawrexamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val brush = Brush.verticalGradient(
        if (dark) listOf(Color(0xFF0A2540), Color(0xFF06101D)) else listOf(Color(0xFFD9F3FB), Color(0xFFEEF5FB)),
    )
    Box(Modifier.fillMaxSize().background(brush).safeDrawingPadding()) { content() }
}

/** Translucent panel. One flat layer (no blur) keeps older phones smooth. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content,
    )
}
