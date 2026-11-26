package com.kawrexam.student.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Question-interface look (from the Banani export): deep navy header, pale-cyan canvas, cyan as the single
 * action colour, mint = answered, amber = flagged, pink = time almost up. Used only while an exam is open;
 * the rest of the app keeps its own look.
 */
internal object Ex {
    val Navy = Color(0xFF061C2A)
    val NavyLift = Color(0xFF12303F)
    val Cyan = Color(0xFF00D9F5)
    val CyanDeep = Color(0xFF00AFCB)
    val CyanText = Color(0xFF0A9DB8)
    val Mint = Color(0xFF00E5A0)
    val MintBg = Color(0xFFD3F9E8)
    val MintLine = Color(0xFF8DEBC6)
    val Amber = Color(0xFFFF9F1C)
    val AmberBg = Color(0xFFFFF0D6)
    val AmberLine = Color(0xFFF6CF8A)
    val Pink = Color(0xFFFF4D6D)
    val Pale = Color(0xFFF2FBFE)
    val Line = Color(0xFFCDE8F0)
    val Ink = Color(0xFF0B2230)
    val Soft = Color(0xFF5B7686)
    val Muted = Color(0xFF9DB5C2)
    val Canvas = Brush.verticalGradient(listOf(Color(0xFFF3FCFF), Color(0xFFD9F3FA)))
    val CyanGrad = Brush.horizontalGradient(listOf(Color(0xFF00D9F5), Color(0xFF00AFCB)))
}

/** A click target that swallows taps without any visual feedback (scrims, sheets). */
@Composable
internal fun Modifier.tapOnly(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = source, indication = null, onClick = onClick)
}

// ───────── Header ─────────
/** level: 0 = normal, 1 = last five minutes, 2 = last minute. */
internal fun timerLevel(leftMs: Long) = when { leftMs <= 60_000 -> 2; leftMs <= 300_000 -> 1; else -> 0 }

@Composable
internal fun TimerChip(text: String, level: Int) {
    val c = when (level) { 2 -> Ex.Pink; 1 -> Ex.Amber; else -> Ex.Cyan }
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(Ex.Navy).border(1.5.dp, c.copy(alpha = 0.55f), shape).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
    }
}

@Composable
internal fun ExamHeader(title: String, name: String, className: String?, timer: String, level: Int, online: Boolean, battery: Int) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(Ex.Navy)
            .statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(48.dp).shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = Ex.Cyan, spotColor = Ex.Cyan)
                    .clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Ex.Cyan, Color(0xFFBDF4FF)))),
                contentAlignment = Alignment.Center,
            ) { Text("K", color = Ex.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
            Column(Modifier.weight(1f)) {
                Text("KAWREXAM", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 3.sp)
                Text(name, color = Ex.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TimerChip(timer, level)
        }
        val pill = RoundedCornerShape(16.dp)
        Row(
            Modifier.fillMaxWidth().clip(pill).background(Ex.NavyLift).border(1.dp, Color(0x33FFFFFF), pill).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) Ex.Mint else Ex.Amber))
            Text(
                listOfNotNull(if (online) "Online" else "Offline", className?.takeIf { it.isNotBlank() }, if (battery >= 0) "$battery%" else null).joinToString(" • "),
                color = Ex.Muted, fontSize = 13.sp, maxLines = 1,
            )
        }
    }
}

