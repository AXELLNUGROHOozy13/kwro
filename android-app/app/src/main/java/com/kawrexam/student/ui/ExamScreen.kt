package com.kawrexam.student.ui

import android.app.Activity
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kawrexam.student.MainViewModel
import com.kawrexam.student.data.QuestionDto

private fun typeLabel(type: String) = when (type) {
    "MULTIPLE_CHOICE" -> "PILIHAN GANDA"
    "TRUE_FALSE" -> "BENAR / SALAH"
    "MULTIPLE_SELECT" -> "PILIHAN MAJEMUK"
    "ESSAY" -> "URAIAN"
    else -> "ISIAN SINGKAT"
}

private fun hintFor(type: String) = when (type) {
    "MULTIPLE_SELECT" -> "Pilih semua jawaban yang benar. Jawaban tersimpan otomatis dan bisa diganti kapan pun sebelum Selesai."
    "ESSAY", "SHORT_ANSWER" -> "Tulis jawabanmu. Tersimpan otomatis dan bisa diubah kapan pun sebelum Selesai."
    else -> "Pilih salah satu jawaban. Jawaban tersimpan otomatis dan bisa diganti kapan pun sebelum Selesai."
}

@Composable
fun ExamScreen(vm: MainViewModel) {
    val s = vm.session ?: return
    val n = s.questions.size
    val q = s.questions.getOrNull(vm.index) ?: return
    val ctx = LocalContext.current
    val left = (vm.deadlineMs - vm.nowMs).coerceAtLeast(0)
    val answered = s.questions.count { isAnswered(vm.answers[it.id]) }
    val flaggedCount = s.questions.count { vm.flagged[it.id] == true }
    val battery = remember(vm.nowMs / 30_000) { ctx.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1 }
    var sheet by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var finishPin by remember { mutableStateOf("") }
    var unlockPin by remember { mutableStateOf("") }

    // The header is dark navy, so the status-bar icons must be light while the exam is open.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (controller != null && previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    // The back button never leaves an exam; it only closes our own sheet or modal.
    BackHandler(enabled = true) { if (confirm) { confirm = false; finishPin = "" } else if (sheet) sheet = false }

    val picked = vm.answers[q.id]?.optionIds ?: emptySet()
    val letters = q.options.mapIndexedNotNull { i, o -> if (o.id in picked) ('A' + i).toString() else null }
    val isOptionType = q.type == "MULTIPLE_CHOICE" || q.type == "TRUE_FALSE" || q.type == "MULTIPLE_SELECT"
    val answeredHere = isAnswered(vm.answers[q.id])

    Box(Modifier.fillMaxSize().background(Ex.Canvas)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ExamHeader(s.title, vm.student?.name ?: "", vm.student?.className, mmss(left), timerLevel(left), vm.online, battery)

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProgressCard(vm.index, n, answered, flaggedCount)

                QuestionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip(typeLabel(q.type), Color(0xFFE6FAFF), Ex.CyanText, Ex.Line)
                        Chip("SOAL %02d".format(vm.index + 1), Ex.Navy, Ex.Cyan)
                    }
                    Text(q.text, color = Ex.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 30.sp)
                    if (q.imageUrl != null) {
                        Text(
                            "Soal ini punya gambar, tetapi versi aplikasi ini belum bisa menampilkannya. Tanyakan ke pengawas.",
                            color = Ex.Soft, fontSize = 14.sp,
                        )
                    }
                    Text(hintFor(q.type), color = Ex.Soft, fontSize = 15.sp, lineHeight = 22.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val flagged = vm.flagged[q.id] == true
                        ExButton(
                            if (flagged) "Ditandai • Periksa lagi" else "Tandai untuk diperiksa", { vm.toggleFlag(q.id) },
                            Modifier.weight(1f), if (flagged) BtnKind.Amber else BtnKind.Ghost, leading = Ic.Flag, size = 56,
                        )
                        SquareButton(Ic.Eraser, dark = false, onClick = { vm.clearAnswer(q) }, enabled = answeredHere)
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isOptionType) "PILIH JAWABAN" else "TULIS JAWABAN", Modifier.weight(1f), color = Ex.CyanText, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 2.sp)
                    if (letters.isNotEmpty()) Text(letters.joinToString(", ") + " terpilih", color = Ex.CyanText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                AnswerArea(vm, q)

                if (answeredHere) {
                    val what = if (letters.isNotEmpty()) "Jawaban ${letters.joinToString(", ")}" else "Jawabanmu"
                    SaveBanner("$what tersimpan • ${mmss(left)} tersisa")
                }
            }

            ExamNavBar(
                canPrev = vm.index > 0, isLast = vm.index >= n - 1,
                onPrev = { vm.goTo(vm.index - 1) },
                onNext = { if (vm.index >= n - 1) confirm = true else vm.goTo(vm.index + 1) },
                onGrid = { sheet = true },
            )
        }

        // Daftar Soal: drawn inside this window (a separate dialog window would count as leaving the exam).
        AnimatedVisibility(sheet, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
            QuestionSheet(
                status = s.questions.map { qq -> if (vm.flagged[qq.id] == true) 2 else if (isAnswered(vm.answers[qq.id])) 1 else 0 },
                current = vm.index,
                onPick = { vm.goTo(it); sheet = false },
                onClose = { sheet = false },
                onFinish = { sheet = false; confirm = true },
            )
        }

        if (confirm) {
            FinishModal(
                total = n, answered = answered, flagged = flaggedCount, pin = finishPin, onPin = { finishPin = it },
                onConfirm = { val p = finishPin; confirm = false; finishPin = ""; vm.submit(p) },
                onBack = { confirm = false; finishPin = "" },
                onShowList = { confirm = false; finishPin = ""; sheet = true },
            )
        }

        if (vm.penaltyLeftMs() > 0) {
            Box(Modifier.fillMaxSize().background(Color(0xF2061C2A)).tapOnly { }.imePadding(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Layar terkunci", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                    Text(mmss(vm.penaltyLeftMs()), color = Ex.Cyan, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp)
                    Text("Pelanggaran ke-${vm.warningCount}. Waktu ujian tetap berjalan.", color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text("Atau minta PIN ke pengawas untuk membuka kunci:", color = Ex.Muted, textAlign = TextAlign.Center)
                    PinField(unlockPin, { unlockPin = it }, "PIN pengawas", dark = true)
                    ExButton("Buka kunci", { vm.unlock(unlockPin); unlockPin = "" }, Modifier.fillMaxWidth(), BtnKind.Primary, enabled = unlockPin.isNotEmpty() && !vm.unlocking, size = 56)
                }
            }
        }

        if (vm.finishing) {
            Box(Modifier.fillMaxSize().background(Color(0xB3061C2A)).tapOnly { }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Ex.Cyan)
                    Spacer(Modifier.height(12.dp))
                    Text("Mengirim jawaban…", color = Color.White, fontWeight = FontWeight.SemiBold)
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
}

@Composable
private fun AnswerArea(vm: MainViewModel, q: QuestionDto) {
    when (q.type) {
        "MULTIPLE_CHOICE", "TRUE_FALSE", "MULTIPLE_SELECT" -> {
            val picked = vm.answers[q.id]?.optionIds ?: emptySet()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                q.options.forEachIndexed { i, o ->
                    ExamOption(('A' + i).toString(), o.text, selected = o.id in picked, multi = q.type == "MULTIPLE_SELECT") { vm.selectOption(q, o.id) }
                }
            }
        }
        else -> OutlinedTextField(
            value = vm.answers[q.id]?.text.orEmpty(), onValueChange = { vm.setText(q, it) },
            label = { Text(if (q.type == "ESSAY") "Jawaban uraian" else "Jawaban singkat") },
            minLines = if (q.type == "ESSAY") 6 else 1, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ex.Cyan, unfocusedBorderColor = Ex.Line, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                focusedTextColor = Ex.Ink, unfocusedTextColor = Ex.Ink, focusedLabelColor = Ex.CyanText, cursorColor = Ex.Cyan,
            ),
        )
    }
}
