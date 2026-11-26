package com.kawrexam.student.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import com.kawrexam.student.R

/*
 * KAWREXAM look: light "liquid glass". Soft sky-to-lavender backdrop, white frosted cards, one violet brand colour
 * with a deep-indigo gradient for primary actions. The logo is the open book wrapped by an orbit ring.
 */
val Violet = Color(0xFF6D4BE0)
val VioletDeep = Color(0xFF3B22A8)
val VioletSoft = Color(0xFFE9E3FF)
val Ink = Color(0xFF1B1747)
val InkSoft = Color(0xFF5E5A86)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5436CF), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF), onPrimaryContainer = Ink,
    secondary = Violet, tertiary = Color(0xFFF472B6),
    background = Color(0xFFEEF0FF), surface = Color.White,
    onBackground = Ink, onSurface = Ink, onSurfaceVariant = InkSoft,
    error = Color(0xFFD93B4E), outline = Color(0x2E6D4BE0),
)

private val base = Typography()
/** DM Sans: geometric and friendly, but still very legible at body sizes. Bundled in res/font (SIL OFL, see docs/DMSans-OFL.txt). */
val Sans = FontFamily(
    Font(R.font.dmsans_regular, FontWeight.Normal),
    Font(R.font.dmsans_medium, FontWeight.Medium),
    Font(R.font.dmsans_semibold, FontWeight.SemiBold),
    Font(R.font.dmsans_bold, FontWeight.Bold),
    Font(R.font.dmsans_extrabold, FontWeight.ExtraBold),
)
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold),
    displayMedium = base.displayMedium.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold),
    displaySmall = base.displaySmall.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Sans, fontSize = 17.sp, lineHeight = 26.sp), // exam questions: easy to read for long
    bodyMedium = base.bodyMedium.copy(fontFamily = Sans),
    bodySmall = base.bodySmall.copy(fontFamily = Sans),
    labelLarge = base.labelLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Sans),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
)

@Composable
fun KawrexamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = AppTypography, shapes = AppShapes) {
        // No Surface sits above our screens, so without this every plain Text would fall back to the system font.
        ProvideTextStyle(AppTypography.bodyMedium, content)
    }
}

/** Sky-blue haze at the top fading into lavender, with a violet glow at the bottom corner. */
@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFD9E5FF), Color(0xFFEAE9FF), Color(0xFFF3F0FF))))
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(Color(0x55FFFFFF), Color.Transparent), center = Offset(size.width * 0.2f, size.height * 0.04f), radius = size.width * 0.8f))
                drawRect(Brush.radialGradient(listOf(Color(0x33835CF0), Color.Transparent), center = Offset(size.width * 1.05f, size.height * 1.0f), radius = size.width * 1.0f))
            }
            .safeDrawingPadding(),
    ) { content() }
}

/** Frosted white panel with a lit rim. One flat layer (no blur) keeps older phones smooth. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0x336D4BE0)))
    Card(
        modifier = modifier.shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x226D4BE0), spotColor = Color(0x226D4BE0)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)),
        border = BorderStroke(1.dp, rim),
        content = content,
    )
}

/** The KAWREXAM mark (open book + orbit ring + spark), no background. */
@Composable
fun LogoMark(size: Dp = 56.dp) {
    Image(painterResource(R.drawable.ic_kawrexam_logo), contentDescription = "KAWREXAM", modifier = Modifier.size(size))
}

/** The logo on its dark rounded app-icon tile. */
@Composable
fun AppIconTile(size: Dp = 88.dp) {
    Box(
        Modifier.size(size).shadow(18.dp, RoundedCornerShape(size * 0.26f), ambientColor = Color(0x556D4BE0), spotColor = Color(0x556D4BE0))
            .clip(RoundedCornerShape(size * 0.26f))
            .background(Brush.linearGradient(listOf(Color(0xFF2E2680), Color(0xFF15122F))))
            .border(1.dp, Color(0x66A78BFA), RoundedCornerShape(size * 0.26f)),
        contentAlignment = Alignment.Center,
    ) { LogoMark(size * 0.72f) }
}

/** "KAWR" in ink and "EXAM" in violet, as in the brand lockup. */
@Composable
fun Wordmark(fontSize: Int = 24) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Ink)) { append("KAWR") }
            withStyle(SpanStyle(color = Violet)) { append("EXAM") }
        },
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp, letterSpacing = 0.5.sp,
    )
}

/** Logo, name, and the tagline. Used on the sign-in screen. */
@Composable
fun BrandBlock() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIconTile()
        Spacer(Modifier.height(14.dp))
        Wordmark(30)
        Text("Ujian Online, Lebih Mudah.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Deep-indigo gradient pill: the primary call to action. Optional drawn icons before and after the label. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: Ic? = null, trailing: Ic? = null) {
    val shape = RoundedCornerShape(50)
    val bg = if (enabled) Brush.horizontalGradient(listOf(Color(0xFF6A4BE6), Color(0xFF3B22A8))) else Brush.horizontalGradient(listOf(Color(0xFFC9C4E6), Color(0xFFC9C4E6)))
    Row(
        modifier.defaultMinSize(minHeight = 48.dp)
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = Color(0x663B22A8), spotColor = Color(0x663B22A8))
            .clip(shape).background(bg).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (leading != null) Icon24(leading, Color.White, 16.dp, filled = true)
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
        if (trailing != null) Icon24(trailing, Color.White, 18.dp)
    }
}

/** White frosted circle button for header actions. */
@Composable
fun GlassCircleButton(icon: Ic, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).shadow(6.dp, CircleShape, ambientColor = Color(0x226D4BE0), spotColor = Color(0x226D4BE0))
            .clip(CircleShape).background(Color.White.copy(alpha = 0.8f)).border(1.dp, Color.White, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon24(icon, Ink, 20.dp) }
}

/** Small stat tile: coloured icon square, label, big number. */
@Composable
fun StatCard(icon: Ic, tint: Color, label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.75f), tint))), contentAlignment = Alignment.Center) {
                Icon24(icon, Color.White, 24.dp, filled = true, onTint = tint)
            }
            Column {
                Text(label, color = InkSoft, fontSize = 12.sp, maxLines = 1)
                Text(value, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** A whole-row answer choice: large tap target, clear selected state. Radio for one answer, checkbox for several. */
@Composable
fun OptionCard(text: String, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).clip(shape)
            .background(if (selected) scheme.primaryContainer else Color.White.copy(alpha = 0.7f))
            .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outline, shape)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (multi) Checkbox(checked = selected, onCheckedChange = { onClick() }) else RadioButton(selected = selected, onClick = onClick)
        Text(text, Modifier.weight(1f).padding(end = 10.dp), color = scheme.onSurface)
    }
}

/** Countdown pill: violet normally, red in the last five minutes. */
@Composable
fun TimerPill(text: String, low: Boolean) {
    val c = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 5.dp)) {
        Text(text, color = c, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
    }
}
