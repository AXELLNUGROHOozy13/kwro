package com.kawrexam.student.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Every icon in the app is drawn here on a 24-unit grid: no emoji, no icon font, no image files. */
enum class Ic { Home, Clipboard, Clock, Gear, Check, Close, Dot, Trophy, Grid, Megaphone, Folder, Lock, Refresh, Logout, Play, Arrow, Chevron, Shield, Alert, Calc, Back, Flag, Save, Eraser }

@Composable
fun Icon24(kind: Ic, tint: Color, size: Dp = 24.dp, filled: Boolean = false, onTint: Color = Color.White) {
    Canvas(Modifier.size(size)) { drawIcon(kind, tint, onTint, filled) }
}

private fun DrawScope.drawIcon(kind: Ic, c: Color, on: Color, filled: Boolean) {
    val u = size.width / 24f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    val sw = 2f * u
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, col: Color = c, w: Float = sw) = drawLine(col, o(x1, y1), o(x2, y2), strokeWidth = w, cap = StrokeCap.Round)
    fun path(col: Color = c, fill: Boolean = false, build: Path.() -> Unit) {
        val p = Path().apply(build)
        if (fill) drawPath(p, col) else drawPath(p, col, style = st)
    }
    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, col: Color = c, fill: Boolean = false) {
        if (fill) drawRoundRect(col, o(x, y), Size(w * u, h * u), CornerRadius(r * u))
        else drawRoundRect(col, o(x, y), Size(w * u, h * u), CornerRadius(r * u), style = st)
    }
    fun P(p: Path, x: Float, y: Float, first: Boolean = false) { if (first) p.moveTo(x * u, y * u) else p.lineTo(x * u, y * u) }

    when (kind) {
        Ic.Home -> path(fill = filled) {
            P(this, 3.5f, 11f, true); P(this, 12f, 3.5f); P(this, 20.5f, 11f); P(this, 20.5f, 20.5f); P(this, 14.5f, 20.5f)
            P(this, 14.5f, 14.5f); P(this, 9.5f, 14.5f); P(this, 9.5f, 20.5f); P(this, 3.5f, 20.5f); close()
        }
        Ic.Clipboard -> {
            rrect(4.5f, 4f, 15f, 17f, 3f, fill = filled)
            rrect(8.5f, 2f, 7f, 4f, 1.6f, fill = true)
            val lc = if (filled) on else c
            line(8.5f, 11f, 15.5f, 11f, lc); line(8.5f, 15f, 15.5f, 15f, lc)
        }
        Ic.Clock -> {
            if (filled) drawCircle(c, 9.5f * u, o(12f, 12f)) else drawCircle(c, 9.5f * u, o(12f, 12f), style = st)
            val hc = if (filled) on else c
            line(12f, 12f, 12f, 6.8f, hc); line(12f, 12f, 15.8f, 14.2f, hc)
        }
        Ic.Gear -> {
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0); val cs = cos(a).toFloat(); val sn = sin(a).toFloat()
                line(12f + 7.6f * cs, 12f + 7.6f * sn, 12f + 10.4f * cs, 12f + 10.4f * sn, w = 3.2f * u)
            }
            if (filled) drawCircle(c, 7.4f * u, o(12f, 12f)) else drawCircle(c, 7.4f * u, o(12f, 12f), style = st)
            drawCircle(if (filled) on else c, 2.8f * u, o(12f, 12f), style = Stroke(width = 1.8f * u))
        }
        Ic.Check -> path(build = { P(this, 4.5f, 12.5f, true); P(this, 9.8f, 17.8f); P(this, 19.5f, 6.5f) })
        Ic.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Ic.Dot -> drawCircle(c, 4f * u, o(12f, 12f))
        Ic.Trophy -> {
            path(fill = true) {
                moveTo(7f * u, 3.5f * u); lineTo(17f * u, 3.5f * u); lineTo(17f * u, 9f * u)
                cubicTo(17f * u, 12.2f * u, 14.8f * u, 14f * u, 12f * u, 14f * u)
                cubicTo(9.2f * u, 14f * u, 7f * u, 12.2f * u, 7f * u, 9f * u); close()
            }
            path { P(this, 7f, 5.5f, true); P(this, 4f, 5.5f); cubicTo(4f * u, 9.5f * u, 5.4f * u, 11f * u, 7.4f * u, 11.4f * u) }
            path { P(this, 17f, 5.5f, true); P(this, 20f, 5.5f); cubicTo(20f * u, 9.5f * u, 18.6f * u, 11f * u, 16.6f * u, 11.4f * u) }
            line(12f, 14f, 12f, 18f)
            rrect(7.5f, 18f, 9f, 3f, 1.3f, fill = true)
        }
        Ic.Grid -> {
            rrect(3.5f, 3.5f, 7.5f, 7.5f, 2.2f, fill = true); rrect(13f, 3.5f, 7.5f, 7.5f, 2.2f, fill = true)
            rrect(3.5f, 13f, 7.5f, 7.5f, 2.2f, fill = true); rrect(13f, 13f, 7.5f, 7.5f, 2.2f, fill = true)
        }
        Ic.Megaphone -> {
            path(fill = true) { P(this, 3.5f, 9.5f, true); P(this, 8f, 9.5f); P(this, 16f, 4.5f); P(this, 16f, 19.5f); P(this, 8f, 14.5f); P(this, 3.5f, 14.5f); close() }
            line(7.5f, 15f, 9f, 20f)
            path { moveTo(19f * u, 9f * u); cubicTo(20.8f * u, 10.6f * u, 20.8f * u, 13.4f * u, 19f * u, 15f * u) }
        }
        Ic.Folder -> path(fill = filled) { P(this, 3f, 6.5f, true); P(this, 9.5f, 6.5f); P(this, 11.5f, 9f); P(this, 21f, 9f); P(this, 21f, 19.5f); P(this, 3f, 19.5f); close() }
        Ic.Lock -> {
            rrect(5f, 11f, 14f, 10f, 3f, fill = true)
            path { moveTo(8f * u, 11f * u); lineTo(8f * u, 8f * u); cubicTo(8f * u, 5.5f * u, 10f * u, 3.8f * u, 12f * u, 3.8f * u); cubicTo(14f * u, 3.8f * u, 16f * u, 5.5f * u, 16f * u, 8f * u); lineTo(16f * u, 11f * u) }
            drawCircle(on, 1.6f * u, o(12f, 15.5f))
        }
        Ic.Refresh -> {
            drawArc(c, -50f, 280f, false, o(4f, 4f), Size(16f * u, 16f * u), style = st)
            val ex = 12f + 8f * cos(Math.toRadians(230.0)).toFloat(); val ey = 12f + 8f * sin(Math.toRadians(230.0)).toFloat()
            path(fill = true) { P(this, ex + 3.4f, ey - 2.4f, true); P(this, ex - 3.4f, ey - 2.2f); P(this, ex + 0.6f, ey + 3.6f); close() }
        }
        Ic.Logout -> {
            path { P(this, 10f, 4f, true); P(this, 5f, 4f); P(this, 5f, 20f); P(this, 10f, 20f) }
            line(9f, 12f, 20f, 12f)
            path { P(this, 16f, 8f, true); P(this, 20f, 12f); P(this, 16f, 16f) }
        }
        Ic.Play -> path(fill = true) { P(this, 7f, 4.5f, true); P(this, 19.5f, 12f); P(this, 7f, 19.5f); close() }
        Ic.Arrow -> { line(4f, 12f, 19.5f, 12f); path { P(this, 13.5f, 6f, true); P(this, 19.5f, 12f); P(this, 13.5f, 18f) } }
        Ic.Chevron -> path { P(this, 9f, 5.5f, true); P(this, 15.5f, 12f); P(this, 9f, 18.5f) }
        Ic.Shield -> {
            path(fill = filled) {
                moveTo(12f * u, 2.8f * u); lineTo(19.5f * u, 5.8f * u); lineTo(19.5f * u, 11.5f * u)
                cubicTo(19.5f * u, 16f * u, 16.4f * u, 19.6f * u, 12f * u, 21.2f * u)
                cubicTo(7.6f * u, 19.6f * u, 4.5f * u, 16f * u, 4.5f * u, 11.5f * u); lineTo(4.5f * u, 5.8f * u); close()
            }
            path(col = if (filled) on else c) { P(this, 8.5f, 12f, true); P(this, 11f, 14.5f); P(this, 15.5f, 9.5f) }
        }
        Ic.Alert -> { line(12f, 5.5f, 12f, 14f, w = 2.6f * u); drawCircle(c, 1.7f * u, o(12f, 18.6f)) }
        Ic.Back -> { line(19.5f, 12f, 4f, 12f); path { P(this, 10.5f, 6f, true); P(this, 4.5f, 12f); P(this, 10.5f, 18f) } }
        Ic.Flag -> {
            line(6f, 3.5f, 6f, 21f)
            path(fill = true) { P(this, 6f, 4.5f, true); P(this, 19f, 4.5f); P(this, 15.5f, 9f); P(this, 19f, 13.5f); P(this, 6f, 13.5f); close() }
        }
        Ic.Save -> {
            rrect(4f, 4f, 16f, 16f, 3f, fill = filled)
            val lc = if (filled) on else c
            rrect(8f, 4f, 7f, 5f, 1f, col = lc)
            rrect(7.5f, 13f, 9f, 7f, 1.5f, col = lc)
        }
        Ic.Eraser -> {
            path { P(this, 9.5f, 19.5f, true); P(this, 4.5f, 14.5f); P(this, 13f, 6f); P(this, 19f, 12f); P(this, 11.5f, 19.5f); close() }
            line(9f, 19.5f, 20f, 19.5f); line(8.2f, 10.8f, 14.2f, 16.8f)
        }
        Ic.Calc -> {
            val w = 1.9f * u
            line(4.5f, 7f, 9.5f, 7f, w = w); line(7f, 4.5f, 7f, 9.5f, w = w)          // +
            line(14.5f, 7f, 19.5f, 7f, w = w)                                           // -
            line(4.8f, 14.8f, 9.2f, 19.2f, w = w); line(9.2f, 14.8f, 4.8f, 19.2f, w = w) // x
            line(14.5f, 17f, 19.5f, 17f, w = w); drawCircle(c, 1.1f * u, o(17f, 14f)); drawCircle(c, 1.1f * u, o(17f, 20f)) // ÷
        }
    }
}
