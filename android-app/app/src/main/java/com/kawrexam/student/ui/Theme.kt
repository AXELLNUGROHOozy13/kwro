package com.kawrexam.student.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * KAWREXAM look: institutional blue on frosted glass, one brass accent (the seal). Headings are serif for a formal
 * register, body text is the system sans. Same tokens as the admin dashboard so the two feel like one product.
 */
val Brass = Color(0xFF9A7420)
private val BrassDark = Color(0xFFD4AD57)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B4F8A), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E2F4), onPrimaryContainer = Color(0xFF0C2340),
    secondary = Color(0xFF123A6B), tertiary = Brass,
    background = Color(0xFFE6ECF3), surface = Color.White,
    onBackground = Color(0xFF0C2340), onSurface = Color(0xFF0C2340), onSurfaceVariant = Color(0xFF51647B),
    error = Color(0xFFA52A21), outline = Color(0x240C2340),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB2EA), onPrimary = Color(0xFF06213F),
    primaryContainer = Color(0xFF1B3A5E), onPrimaryContainer = Color(0xFFE6EDF7),
    secondary = Color(0xFF5B95D4), tertiary = BrassDark,
    background = Color(0xFF07111F), surface = Color(0xFF12243E),
    onBackground = Color(0xFFE6EDF7), onSurface = Color(0xFFE6EDF7), onSurfaceVariant = Color(0xFF9DB0C8),
    error = Color(0xFFFF8A80), outline = Color(0x2E8FB6E6),
)

private val base = Typography()
private val AppTypography = Typography(
    headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp), // exam questions: comfortable to read for a long time
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp),
)

@Composable
fun KawrexamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography, shapes = AppShapes, content = content,
    )
}

/** Soft blue light from the top corner and a faint brass glow below: something for the glass panels to sit on. */
@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme.background
    val glowA = if (dark) Color(0x334682D2) else Color(0x381B4F8A)
    val glowB = if (dark) Color(0x14D4AD57) else Color(0x1F9A7420)
    Box(
        Modifier.fillMaxSize().background(base)
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(glowA, Color.Transparent), center = Offset(size.width * 0.1f, -size.height * 0.02f), radius = size.width * 1.1f))
                drawRect(Brush.radialGradient(listOf(glowB, Color.Transparent), center = Offset(size.width * 1.05f, size.height * 1.02f), radius = size.width * 1.0f))
            }
            .safeDrawingPadding(),
    ) { content() }
}

/** Translucent panel with a lit top edge. One flat layer (no blur) keeps older phones smooth. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val rim = Brush.verticalGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
        else listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f)),
    )
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.62f else 0.74f)),
        border = BorderStroke(1.dp, rim),
        content = content,
    )
}

/** The KAWREXAM seal: a monogram inside a brass ring. Decorative. */
@Composable
fun Seal(size: androidx.compose.ui.unit.Dp = 64.dp) {
    val ring = MaterialTheme.colorScheme.tertiary
    Box(
        Modifier.size(size).border(2.dp, ring, CircleShape).padding(4.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF3A77BD), Color(0xFF0F3262)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("KRX", color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.27f).sp, letterSpacing = 1.sp)
    }
}

/** Seal, name, and one line of what this is. Used on the sign-in screen. */
@Composable
fun BrandBlock() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Seal()
        Spacer(Modifier.height(12.dp))
        Text("KAWREXAM", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Text("Sistem ujian sekolah", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** A whole-row answer choice: large tap target, clear selected state. Radio for one answer, checkbox for several. */
@Composable
fun OptionCard(text: String, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).clip(shape)
            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.75f) else scheme.surface.copy(alpha = 0.55f))
            .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outline, shape)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (multi) Checkbox(checked = selected, onCheckedChange = { onClick() }) else RadioButton(selected = selected, onClick = onClick)
        Text(text, Modifier.weight(1f).padding(end = 10.dp), color = scheme.onSurface)
    }
}

/** Countdown pill: calm blue normally, red in the last five minutes. */
@Composable
fun TimerPill(text: String, low: Boolean) {
    val c = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text, color = c, style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
    }
}