// ───────── Progress card ─────────
@Composable
private fun ProgressRing(frac: Float, size: Dp = 58.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 7.dp.toPx()
            val inset = w / 2
            val d = Size(this.size.width - w, this.size.height - w)
            drawArc(Color(0xFFDDF1F6), 0f, 360f, false, Offset(inset, inset), d, style = Stroke(w))
            if (frac > 0f) drawArc(Ex.Cyan, -90f, 360f * frac.coerceIn(0f, 1f), false, Offset(inset, inset), d, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("${(frac * 100).toInt()}%", color = Ex.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
private fun StatTile(icon: Ic?, dot: Color, bg: Color, line: Color, value: Int, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.clip(shape).background(bg).border(1.dp, line, shape).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(dot), contentAlignment = Alignment.Center) {
            if (icon != null) Icon24(icon, Ex.Navy, 16.dp, filled = icon == Ic.Flag)
            else Box(Modifier.size(12.dp).clip(CircleShape).border(2.5.dp, Ex.Soft, CircleShape))
        }
        Column {
            Text("$value", color = Ex.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(label, color = Ex.Soft, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun ProgressCard(index: Int, total: Int, answered: Int, flagged: Int) {
    val frac = if (total == 0) 0f else answered / total.toFloat()
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().shadow(12.dp, shape, ambientColor = Color(0x3300AFCB), spotColor = Color(0x3300AFCB))
            .clip(shape).background(Color.White).border(1.dp, Ex.Line, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SOAL %02d DARI %d".format(index + 1, total), color = Ex.Soft, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${(frac * 100).toInt()}%", color = Ex.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp)
                    Text("Selesai", color = Ex.Soft, fontSize = 17.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            ProgressRing(frac)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0xFFDDF1F6))) {
            if (frac > 0f) Box(Modifier.fillMaxWidth(frac.coerceIn(0.03f, 1f)).fillMaxHeight().clip(CircleShape).background(Ex.CyanGrad))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Ic.Check, Ex.Mint, Ex.MintBg, Ex.MintBg, answered, "Terjawab", Modifier.weight(1f))
            StatTile(null, Color(0xFFDDF1F6), Ex.Pale, Ex.Line, total - answered, "Belum", Modifier.weight(1f))
            StatTile(Ic.Flag, Ex.Amber, Ex.AmberBg, Ex.AmberBg, flagged, "Ditandai", Modifier.weight(1f))
        }
    }
}

// ───────── Question card ─────────
@Composable
internal fun Chip(text: String, bg: Color, fg: Color, line: Color? = null) {
    val shape = RoundedCornerShape(50)
    Text(
        text, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.2.sp, maxLines = 1,
        modifier = Modifier.clip(shape).background(bg).let { if (line != null) it.border(1.dp, line, shape) else it }.padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** White card with the cyan accent bar down its left edge. */
@Composable
internal fun QuestionCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).shadow(12.dp, shape, ambientColor = Color(0x3300AFCB), spotColor = Color(0x3300AFCB))
            .clip(shape).background(Color.White).border(1.dp, Ex.Line, shape),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(Ex.Cyan, Ex.CyanDeep))))
        Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

enum class BtnKind { Primary, Ghost, Amber }

@Composable
internal fun ExButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.Primary, enabled: Boolean = true,
    leading: Ic? = null, trailing: Ic? = null, size: Int = 52,
) {
    val shape = RoundedCornerShape((size / 2.8).dp)
    val base = Modifier.defaultMinSize(minHeight = size.dp)
    val styled = when (kind) {
        BtnKind.Primary -> base.shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = Color(0x6600D9F5), spotColor = Color(0x6600D9F5)).clip(shape)
            .background(if (enabled) Ex.CyanGrad else Brush.horizontalGradient(listOf(Color(0xFFC9DDE3), Color(0xFFC9DDE3))))
        BtnKind.Ghost -> base.clip(shape).background(Ex.Pale).border(1.dp, Ex.Line, shape)
        BtnKind.Amber -> base.shadow(8.dp, shape, ambientColor = Color(0x66FF9F1C), spotColor = Color(0x66FF9F1C)).clip(shape).background(Ex.Amber)
    }
    Row(
        modifier.then(styled).alpha(if (enabled) 1f else 0.5f).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (leading != null) Icon24(leading, Ex.Ink, 18.dp, filled = leading == Ic.Flag)
        Text(text, color = Ex.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Icon24(trailing, Ex.Ink, 18.dp)
    }
}

@Composable
internal fun SquareButton(icon: Ic, dark: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.size(52.dp).alpha(if (enabled) 1f else 0.45f).clip(shape)
            .background(if (dark) Ex.Navy else Ex.Pale).let { if (dark) it else it.border(1.dp, Ex.Line, shape) }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon24(icon, if (dark) Ex.Cyan else Ex.Ink, 22.dp, filled = icon == Ic.Grid) }
}

