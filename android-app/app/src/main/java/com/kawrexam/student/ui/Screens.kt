package com.kawrexam.student.ui

import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kawrexam.student.MainViewModel
import com.kawrexam.student.Screen
import com.kawrexam.student.data.AnswerState
import com.kawrexam.student.data.ExamItem
import com.kawrexam.student.data.QuestionDto
import com.kawrexam.student.guard.DndManager
import com.kawrexam.student.guard.ExamVpnService
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("id", "ID")).withZone(ZoneId.systemDefault())
private fun fmtIso(iso: String?): String = iso?.let { runCatching { dateFmt.format(Instant.parse(it)) }.getOrNull() } ?: "-"
private fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%02d:%02d".format(s / 60, s % 60) }
private fun isAnswered(a: AnswerState?): Boolean = a != null && (a.optionIds.isNotEmpty() || !a.text.isNullOrBlank())

@Composable
fun AppRoot(vm: MainViewModel, dnd: DndManager) {
    GlassBackground {
        when (val s = vm.screen) {
            Screen.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            Screen.Login -> LoginScreen(vm)
            Screen.ChangePassword -> ChangePasswordScreen(vm)
            Screen.Exams -> ExamsScreen(vm)
            is Screen.Checklist -> ChecklistScreen(vm, s.exam, dnd)
            Screen.InExam -> ExamScreen(vm)
            is Screen.Result -> ResultScreen(vm, s)
            Screen.Suspended -> SuspendedScreen(vm)
        }
    }
}

@Composable
private fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = modifier)

// ───────── Login ─────────
@Composable
fun LoginScreen(vm: MainViewModel) {
    var code by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        BrandBlock()
        Spacer(Modifier.height(28.dp))
        Muted("Masuk dengan kode siswa dari pengawas.", Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code, onValueChange = { if (it.length <= 7 && it.all(Char::isDigit)) code = it },
                    label = { Text("Kode siswa (7 angka)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, label = { Text("Kata sandi") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(vm.error)
                Button(onClick = { vm.login(code, pw) }, enabled = !vm.busy && code.length == 7 && pw.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.busy) "Memproses…" else "Masuk")
                }
            }
        }
    }
}

@Composable
fun ChangePasswordScreen(vm: MainViewModel) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val mismatch = again.isNotEmpty() && again != next
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text("Buat kata sandi baru", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Muted("Kata sandi awalmu sama dengan kode login. Ganti dulu sebelum mulai ujian.")
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val kb = KeyboardOptions(keyboardType = KeyboardType.Password)
                OutlinedTextField(current, { current = it }, label = { Text("Kata sandi lama") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(next, { next = it }, label = { Text("Kata sandi baru (min. 8 karakter)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(again, { again = it }, label = { Text("Ulangi kata sandi baru") }, singleLine = true, isError = mismatch, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb, modifier = Modifier.fillMaxWidth())
                if (mismatch) ErrorText("Kata sandi tidak sama.")
                ErrorText(vm.error)
                Button(onClick = { vm.changePassword(current, next) }, enabled = !vm.busy && current.isNotEmpty() && next.length >= 8 && next == again, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.busy) "Memproses…" else "Simpan")
                }
            }
        }
    }
}

