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
internal fun fmtIso(iso: String?): String = iso?.let { runCatching { dateFmt.format(Instant.parse(it)) }.getOrNull() } ?: "-"
internal fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%02d:%02d".format(s / 60, s % 60) }
internal fun isAnswered(a: AnswerState?): Boolean = a != null && (a.optionIds.isNotEmpty() || !a.text.isNullOrBlank())

@Composable
fun AppRoot(vm: MainViewModel, dnd: DndManager) {
    // The exam screen paints its own full-bleed background and handles system-bar insets itself.
    if (vm.screen == Screen.InExam) { ExamScreen(vm); return }
    GlassBackground {
        when (val s = vm.screen) {
            Screen.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            Screen.Login -> LoginScreen(vm)
            Screen.ChangePassword -> ChangePasswordScreen(vm)
            Screen.Exams -> MainShell(vm)
            is Screen.Checklist -> ChecklistScreen(vm, s.exam, dnd)
            Screen.InExam -> ExamScreen(vm)
            is Screen.Result -> ResultScreen(vm, s)
            Screen.Suspended -> SuspendedScreen(vm)
        }
    }
}

@Composable
internal fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) =
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
                StatusTitle(if (dndOk) 1 else 0, if (dndOk) "Akses Jangan Ganggu aktif" else "Akses Jangan Ganggu belum diberikan")
                Muted("Dipakai untuk mematikan notifikasi dan panggilan selama ujian. Pengaturan lamamu dikembalikan setelah selesai.")
                if (!dndOk) OutlinedButton(onClick = { dnd.openAccessSettings() }) { Text("Buka pengaturan") }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusTitle(if (vpnOk) 1 else 0, if (vpnOk) "Kunci jaringan ujian aktif" else "Kunci jaringan ujian belum aktif")
                Muted("Memutus internet semua aplikasi lain selama ujian (chat, browser, VPN lain). Hanya KAWREXAM yang tetap online. Tidak ada data yang dikirim atau dibaca lewat fitur ini. Android akan menampilkan permintaan koneksi VPN: pilih OK. Jika kamu memakai VPN lain, VPN itu akan dimatikan.")
                if (!vpnOk) OutlinedButton(onClick = { vm.requestVpn() }) { Text("Aktifkan kunci jaringan") }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusTitle(if (batteryOk) 1 else 2, if (batteryOk) "Hemat baterai tidak membatasi aplikasi" else "Disarankan: izinkan aplikasi tanpa pembatasan baterai")
                if (!batteryOk) OutlinedButton(onClick = { dnd.openBatterySettings() }) { Text("Buka pengaturan") }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Yang terjadi selama ujian", fontWeight = FontWeight.SemiBold)
                RuleList(listOf(
                    "Layar dikunci (pin) dan tangkapan layar diblokir.",
                    "Notifikasi dimatikan, hanya alarm yang berbunyi.",
                    "Aplikasi mencatat kalau kamu keluar dari ujian. Tidak ada data lain yang dipantau.",
                    "Keluar dari ujian = pelanggaran: layar terkunci 5, 10, lalu 20 menit. Waktu ujian tetap berjalan.",
                    "Menyambungkan layar ke TV / layar lain (cast) atau mematikan kunci jaringan = pelanggaran.",
                    "Keluar paksa dari kunci layar = akunmu ditangguhkan. Hanya pengawas yang bisa membukanya, lalu kamu masuk ulang.",
                    "Disarankan: nyalakan mode pesawat dengan Wi-Fi tetap menyala.",
                ))
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
                    if (r.correct != null) Muted("Benar ${r.correct}  ·  Salah ${r.incorrect}  ·  Kosong ${r.unanswered}")
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
internal fun TrackedAlertDialog(
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
