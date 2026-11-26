package com.kawrexam.student.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kawrexam.student.BuildConfig
import com.kawrexam.student.MainViewModel
import com.kawrexam.student.data.ExamItem
import com.kawrexam.student.data.HistoryItem
import kotlin.math.cos
import kotlin.math.sin

private val TAB_LABELS = listOf("Beranda", "Ujian", "Riwayat", "Pengaturan")
private val DONE_STATUSES = setOf("SUBMITTED", "FORCE_SUBMITTED")

/** The signed-in shell: one of four pages and the floating bottom bar. */
@Composable
fun MainShell(vm: MainViewModel) {
    // Stats on the home page and the history page both need the list of finished exams.
    LaunchedEffect(vm.historyLoaded, vm.tab) { if (!vm.historyLoaded) vm.loadHistory(showError = vm.tab == 2) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (vm.tab) {
                0 -> HomePage(vm)
                1 -> ExamsPage(vm)
                2 -> HistoryPage(vm)
                else -> SettingsPage(vm)
            }
        }
        BottomBar(vm.tab, { vm.tab = it }, Modifier.align(Alignment.BottomCenter))
    }
}

private val PAGE_PADDING = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 110.dp)

// ───────── Bottom bar ─────────
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            .shadow(14.dp, shape, ambientColor = Color(0x446D4BE0), spotColor = Color(0x446D4BE0))
            .clip(shape).background(Color.White.copy(alpha = 0.92f)).border(1.dp, Color.White, shape).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TAB_LABELS.forEachIndexed { i, label ->
            val on = i == selected
            val tint = if (on) Violet else InkSoft
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if (on) VioletSoft else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon24(listOf(Ic.Home, Ic.Clipboard, Ic.Clock, Ic.Gear)[i], tint, 26.dp, filled = on, onTint = VioletSoft)
                Text(label, color = tint, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

// ───────── Home ─────────
@Composable
private fun HomePage(vm: MainViewModel) {
    val exams = vm.exams
    val running = exams.count { it.sessionStatus == "IN_PROGRESS" }
    val ready = exams.count { it.canStart }
    val first = exams.firstOrNull { it.canStart }
    val scores = vm.history.filter { it.visible && it.finalScore != null }.map { it.finalScore!! }
    val avg = if (scores.isEmpty()) "-" else "%.1f".format(scores.average())
    val doneCount = if (vm.historyLoaded) vm.history.size else exams.count { it.sessionStatus in DONE_STATUSES }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAGE_PADDING, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LogoMark(52.dp)
                Column(Modifier.weight(1f)) {
                    Wordmark(24)
                    Muted("Ujian Online, Lebih Mudah.")
                }
                GlassCircleButton(Ic.Refresh) { vm.refreshExams() }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                SchoolBuilding(Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = 4.dp).size(width = 270.dp, height = 250.dp))
                Column(Modifier.padding(top = 14.dp)) {
                    Text("Halo, ${vm.student?.name?.substringBefore(' ') ?: "Siswa"}!", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ink, lineHeight = 36.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Siap menghadapi\nujian hari ini?", fontSize = 17.sp, color = InkSoft, lineHeight = 23.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GradientButton(
                            if (running > 0) "Lanjutkan" else "Mulai Ujian",
                            leading = Ic.Play, trailing = Ic.Arrow,
                            onClick = { (exams.firstOrNull { it.sessionStatus == "IN_PROGRESS" && it.canStart } ?: first)?.let { vm.openChecklist(it) } },
                            enabled = (first != null || running > 0) && !vm.busy,
                        )
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.7f)).border(1.dp, Color.White, RoundedCornerShape(50))
                                .clickable { vm.tab = 2 }.padding(horizontal = 18.dp, vertical = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon24(Ic.Clock, Ink, 18.dp)
                                Text("Riwayat", color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Ic.Clipboard, Color(0xFF7C5CF0), "Total Ujian", "${exams.size}", Modifier.weight(1f))
                    StatCard(Ic.Check, Color(0xFF34C58A), "Ujian Selesai", "$doneCount", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Ic.Trophy, Color(0xFFF5A524), "Rata-rata Nilai", avg, Modifier.weight(1f))
                    StatCard(Ic.Clock, Color(0xFF3B82F6), "Siap Dimulai", "$ready", Modifier.weight(1f))
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon24(Ic.Grid, Violet, 22.dp)
                        Text("Ujian Tersedia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Row(Modifier.clickable { vm.tab = 1 }, verticalAlignment = Alignment.CenterVertically) {
                            Text("Lihat Semua", color = InkSoft, fontSize = 13.sp)
                            Icon24(Ic.Chevron, InkSoft, 16.dp)
                        }
                    }
                    ErrorText(vm.error)
                    if (exams.isEmpty() && !vm.busy) Muted("Belum ada ujian untuk kelasmu. Tekan tombol muat ulang saat pengawas sudah membuka ujian.")
                    if (vm.busy && exams.isEmpty()) CircularProgressIndicator(Modifier.size(24.dp))
                    exams.take(2).forEach { e -> ExamCard(vm, e) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon24(Ic.Megaphone, Violet, 22.dp)
                        Text("Pengumuman", style = MaterialTheme.typography.titleLarge)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.6f)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFD9DF)), contentAlignment = Alignment.Center) {
                            Icon24(Ic.Alert, Color(0xFFE23A55), 24.dp)
                        }
                        Column {
                            Text("Siapkan HP-mu sebelum ujian", fontWeight = FontWeight.Bold, color = Ink)
                            Muted("Layar dikunci selama ujian. Pastikan baterai cukup dan koneksi stabil.")
                        }
                    }
                }
            }
        }
    }
}