// ───────── Exam list (home) ─────────
@Composable
fun ExamsScreen(vm: MainViewModel) {
    val exams = vm.exams
    val done = exams.count { it.sessionStatus != null && it.sessionStatus != "IN_PROGRESS" }
    val running = exams.count { it.sessionStatus == "IN_PROGRESS" }
    val ready = exams.count { it.canStart }
    val first = exams.firstOrNull { it.canStart }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LogoMark(52.dp)
                Column(Modifier.weight(1f)) {
                    Wordmark(24)
                    Muted("Ujian Online, Lebih Mudah.")
                }
                GlassCircleButton("↻") { vm.refreshExams() }
                GlassCircleButton("⎋") { vm.logout() }
            }
        }
        item {
            Box(Modifier.fillMaxWidth()) {
                SchoolBuilding(Modifier.align(Alignment.CenterEnd).size(width = 190.dp, height = 170.dp))
                Column(Modifier.padding(top = 18.dp)) {
                    Text("Halo, ${vm.student?.name?.substringBefore(' ') ?: "Siswa"}! 👋", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ink, lineHeight = 36.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Siap menghadapi\nujian hari ini?", fontSize = 17.sp, color = InkSoft, lineHeight = 23.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GradientButton(if (running > 0) "▶  Lanjutkan  →" else "▶  Mulai Ujian  →", onClick = { (exams.firstOrNull { it.sessionStatus == "IN_PROGRESS" && it.canStart } ?: first)?.let { vm.openChecklist(it) } }, enabled = (first != null || running > 0) && !vm.busy)
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.6f)).clickable(enabled = !vm.busy) { vm.refreshExams() }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                        ) { Text("↻  Muat Ulang", color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("📋", Color(0xFF7C5CF0), "Total Ujian", "${exams.size}", Modifier.weight(1f))
                    StatCard("✔", Color(0xFF34C58A), "Ujian Selesai", "$done", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("🏆", Color(0xFFF5A524), "Siap Dimulai", "$ready", Modifier.weight(1f))
                    StatCard("⏱", Color(0xFF3B82F6), "Berlangsung", "$running", Modifier.weight(1f))
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("▦", color = Violet, fontSize = 22.sp)
                        Text("Ujian Tersedia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    }
                    ErrorText(vm.error)
                    if (exams.isEmpty() && !vm.busy) Muted("Belum ada ujian untuk kelasmu. Tekan Muat Ulang saat pengawas sudah membuka ujian.")
                    if (vm.busy && exams.isEmpty()) CircularProgressIndicator(Modifier.size(24.dp))
                    exams.forEach { e -> ExamCard(vm, e) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📢", fontSize = 22.sp)
                        Text("Pengumuman", style = MaterialTheme.typography.titleLarge)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.6f)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFD9DF)), contentAlignment = Alignment.Center) {
                            Text("!", color = Color(0xFFE23A55), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
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

@Composable
private fun ExamCard(vm: MainViewModel, e: ExamItem) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color(0xFFEAE4FF).copy(alpha = 0.8f)))).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF8B6CF7), Color(0xFF5B3FD6)))), contentAlignment = Alignment.Center) {
                Text("+ −\n× ÷", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 22.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(VioletSoft).padding(horizontal = 10.dp, vertical = 2.dp)) {
                    Text(vm.student?.className?.let { "Kelas $it" } ?: "Ujian", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(e.title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink, maxLines = 2)
                Muted("⏱ ${e.durationMinutes} Menit")
            }
        }
        Muted("Mulai: ${fmtIso(e.startsAt)}" + if (e.endsAt != null) "  •  Berakhir: ${fmtIso(e.endsAt)}" else "")
        if (!e.canStart && e.reason != null) Text(e.reason, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        GradientButton(
            if (e.sessionStatus == "IN_PROGRESS") "Lanjutkan  →" else "Mulai  →",
            onClick = { vm.openChecklist(e) }, enabled = e.canStart && !vm.busy, modifier = Modifier.align(Alignment.End),
        )
    }
}

/** Soft, translucent school block behind the greeting. Pure drawing, no image asset. */
@Composable
private fun SchoolBuilding(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        val wall = Brush.verticalGradient(listOf(Color(0xCCBFD0F5), Color(0x99D8D2FF)))
        drawRoundRect(wall, topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.82f, h * 0.88f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f))
        drawRoundRect(Color(0xAA9DB4EC), topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.88f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f))
        val cols = 4; val rows = 5
        for (r in 0 until rows) for (c in 0 until cols) {
            val x = w * (0.40f + c * 0.145f); val y = h * (0.20f + r * 0.15f)
            drawRoundRect(Color(0xCCF4F7FF), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(w * 0.105f, h * 0.10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
        }
    }
}