// ───────── Answer options ─────────
@Composable
internal fun ExamOption(letter: String, text: String, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val box = if (multi) RoundedCornerShape(9.dp) else CircleShape
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 66.dp)
            .let { if (selected) it.shadow(14.dp, shape, ambientColor = Ex.Cyan, spotColor = Ex.Cyan) else it }
            .clip(shape).background(if (selected) Ex.Navy else Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) Ex.Cyan else Ex.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .let { if (selected) it.background(Brush.linearGradient(listOf(Ex.Cyan, Color(0xFFA8F0FF)))) else it.background(Ex.Pale).border(1.dp, Ex.Line, RoundedCornerShape(14.dp)) },
            contentAlignment = Alignment.Center,
        ) { Text(letter, color = Ex.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
        Text(text, Modifier.weight(1f), color = if (selected) Color.White else Ex.Ink, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp)
        Box(
            Modifier.size(30.dp).clip(box).let { if (selected) it.background(Ex.Cyan) else it.border(2.dp, Ex.Line, box) },
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon24(Ic.Check, Ex.Navy, 18.dp) }
    }
}

@Composable
internal fun SaveBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ex.MintBg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon24(Ic.Save, Ex.Ink, 18.dp)
        Text(text, color = Ex.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

// ───────── Bottom navigation ─────────
@Composable
internal fun ExamNavBar(canPrev: Boolean, isLast: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onGrid: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        Modifier.fillMaxWidth().shadow(18.dp, shape, ambientColor = Color(0x3300AFCB), spotColor = Color(0x3300AFCB))
            .clip(shape).background(Color.White).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ExButton("Sebelumnya", onPrev, Modifier.weight(1f), BtnKind.Ghost, enabled = canPrev, leading = Ic.Back)
            SquareButton(Ic.Grid, dark = true, onClick = onGrid)
            if (isLast) ExButton("Selesaikan", onNext, Modifier.weight(1f), BtnKind.Primary, trailing = Ic.Check)
            else ExButton("Selanjutnya", onNext, Modifier.weight(1f), BtnKind.Primary, trailing = Ic.Arrow)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ex.Mint))
            Spacer(Modifier.width(8.dp))
            Text("Jawaban tersimpan otomatis", color = Ex.Soft, fontSize = 12.sp)
        }
    }
}

// ───────── Daftar Soal sheet ─────────
/** status per question: 0 = blank, 1 = answered, 2 = flagged (flag wins over answered, as in the design). */
@Composable
internal fun QuestionSheet(status: List<Int>, current: Int, onPick: (Int) -> Unit, onClose: () -> Unit, onFinish: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xAA061C2A)).tapOnly(onClose), contentAlignment = Alignment.BottomCenter) {
        val top = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        Column(
            Modifier.fillMaxWidth().heightIn(max = 600.dp).clip(top).background(Color(0xFFF7FBFC)).tapOnly { }
                .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(46.dp).height(5.dp).clip(CircleShape).background(Color(0xFFD5EBF1)))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Daftar Soal", color = Ex.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text("Ketuk nomor untuk berpindah • tersimpan otomatis", color = Ex.Soft, fontSize = 14.sp)
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Ex.Pale).border(1.dp, Ex.Line, CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon24(Ic.Close, Ex.Ink, 18.dp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(Ex.Cyan, "Aktif"); Legend(Ex.Mint, "Dijawab"); Legend(Color(0xFFDDF1F6), "Belum"); Legend(Ex.Amber, "Ditandai")
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val cols = 6
                for (r in 0 until (status.size + cols - 1) / cols) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in 0 until cols) {
                            val i = r * cols + c
                            if (i < status.size) NumberCell(i, status[i], i == current, Modifier.weight(1f)) { onPick(i) }
                            else Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            ExButton("Selesaikan Ujian", onFinish, Modifier.fillMaxWidth(), BtnKind.Primary, size = 56)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(label, color = Ex.Soft, fontSize = 13.sp)
    }
}

@Composable
private fun NumberCell(i: Int, state: Int, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val bg = when { active -> Ex.Navy; state == 2 -> Ex.AmberBg; state == 1 -> Ex.MintBg; else -> Ex.Pale }
    val line = when { active -> Ex.Cyan; state == 2 -> Ex.AmberLine; state == 1 -> Ex.MintLine; else -> Ex.Line }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().let { if (active) it.shadow(10.dp, shape, ambientColor = Ex.Cyan, spotColor = Ex.Cyan) else it }
                .clip(shape).background(bg).border(if (active) 2.dp else 1.dp, line, shape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${i + 1}", fontSize = 17.sp, color = if (active) Color.White else Ex.Ink,
                fontWeight = if (state != 0 || active) FontWeight.ExtraBold else FontWeight.Normal,
            )
        }
        if (!active && state != 0) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(14.dp).clip(CircleShape).background(if (state == 2) Ex.Amber else Ex.Mint))
        }
    }
}