// ───────── Exams ─────────
@Composable
private fun PageTitle(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Muted(subtitle)
        }
        action?.invoke()
    }
}

@Composable
private fun ExamsPage(vm: MainViewModel) {
    var filter by remember { mutableIntStateOf(0) }
    val labels = listOf("Semua", "Siap", "Berlangsung", "Selesai")
    val shown = vm.exams.filter {
        when (filter) {
            1 -> it.canStart && it.sessionStatus != "IN_PROGRESS"
            2 -> it.sessionStatus == "IN_PROGRESS"
            3 -> it.sessionStatus in DONE_STATUSES
            else -> true
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAGE_PADDING, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Ujian", "${vm.exams.size} ujian untuk kelasmu") { GlassCircleButton(Ic.Refresh) { vm.refreshExams() } } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, l ->
                    val on = i == filter
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (on) Violet else Color.White.copy(alpha = 0.75f))
                            .clickable { filter = i }.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(l, color = if (on) Color.White else Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            }
        }
        item { ErrorText(vm.error) }
        if (shown.isEmpty()) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Muted(if (vm.exams.isEmpty()) "Belum ada ujian untuk kelasmu. Tekan tombol muat ulang saat pengawas sudah membuka ujian." else "Tidak ada ujian di kategori ini.")
                }
            }
        }
        items(shown, key = { it.id }) { e -> GlassCard(Modifier.fillMaxWidth()) { Box(Modifier.padding(12.dp)) { ExamCard(vm, e) } } }
    }
}

@Composable
internal fun ExamCard(vm: MainViewModel, e: ExamItem) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color(0xFFEAE4FF).copy(alpha = 0.8f)))).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF8B6CF7), Color(0xFF5B3FD6)))), contentAlignment = Alignment.Center) {
                Icon24(Ic.Calc, Color.White, 38.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(VioletSoft).padding(horizontal = 10.dp, vertical = 2.dp)) {
                    Text(vm.student?.className?.let { "Kelas $it" } ?: "Ujian", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(e.title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon24(Ic.Clock, InkSoft, 14.dp)
                    Muted("${e.durationMinutes} Menit")
                }
            }
        }
        Muted("Mulai: ${fmtIso(e.startsAt)}" + if (e.endsAt != null) "  ·  Berakhir: ${fmtIso(e.endsAt)}" else "")
        if (!e.canStart && e.reason != null) Text(e.reason, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        GradientButton(
            if (e.sessionStatus == "IN_PROGRESS") "Lanjutkan" else "Mulai", trailing = Ic.Arrow,
            onClick = { vm.openChecklist(e) }, enabled = e.canStart && !vm.busy, modifier = Modifier.align(Alignment.End),
        )
    }
}