// ───────── Pre-exam checklist ─────────
@Composable
fun ChecklistScreen(vm: MainViewModel, exam: ExamItem, dnd: DndManager) {
    val tick = vm.resumeTick // re-check permissions when the student returns from Settings
    val dndOk = remember(tick) { dnd.hasAccess() }
    val batteryOk = remember(tick) { dnd.ignoresBatteryOptimization() }
    val vpnOk = ExamVpnService.active // observable: flips as soon as the student approves the system dialog
    var consent by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(exam.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Muted("Siapkan HP-mu sebelum mulai. Setelah ujian dimulai, pengaturan tidak bisa dibuka.")
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (dndOk) "✔ Akses Jangan Ganggu aktif" else "✖ Akses Jangan Ganggu belum diberikan", fontWeight = FontWeight.SemiBold)
                Muted("Dipakai untuk mematikan notifikasi dan panggilan selama ujian. Pengaturan lamamu dikembalikan setelah selesai.")
                if (!dndOk) OutlinedButton(onClick = { dnd.openAccessSettings() }) { Text("Buka pengaturan") }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (vpnOk) "✔ Kunci jaringan ujian aktif" else "✖ Kunci jaringan ujian belum aktif", fontWeight = FontWeight.SemiBold)
                Muted("Memutus internet semua aplikasi lain selama ujian (chat, browser, VPN lain). Hanya KAWREXAM yang tetap online. Tidak ada data yang dikirim atau dibaca lewat fitur ini. Android akan menampilkan permintaan koneksi VPN: pilih OK. Jika kamu memakai VPN lain, VPN itu akan dimatikan.")
                if (!vpnOk) OutlinedButton(onClick = { vm.requestVpn() }) { Text("Aktifkan kunci jaringan") }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (batteryOk) "✔ Hemat baterai tidak membatasi aplikasi" else "• Disarankan: izinkan aplikasi tanpa pembatasan baterai", fontWeight = FontWeight.SemiBold)
                if (!batteryOk) OutlinedButton(onClick = { dnd.openBatterySettings() }) { Text("Buka pengaturan") }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Yang terjadi selama ujian", fontWeight = FontWeight.SemiBold)
                Muted("• Layar dikunci (pin) dan tangkapan layar diblokir.\n• Notifikasi dimatikan, hanya alarm yang berbunyi.\n• Aplikasi mencatat kalau kamu keluar dari ujian. Tidak ada data lain yang dipantau.\n• Keluar dari ujian = pelanggaran: layar terkunci 5, 10, lalu 20 menit. Waktu ujian tetap berjalan.\n• Menyambungkan layar ke TV / layar lain (cast) atau mematikan kunci jaringan = pelanggaran.\n• Keluar paksa dari kunci layar = akunmu ditangguhkan. Hanya pengawas yang bisa membukanya, lalu kamu masuk ulang.\n• Disarankan: nyalakan mode pesawat dengan Wi-Fi tetap menyala.")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { consent = !consent }) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text("Saya mengerti dan setuju.")
                }
            }
        }
        ErrorText(vm.error)
        Button(onClick = { vm.startExam(exam) }, enabled = dndOk && vpnOk && consent && !vm.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.busy) "Memuat soal…" else "Mulai ujian")
        }
        OutlinedButton(onClick = { vm.backToExams() }, modifier = Modifier.fillMaxWidth()) { Text("Batal") }
    }
}