// ───────── Finish confirmation ─────────
@Composable
private fun SummaryTile(label: String, value: Int, bg: Color, line: Color, valueColor: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clip(shape).background(bg).border(1.dp, line, shape).padding(14.dp)) {
        Text(label, color = Ex.Soft, fontSize = 13.sp)
        Text("$value", color = valueColor, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
    }
}

/**
 * Same rules as before the redesign: the server only accepts a student-initiated finish when every question is
 * answered and the supervisor PIN is right, so with blanks left this card only offers a way back.
 */
@Composable
internal fun FinishModal(
    total: Int, answered: Int, flagged: Int, pin: String, onPin: (String) -> Unit,
    onConfirm: () -> Unit, onBack: () -> Unit, onShowList: () -> Unit,
) {
    val blank = total - answered
    Box(Modifier.fillMaxSize().background(Color(0xB3061C2A)).imePadding().tapOnly(onBack), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(32.dp)
        Column(
            Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 640.dp).clip(shape).background(Color.White).tapOnly { }
                .verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(72.dp).shadow(22.dp, CircleShape, ambientColor = Ex.Cyan, spotColor = Ex.Cyan).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Ex.Cyan, Color(0xFF9DEFFF)))),
                contentAlignment = Alignment.Center,
            ) { Icon24(if (blank > 0) Ic.Alert else Ic.Shield, Ex.Navy, 34.dp, filled = blank == 0, onTint = Ex.Cyan) }
            Text("Yakin ingin menyelesaikan ujian?", color = Ex.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, textAlign = TextAlign.Center, lineHeight = 28.sp)
            Text("Periksa kembali sebelum mengumpulkan — jawaban terkunci setelah selesai.", color = Ex.Soft, fontSize = 15.sp, textAlign = TextAlign.Center)

            val note = RoundedCornerShape(18.dp)
            if (blank > 0) {
                Text(
                    "Masih ada $blank soal kosong. Semua soal harus dijawab sebelum ujian bisa diselesaikan.",
                    Modifier.fillMaxWidth().clip(note).background(Ex.AmberBg).border(1.dp, Ex.AmberLine, note).padding(14.dp),
                    color = Ex.Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile("Total soal", total, Ex.Pale, Ex.Line, Ex.Ink, Modifier.weight(1f))
                SummaryTile("Sudah dijawab", answered, Ex.MintBg, Ex.MintBg, Ex.Ink, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile("Belum dijawab", blank, Ex.Pale, Ex.Line, if (blank > 0) Ex.Pink else Ex.Ink, Modifier.weight(1f))
                SummaryTile("Ditandai", flagged, Ex.AmberBg, Ex.AmberBg, Ex.Ink, Modifier.weight(1f))
            }
            if (blank == 0) {
                Text("Masukkan PIN dari pengawas untuk mengumpulkan.", color = Ex.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
                PinField(pin, onPin, "PIN pengawas")
                ExButton("Ya, Selesaikan", onConfirm, Modifier.fillMaxWidth(), BtnKind.Primary, enabled = pin.isNotEmpty(), size = 56)
            } else {
                ExButton("Lihat Daftar Soal", onShowList, Modifier.fillMaxWidth(), BtnKind.Primary, leading = null, size = 56)
            }
            ExButton("Kembali Mengerjakan", onBack, Modifier.fillMaxWidth(), BtnKind.Ghost, leading = Ic.Back, size = 52)
        }
    }
}

@Composable
internal fun PinField(value: String, onChange: (String) -> Unit, label: String, dark: Boolean = false) {
    androidx.compose.material3.OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(12)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(label) }, shape = RoundedCornerShape(18.dp),
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ex.Cyan, unfocusedBorderColor = if (dark) Color(0x55FFFFFF) else Ex.Line,
            focusedTextColor = if (dark) Color.White else Ex.Ink, unfocusedTextColor = if (dark) Color.White else Ex.Ink,
            focusedLabelColor = if (dark) Ex.Cyan else Ex.CyanText, unfocusedLabelColor = if (dark) Ex.Muted else Ex.Soft, cursorColor = Ex.Cyan,
        ),
    )
}