// ───────── History ─────────
@Composable
private fun HistoryPage(vm: MainViewModel) {
    val scores = vm.history.filter { it.visible && it.finalScore != null }.map { it.finalScore!! }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAGE_PADDING, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Riwayat", "Ujian yang sudah kamu selesaikan") { GlassCircleButton(Ic.Refresh) { vm.loadHistory(showError = true) } } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Ic.Check, Color(0xFF34C58A), "Selesai", "${vm.history.size}", Modifier.weight(1f))
                StatCard(Ic.Trophy, Color(0xFFF5A524), "Rata-rata", if (scores.isEmpty()) "-" else "%.1f".format(scores.average()), Modifier.weight(1f))
            }
        }
        item { ErrorText(vm.error) }
        if (vm.historyBusy && vm.history.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (vm.history.isEmpty()) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon24(Ic.Folder, Violet, 40.dp)
                    Text("Belum ada riwayat", fontWeight = FontWeight.Bold, color = Ink)
                    Muted("Ujian yang sudah dikumpulkan akan muncul di sini.")
                }
            }
        }
        items(vm.history, key = { it.sessionId }) { h -> HistoryCard(h) }
    }
}

@Composable
private fun HistoryCard(h: HistoryItem) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val ok = h.passed
            val tint = when (ok) { true -> Color(0xFF34C58A); false -> Color(0xFFE5484D); null -> Violet }
            Box(Modifier.size(60.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)).border(2.dp, tint, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (h.visible && h.finalScore != null) "%.0f".format(h.finalScore) else "-", color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(h.title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Ink, maxLines = 2)
                Muted(fmtIso(h.finishedAt) + "  ·  ${h.durationMinutes} menit")
                when {
                    !h.visible -> Muted("Nilai diumumkan oleh pengawas.")
                    ok != null -> Text(if (ok) "Lulus (KKM ${h.kkm?.let { "%.0f".format(it) } ?: "-"})" else "Belum lulus (KKM ${h.kkm?.let { "%.0f".format(it) } ?: "-"})", color = tint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                if (h.correct != null) Muted("Benar ${h.correct}  ·  Salah ${h.incorrect}  ·  Kosong ${h.unanswered}")
                if (h.forced) Muted("Dikumpulkan otomatis.")
            }
        }
    }
}

// ───────── Settings ─────────
@Composable
private fun SettingsPage(vm: MainViewModel) {
    var pwDialog by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val name = vm.student?.name ?: "Siswa"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PAGE_PADDING), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("Pengaturan", "Akun dan informasi aplikasi")
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8B6CF7), Color(0xFF3B22A8)))), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column {
                    Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Ink)
                    Muted(vm.student?.className?.let { "Kelas $it" } ?: "Belum ada kelas")
                }
            }
        }
        notice?.let { Text(it, color = Color(0xFF1E9E6A), fontWeight = FontWeight.SemiBold) }
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(Ic.Lock, "Ganti kata sandi", "Perbarui kata sandi akunmu") { vm.error = null; notice = null; pwDialog = true }
                SettingRow(Ic.Refresh, "Muat ulang data", "Ambil daftar ujian dan riwayat terbaru") { vm.refreshExams(); vm.loadHistory(showError = true) }
                SettingRow(Ic.Shield, "Aturan ujian", if (rules) "Ketuk untuk menutup" else "Yang terjadi selama ujian berlangsung") { rules = !rules }
                if (rules) RuleList(
                    listOf(
                        "Layar dikunci (pin) dan tangkapan layar diblokir.",
                        "Notifikasi dimatikan, hanya alarm yang berbunyi.",
                        "Keluar dari ujian = pelanggaran: layar terkunci 5, 10, lalu 20 menit. Waktu ujian tetap berjalan.",
                        "Keluar paksa dari kunci layar = akunmu ditangguhkan sampai pengawas membukanya.",
                    ),
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconTile(56.dp)
                Column {
                    Wordmark(20)
                    Muted("Ujian Online, Lebih Mudah.")
                    Muted("Versi ${BuildConfig.VERSION_NAME}")
                }
            }
        }
        OutlinedButton(
            onClick = { logoutDialog = true }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon24(Ic.Logout, MaterialTheme.colorScheme.error, 18.dp)
                Text("Keluar dari akun")
            }
        }
    }

    if (pwDialog) PasswordDialog(vm, onClose = { pwDialog = false; vm.error = null }, onDone = { pwDialog = false; notice = "Kata sandi berhasil diganti." })
    if (logoutDialog) AlertDialog(
        onDismissRequest = { logoutDialog = false }, title = { Text("Keluar dari akun?") },
        text = { Text("Kamu harus masuk lagi dengan kode siswa dan kata sandi.") },
        confirmButton = { TextButton(onClick = { logoutDialog = false; vm.logout() }) { Text("Keluar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { logoutDialog = false }) { Text("Batal") } },
    )
}

@Composable
private fun SettingRow(icon: Ic, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(VioletSoft), contentAlignment = Alignment.Center) { Icon24(icon, Violet, 20.dp, filled = true, onTint = VioletSoft) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = Ink)
            Muted(subtitle)
        }
        Icon24(Ic.Chevron, InkSoft, 20.dp)
    }
}