// ───────── Exam ─────────
@Composable
fun ExamScreen(vm: MainViewModel) {
    val s = vm.session ?: return
    val n = s.questions.size
    val q = s.questions.getOrNull(vm.index) ?: return
    BackHandler(enabled = true) { } // the back button never leaves an exam
    val ctx = LocalContext.current
    val left = (vm.deadlineMs - vm.nowMs).coerceAtLeast(0)
    val answered = s.questions.count { isAnswered(vm.answers[it.id]) }
    val battery = remember(vm.nowMs / 30_000) { ctx.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1 }
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    var confirm by remember { mutableStateOf(false) }
    var finishPin by remember { mutableStateOf("") }
    var unlockPin by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, fontWeight = FontWeight.Bold, maxLines = 1)
                    Muted(vm.student?.name ?: "")
                }
                Muted("$clock  •  ${if (battery >= 0) "$battery%" else "-"}  •  ${if (vm.online) "Online" else "Offline"}")
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Soal ${vm.index + 1} dari $n", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TimerPill(mmss(left), low = left < 300_000)
            }
            LinearProgressIndicator(progress = { if (n == 0) 0f else answered / n.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            GlassCard(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuestionBody(vm, q)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(s.questions) { i, qq ->
                    val done = isAnswered(vm.answers[qq.id])
                    val bg = when {
                        i == vm.index -> MaterialTheme.colorScheme.primary
                        done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable { vm.goTo(i) }, contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = if (i == vm.index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.goTo(vm.index - 1) }, enabled = vm.index > 0, modifier = Modifier.weight(1f)) { Text("Sebelumnya") }
                OutlinedButton(onClick = { vm.goTo(vm.index + 1) }, enabled = vm.index < n - 1, modifier = Modifier.weight(1f)) { Text("Selanjutnya") }
                Button(onClick = { confirm = true }, modifier = Modifier.weight(1f)) { Text("Selesai") }
            }
        }

        if (vm.penaltyLeftMs() > 0) {
            Box(Modifier.fillMaxSize().background(Color(0xF01B1747)).clickable(enabled = true) { }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("Layar terkunci", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(mmss(vm.penaltyLeftMs()), color = Color(0xFFB9A4FF), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text("Pelanggaran ke-${vm.warningCount}. Waktu ujian tetap berjalan.", color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text("Atau minta PIN ke pengawas untuk membuka kunci:", color = Color.White, textAlign = TextAlign.Center)
                    OutlinedTextField(
                        unlockPin, { unlockPin = it.filter(Char::isDigit).take(12) }, label = { Text("PIN pengawas") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.unlock(unlockPin); unlockPin = "" }, enabled = unlockPin.isNotEmpty() && !vm.unlocking) { Text("Buka kunci") }
                }
            }
        }
        if (vm.finishing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(enabled = true) { }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Mengirim jawaban…", color = Color.White)
                }
            }
        }
    }

    vm.warning?.let { w ->
        TrackedAlertDialog(vm, onDismissRequest = { }, title = { Text("Peringatan") }, text = { Text(w) },
            confirmButton = { TextButton(onClick = { vm.warning = null }) { Text("Mengerti") } })
    }
    vm.error?.let { e ->
        TrackedAlertDialog(vm, onDismissRequest = { vm.error = null }, title = { Text("Gagal") }, text = { Text(e) },
            confirmButton = { TextButton(onClick = { vm.error = null }) { Text("OK") } })
    }
    if (confirm) {
        val blank = n - answered
        TrackedAlertDialog(
            vm, onDismissRequest = { confirm = false; finishPin = "" }, title = { Text(if (blank > 0) "Belum bisa selesai" else "Selesaikan ujian?") },
            text = {
                if (blank > 0) Text("Masih ada $blank soal belum dijawab. Jawab semua soal dulu.")
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Semua soal sudah dijawab. Masukkan PIN dari pengawas. Setelah selesai, jawaban tidak bisa diubah.")
                    OutlinedTextField(
                        finishPin, { finishPin = it.filter(Char::isDigit).take(12) }, label = { Text("PIN pengawas") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            },
            confirmButton = {
                if (blank == 0) TextButton(enabled = finishPin.isNotEmpty(), onClick = { val p = finishPin; confirm = false; finishPin = ""; vm.submit(p) }) { Text("Selesai") }
            },
            dismissButton = { TextButton(onClick = { confirm = false; finishPin = "" }) { Text(if (blank > 0) "Kembali ke soal" else "Batal") } },
        )
    }
}

@Composable
private fun QuestionBody(vm: MainViewModel, q: QuestionDto) {
    Text(q.text, style = MaterialTheme.typography.bodyLarge)
    if (q.imageUrl != null) Muted("Soal ini punya gambar, tetapi versi aplikasi ini belum bisa menampilkannya. Tanyakan ke pengawas.")
    val picked = vm.answers[q.id]?.optionIds ?: emptySet()
    when (q.type) {
        "MULTIPLE_CHOICE", "TRUE_FALSE" -> q.options.forEach { o ->
            OptionCard(o.text, selected = o.id in picked, multi = false) { vm.selectOption(q, o.id) }
        }
        "MULTIPLE_SELECT" -> {
            Muted("Pilih semua jawaban yang benar.")
            q.options.forEach { o -> OptionCard(o.text, selected = o.id in picked, multi = true) { vm.selectOption(q, o.id) } }
        }
        else -> OutlinedTextField(
            value = vm.answers[q.id]?.text.orEmpty(), onValueChange = { vm.setText(q, it) },
            label = { Text(if (q.type == "ESSAY") "Jawaban uraian" else "Jawaban singkat") },
            minLines = if (q.type == "ESSAY") 6 else 1, modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ───────── Result ─────────
@Composable
fun ResultScreen(vm: MainViewModel, s: Screen.Result) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val r = s.result
                if (r == null) {
                    Text("Ujian selesai", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(s.note ?: "", textAlign = TextAlign.Center)
                } else if (!r.visible) {
                    Text("Jawaban terkirim", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(r.message ?: "Hasil akan diumumkan oleh pengawas.", textAlign = TextAlign.Center)
                } else {
                    Text("Ujian selesai", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("%.1f".format(r.finalScore ?: 0.0), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (r.passed != null) Text(if (r.passed) "Lulus (KKM ${r.kkm?.let { "%.0f".format(it) } ?: "-"})" else "Belum lulus (KKM ${r.kkm?.let { "%.0f".format(it) } ?: "-"})")
                    if (r.correct != null) Muted("Benar ${r.correct} • Salah ${r.incorrect} • Kosong ${r.unanswered}")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.backToExams() }, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke daftar ujian") }
            }
        }
    }
}

/**
 * AlertDialog that tells the guard "this dialog is ours". A dialog is a separate window and takes focus from the exam;
 * without this, reading a dialog for more than 3 seconds would be reported as leaving the exam.
 */
@Composable
private fun TrackedAlertDialog(
    vm: MainViewModel,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    DisposableEffect(Unit) { vm.dialogOpened(); onDispose { vm.dialogClosed() } }
    AlertDialog(onDismissRequest = onDismissRequest, title = title, text = text, confirmButton = confirmButton, dismissButton = dismissButton)
}

// ───────── Suspended (left screen pinning) ─────────
@Composable
fun SuspendedScreen(vm: MainViewModel) {
    BackHandler(enabled = !vm.suspendedDelivered) { }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Akun ditangguhkan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Kamu keluar dari kunci layar saat ujian berlangsung. Ujian di HP ini dihentikan dan jawabanmu yang sudah tersimpan aman.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Muted("Hanya pengawas yang bisa membuka kembali ujianmu. Setelah dibuka, masuk ulang dengan kode dan kata sandimu.", Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        if (!vm.suspendedDelivered) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Muted("Mengirim laporan ke server… Pastikan HP tersambung internet.")
        } else {
            Button(onClick = { vm.backToLogin() }) { Text("Ke halaman masuk") }
        }
    }
}