@Composable
private fun PasswordDialog(vm: MainViewModel, onClose: () -> Unit, onDone: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val mismatch = again.isNotEmpty() && again != next
    val kb = KeyboardOptions(keyboardType = KeyboardType.Password)
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Ganti kata sandi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(current, { current = it }, label = { Text("Kata sandi lama") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
                OutlinedTextField(next, { next = it }, label = { Text("Kata sandi baru (min. 8)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
                OutlinedTextField(again, { again = it }, label = { Text("Ulangi kata sandi baru") }, singleLine = true, isError = mismatch, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
                if (mismatch) ErrorText("Kata sandi tidak sama.")
                ErrorText(vm.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !vm.busy && current.isNotEmpty() && next.length >= 8 && next == again, onClick = { vm.changePasswordInSettings(current, next, onDone) }) {
                Text(if (vm.busy) "Memproses…" else "Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } },
    )
}

// ───────── School building ─────────
/**
 * A soft glass-and-concrete school block, drawn in code: tall main wing with a window grid, a lower side wing, a
 * pillared entrance with steps, two trees, and a haze at the left edge so it melts into the greeting text.
 */
@Composable
private fun SchoolBuilding(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        fun x(f: Float) = w * f
        fun y(f: Float) = h * f
        val rr = CornerRadius(6f)

        // ground
        drawRoundRect(Brush.verticalGradient(listOf(Color(0x00B9C6F2), Color(0x55B9C6F2))), Offset(x(.05f), y(.9f)), Size(x(.95f), y(.1f)), CornerRadius(20f))

        // main wing: body, shaded left face, roof slab
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFCFDBF8), Color(0xFFDDD8FB))), Offset(x(.34f), y(.12f)), Size(x(.52f), y(.78f)), rr)
        drawRoundRect(Color(0x66A9BEEB), Offset(x(.34f), y(.12f)), Size(x(.07f), y(.78f)), rr)
        drawRoundRect(Color(0xFFB7C6EC), Offset(x(.31f), y(.085f)), Size(x(.58f), y(.045f)), CornerRadius(8f))
        windowGrid(x(.43f), y(.18f), cols = 4, rows = 7, cw = x(.085f), ch = y(.065f), gx = x(.108f), gy = y(.092f))

        // low side wing
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFD6E0FA), Color(0xFFE4E0FC))), Offset(x(.04f), y(.46f)), Size(x(.32f), y(.44f)), rr)
        drawRoundRect(Color(0xFFB7C6EC), Offset(x(.02f), y(.43f)), Size(x(.36f), y(.04f)), CornerRadius(8f))
        windowGrid(x(.08f), y(.52f), cols = 2, rows = 3, cw = x(.085f), ch = y(.065f), gx = x(.125f), gy = y(.1f))

        // entrance: canopy, pillars, door, steps
        drawRoundRect(Color(0xFF9FB3E8), Offset(x(.5f), y(.68f)), Size(x(.2f), y(.035f)), CornerRadius(6f))
        for (px in listOf(.52f, .64f)) drawRect(Color(0xFFEFF3FF), Offset(x(px), y(.715f)), Size(x(.025f), y(.185f)))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF8FB0EA), Color(0xFFDCE8FF))), Offset(x(.555f), y(.73f)), Size(x(.075f), y(.17f)), CornerRadius(4f))
        for (i in 0 until 3) drawRoundRect(Color(0xFFC3CFF1).copy(alpha = 0.9f - i * 0.2f), Offset(x(.47f - i * .02f), y(.9f + i * .02f)), Size(x(.28f + i * .04f), y(.02f)), CornerRadius(4f))

        // flag pole
        drawLine(Color(0xFF9FB3E8), Offset(x(.6f), y(.12f)), Offset(x(.6f), y(.0f)), strokeWidth = 3f, cap = StrokeCap.Round)
        drawRect(Color(0xFF6D4BE0), Offset(x(.6f), y(.005f)), Size(x(.07f), y(.04f)))

        // trees
        tree(x(.9f), y(.9f), h * .30f, Color(0xFF7FC8B2), Color(0xFF63B39A))
        tree(x(.2f), y(.9f), h * .20f, Color(0xFF93D1BE), Color(0xFF7BC1AB))

        // haze on the left so the text stays readable
        drawRect(Brush.horizontalGradient(listOf(Color(0xFFE4EAFF), Color(0x00E4EAFF)), startX = 0f, endX = x(.4f)), size = Size(x(.4f), h))
    }
}

private fun DrawScope.windowGrid(x0: Float, y0: Float, cols: Int, rows: Int, cw: Float, ch: Float, gx: Float, gy: Float) {
    val glass = Brush.verticalGradient(listOf(Color(0xFF8FB0EA), Color(0xFFEAF1FF)), startY = y0, endY = y0 + rows * gy)
    for (r in 0 until rows) for (c in 0 until cols) {
        val ox = x0 + c * gx; val oy = y0 + r * gy
        drawRoundRect(glass, Offset(ox, oy), Size(cw, ch), CornerRadius(3f))
        drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(ox, oy), Size(cw, ch), CornerRadius(3f), style = Stroke(width = 2.5f))
        drawLine(Color.White.copy(alpha = 0.8f), Offset(ox + cw / 2, oy), Offset(ox + cw / 2, oy + ch), strokeWidth = 2f)
    }
}

private fun DrawScope.tree(cx: Float, baseY: Float, height: Float, light: Color, dark: Color) {
    drawRect(Color(0xFF8E7BB8), Offset(cx - height * .035f, baseY - height * .35f), Size(height * .07f, height * .35f))
    drawCircle(dark, height * .30f, Offset(cx - height * .12f, baseY - height * .55f))
    drawCircle(dark, height * .28f, Offset(cx + height * .16f, baseY - height * .5f))
    drawCircle(light, height * .32f, Offset(cx, baseY - height * .72f))
}

/** Bullet list with drawn dots instead of text bullets. */
@Composable
internal fun RuleList(items: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(Violet))
                Muted(it, Modifier.weight(1f))
            }
        }
    }
}

/** Checklist heading with a drawn status badge: green tick, red cross, or amber dot for "recommended". */
@Composable
internal fun StatusTitle(state: Int, text: String) { // 1 = ok, 0 = missing, 2 = recommended
    val tint = when (state) { 1 -> Color(0xFF1E9E6A); 0 -> Color(0xFFE5484D); else -> Color(0xFFE59A1E) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon24(when (state) { 1 -> Ic.Check; 0 -> Ic.Close; else -> Ic.Dot }, tint, if (state == 2) 16.dp else 14.dp)
        }
        Text(text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}
