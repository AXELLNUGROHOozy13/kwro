package com.kawrexam.student

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kawrexam.student.data.AnswerState
import com.kawrexam.student.data.ExamItem
import com.kawrexam.student.data.HistoryItem
import com.kawrexam.student.data.QuestionDto
import com.kawrexam.student.data.ResultDto
import com.kawrexam.student.data.SessionDto
import com.kawrexam.student.data.StudentInfo
import com.kawrexam.student.data.friendly
import com.kawrexam.student.data.msOf
import com.kawrexam.student.data.scheduleSync
import com.kawrexam.student.guard.AlertPlayer
import com.kawrexam.student.guard.DndManager
import com.kawrexam.student.guard.ExamVpnService
import com.kawrexam.student.guard.externalDisplayPresent
import com.kawrexam.student.guard.isPinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface Screen {
    data object Loading : Screen
    data object Login : Screen
    data object ChangePassword : Screen
    data object Exams : Screen
    data class Checklist(val exam: ExamItem) : Screen
    data object InExam : Screen
    data class Result(val result: ResultDto?, val note: String?) : Screen
    /** Left screen pinning during an exam: only a supervisor can reopen the session. */
    data object Suspended : Screen
}

// Mirrors the server rule (warning n -> minutes). The server's value is the official one.
private val PENALTY_MINUTES = listOf(5, 10, 20)

fun violationLabel(type: String): String = when (type) {
    "LEFT_APP" -> "keluar dari aplikasi ujian"
    "FOCUS_LOST" -> "layar ujian kehilangan fokus"
    "MULTI_WINDOW" -> "membuka mode layar terbagi"
    "OVERLAY_DETECTED" -> "ada jendela lain di atas ujian"
    "PINNING_EXITED" -> "keluar dari kunci layar"
    "SCREEN_CAST" -> "layar disambungkan ke tampilan lain (cast / layar kedua)"
    "VPN_DISABLED" -> "kunci jaringan ujian dimatikan"
    else -> "aktivitas tidak diizinkan"
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).container.repo
    private val dnd = DndManager(app)

    var screen by mutableStateOf<Screen>(Screen.Loading); private set
    var student by mutableStateOf<StudentInfo?>(null); private set
    var exams by mutableStateOf<List<ExamItem>>(emptyList()); private set
    var history by mutableStateOf<List<HistoryItem>>(emptyList()); private set
    var historyLoaded by mutableStateOf(false); private set
    var historyBusy by mutableStateOf(false); private set
    /** Bottom-bar tab shown while the student is on the exam list. */
    var tab by mutableStateOf(0)
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)

    // Exam state
    var session by mutableStateOf<SessionDto?>(null); private set
    val answers = mutableStateMapOf<String, AnswerState>()
    /** Questions the student marked to re-check ("Ditandai"). Local only: a study aid, never sent or scored. */
    val flagged = mutableStateMapOf<String, Boolean>()
    var index by mutableIntStateOf(0); private set
    var nowMs by mutableLongStateOf(0L); private set
    var deadlineMs by mutableLongStateOf(0L); private set
    var penaltyUntilMs by mutableLongStateOf(0L); private set
    var warningCount by mutableIntStateOf(0); private set
    var warning by mutableStateOf<String?>(null)
    var online by mutableStateOf(true); private set
    var finishing by mutableStateOf(false); private set
    var resumeTick by mutableIntStateOf(0)
    var pinRequest by mutableIntStateOf(0); private set
    var vpnRequest by mutableIntStateOf(0); private set
    var suspendedDelivered by mutableStateOf(false); private set

    /** Count of the app's own dialogs on screen. They take window focus, which must not be read as leaving the exam. */
    private var ownDialogs = 0
    val ownDialogOpen: Boolean get() = ownDialogs > 0
    fun dialogOpened() { ownDialogs++ }
    fun dialogClosed() { ownDialogs = maxOf(0, ownDialogs - 1) }
    fun requestVpn() { vpnRequest++ }

    val inExam: Boolean get() = screen is Screen.InExam

    // Server time = anchor + elapsedRealtime, so changing the phone's clock cannot extend the exam.
    private var anchorServer = System.currentTimeMillis()
    private var anchorElapsed = SystemClock.elapsedRealtime()
    private fun anchor(serverIso: String) { anchorServer = msOf(serverIso); anchorElapsed = SystemClock.elapsedRealtime() }
    private fun serverNow() = anchorServer + (SystemClock.elapsedRealtime() - anchorElapsed)

    private var loop: Job? = null
    private var guardArmedAt = Long.MAX_VALUE
    private var wasPinned = false
    private val textJobs = HashMap<String, Job>()
    private var vpnDownReported = false
    private var castSeen = 0
    private var castReported = false
    private var delivering: Job? = null

    init { viewModelScope.launch { bootstrap() } }

    // ── Startup ──
    private suspend fun bootstrap() {
        if (!repo.hasLogin()) { screen = Screen.Login; return }
        // Restarting the app must not undo a suspension that the server has not been told about yet.
        if (repo.isSuspended()) { student = repo.student(); screen = Screen.Suspended; deliverSuspension(); return }
        student = repo.student()
        repo.activeSessionId()?.let { if (resume(it)) return }
        ExamVpnService.stop(getApplication()) // no exam is open: never leave the network lock on
        if (student?.mustChangePassword == true) screen = Screen.ChangePassword else { screen = Screen.Exams; loadExams() }
    }

    private suspend fun resume(sid: String): Boolean = try {
        val dto = repo.session(sid)
        when (dto.status) {
            "IN_PROGRESS" -> { enterExam(dto); true }
            "SUBMITTED", "FORCE_SUBMITTED" -> { val r = repo.submit(sid); repo.forget(sid); screen = Screen.Result(r, null); true }
            else -> { repo.forget(sid); false }
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        if (e is HttpException) repo.forget(sid) // closed or missing on the server
        false
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true; error = null
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is HttpException && e.code() == 401 && screen !is Screen.Login) { doLogout(); error = "Sesi berakhir. Masuk lagi." }
                else error = friendly(e)
            } finally { busy = false }
        }
    }

    // ── Auth and exam list ──
    fun login(code: String, password: String) = launchBusy {
        val r = repo.login(code.trim(), password)
        student = r.student
        if (r.student?.mustChangePassword == true) screen = Screen.ChangePassword else { screen = Screen.Exams; loadExams() }
    }

    fun changePassword(current: String, new: String) = launchBusy {
        repo.changePassword(current, new)
        student = student?.copy(mustChangePassword = false)?.also { repo.saveStudent(it) }
        screen = Screen.Exams
        loadExams()
    }

    fun refreshExams() = launchBusy { loadExams() }
    /** Quiet by default: the home stats load it in the background, so a failure there must not pop an error. */
    fun loadHistory(showError: Boolean = false) {
        viewModelScope.launch {
            historyBusy = true
            try { history = repo.history().data; historyLoaded = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (showError) error = friendly(e) }
            finally { historyBusy = false }
        }
    }
    fun changePasswordInSettings(current: String, new: String, onDone: () -> Unit) = launchBusy {
        repo.changePassword(current, new)
        onDone()
    }
    private suspend fun loadExams() { val r = repo.exams(); anchor(r.serverTime); exams = r.data }

    fun openChecklist(exam: ExamItem) { error = null; screen = Screen.Checklist(exam) }
    fun backToExams() { error = null; historyLoaded = false; ExamVpnService.stop(getApplication()); screen = Screen.Exams; refreshExams() }
    fun backToLogin() { error = null; student = null; screen = Screen.Login }
    fun logout() { viewModelScope.launch { doLogout() } }
    private suspend fun doLogout() {
        loop?.cancel(); releaseGuards(); repo.logout()
        session = null; student = null; exams = emptyList(); history = emptyList(); historyLoaded = false; tab = 0; screen = Screen.Login
    }

    /** Do Not Disturb and the network lock only exist while an exam is open. */
    private fun releaseGuards() { dnd.restore(); ExamVpnService.stop(getApplication()) }

    // ── Exam lifecycle ──
    fun startExam(exam: ExamItem, token: String) = launchBusy {
        val dto = repo.start(exam.id, token.trim())
        repo.setActiveSession(dto.sessionId)
        enterExam(dto)
    }

    private suspend fun enterExam(dto: SessionDto) {
        answers.clear(); answers.putAll(repo.mergedAnswers(dto)); flagged.clear()
        session = dto; index = 0; error = null; warning = null; finishing = false; wasPinned = false
        anchor(dto.serverTime); deadlineMs = msOf(dto.deadlineAt); nowMs = serverNow()
        warningCount = dto.warningCount; penaltyUntilMs = dto.penaltyUntil?.let(::msOf) ?: 0L
        // Violations only count once the screen is pinned (or 20 s have passed), so the pin dialog is not penalised.
        guardArmedAt = SystemClock.elapsedRealtime() + 20_000
        vpnDownReported = false; castSeen = 0; castReported = false
        dnd.enableAlarmsOnly()
        screen = Screen.InExam
        pinRequest++
        startLoop()
    }

    private fun startLoop() {
        loop?.cancel()
        loop = viewModelScope.launch {
            var n = 0
            while (isActive) {
                delay(1000)
                nowMs = serverNow()
                checkPinned()
                checkGuards()
                if (!finishing && deadlineMs > 0 && nowMs >= deadlineMs) autoFinish()
                if (++n % 15 == 0) launch { heartbeat() }
            }
        }
    }

    private fun checkPinned() {
        val pinned = isPinned(getApplication())
        if (pinned && !wasPinned) { wasPinned = true; guardArmedAt = SystemClock.elapsedRealtime() + 1500 }
        else if (!pinned && wasPinned) {
            wasPinned = false
            // Leaving the pin suspends the account (see onViolation). If the guard was not armed yet, just pin again.
            onViolation("PINNING_EXITED", null, SystemClock.elapsedRealtime())
            if (screen is Screen.InExam) { guardArmedAt = SystemClock.elapsedRealtime() + 20_000; pinRequest++ }
        }
    }

    /** Network lock and second-display checks, once per second. Each is reported once per episode. */
    private fun checkGuards() {
        val now = SystemClock.elapsedRealtime()
        if (!ExamVpnService.active) {
            if (!vpnDownReported) {
                vpnDownReported = true
                onViolation("VPN_DISABLED", null, now)
                guardArmedAt = now + 20_000 // the consent dialog steals focus for a moment
                vpnRequest++ // ask the Activity to turn the lock back on
            }
        } else vpnDownReported = false

        if (externalDisplayPresent(getApplication())) {
            if (++castSeen >= 2 && !castReported) { castReported = true; onViolation("SCREEN_CAST", null, now) } // 2 s in a row: no flicker
        } else { castSeen = 0; castReported = false }
    }

    private suspend fun heartbeat() {
        val s = session ?: return
        try {
            val h = repo.heartbeat(s.sessionId)
            anchor(h.serverTime); deadlineMs = msOf(h.deadlineAt)
            warningCount = maxOf(warningCount, h.warningCount)
            h.penaltyUntil?.let { penaltyUntilMs = maxOf(penaltyUntilMs, msOf(it)) }
            online = true
            when (h.status) {
                "IN_PROGRESS" -> mergeSync(repo.syncAll())
                "SUBMITTED", "FORCE_SUBMITTED" -> finishRemote()
                else -> if (h.suspended) suspendRemote() else leaveExam("Ujian dihentikan oleh pengawas. Jawabanmu tersimpan.")
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            online = false
            scheduleSync(getApplication())
        }
    }

    private fun mergeSync(r: com.kawrexam.student.data.ViolationsResp?) {
        r ?: return
        warningCount = maxOf(warningCount, r.warningCount)
        r.penaltyUntil?.let { penaltyUntilMs = maxOf(penaltyUntilMs, msOf(it)) }
    }

    private fun syncNow() {
        viewModelScope.launch {
            try { mergeSync(repo.syncAll()); online = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { online = false; scheduleSync(getApplication()) }
        }
    }

    private suspend fun finishRemote() {
        val s = session ?: return
        try { repo.syncAll(); finishWith(repo.submit(s.sessionId)) } catch (e: CancellationException) { throw e } catch (e: Exception) { /* next heartbeat retries */ }
    }

    // ── Answers (autosave) ──
    fun goTo(i: Int) { val n = session?.questions?.size ?: return; index = i.coerceIn(0, n - 1) }

    fun selectOption(q: QuestionDto, optionId: String) {
        val cur = answers[q.id]?.optionIds ?: emptySet()
        val next = if (q.type == "MULTIPLE_SELECT") (if (optionId in cur) cur - optionId else cur + optionId) else setOf(optionId)
        answers[q.id] = AnswerState(next, null)
        viewModelScope.launch { persist(q.id) }
    }

    fun toggleFlag(qid: String) { if (flagged[qid] == true) flagged.remove(qid) else flagged[qid] = true }

    /** Eraser button: empties the answer. Saved like any other change, so the server sees it as unanswered again. */
    fun clearAnswer(q: QuestionDto) {
        if (answers[q.id] == null) return
        textJobs[q.id]?.cancel()
        answers[q.id] = AnswerState(emptySet(), null)
        viewModelScope.launch { persist(q.id) }
    }

    fun setText(q: QuestionDto, text: String) {
        answers[q.id] = AnswerState(emptySet(), text)
        textJobs[q.id]?.cancel()
        textJobs[q.id] = viewModelScope.launch { delay(600); persist(q.id) }
    }

    private suspend fun persist(qid: String) {
        val s = session ?: return
        val a = answers[qid] ?: return
        repo.saveAnswer(s.sessionId, qid, a.optionIds, a.text)
        syncNow()
    }

    // ── Finish ──
    /** Student-initiated finish: every question must be answered and the supervisor PIN must be right. */
    fun submit(pin: String) {
        val s = session ?: return
        if (finishing) return
        if (s.questions.any { q -> answers[q.id].let { a -> a == null || (a.optionIds.isEmpty() && a.text.isNullOrBlank()) } }) {
            error = "Semua soal harus dijawab sebelum ujian bisa diselesaikan."; return
        }
        viewModelScope.launch {
            finishing = true
            try {
                textJobs.filter { it.value.isActive }.keys.toList().forEach { it.let { qid -> textJobs[qid]?.cancel(); persist(qid) } }
                repo.syncAll()
                finishWith(repo.submit(s.sessionId, pin))
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                finishing = false
                error = friendly(e) + if (e is java.io.IOException) " Jawabanmu aman di HP. Coba kirim lagi saat tersambung." else ""
            }
        }
    }

    var unlocking by mutableStateOf(false); private set

    /** Supervisor gives a PIN; the server zeroes the violation count and lifts the lock. */
    fun unlock(pin: String) {
        val s = session ?: return
        if (unlocking) return
        viewModelScope.launch {
            unlocking = true
            try {
                repo.resetViolations(s.sessionId, pin)
                warningCount = 0; penaltyUntilMs = 0L; warning = null
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = friendly(e) }
            unlocking = false
        }
    }

    /** Time is up: keep retrying until the answers reach the server. */
    private fun autoFinish() {
        val s = session ?: return
        finishing = true
        viewModelScope.launch {
            while (session != null) {
                try { repo.syncAll(); finishWith(repo.submit(s.sessionId)); return@launch }
                catch (e: CancellationException) { throw e }
                catch (e: HttpException) { if (e.code() == 409) { leaveExam(friendly(e)); return@launch }; delay(5000) }
                catch (e: Exception) { delay(5000) }
            }
        }
    }

    private fun finishWith(r: ResultDto) {
        loop?.cancel(); releaseGuards()
        val sid = session?.sessionId
        session = null; finishing = false
        viewModelScope.launch { sid?.let { repo.forget(it) } }
        screen = Screen.Result(r, null)
    }

    private fun leaveExam(note: String) {
        loop?.cancel(); releaseGuards()
        session = null; finishing = false
        viewModelScope.launch { repo.setActiveSession(null) }
        screen = Screen.Result(null, note)
    }

    // ── Violations ──
    fun onViolation(type: String, durationMs: Int?, startedElapsed: Long) {
        val s = session ?: return
        if (screen !is Screen.InExam || finishing || startedElapsed < guardArmedAt) return
        if (type == "PINNING_EXITED") { suspendLocally(s.sessionId, System.currentTimeMillis()); return }
        warningCount += 1
        val minutes = PENALTY_MINUTES[minOf(warningCount - 1, PENALTY_MINUTES.size - 1)]
        penaltyUntilMs = maxOf(penaltyUntilMs, serverNow() + minutes * 60_000L)
        warning = "Pelanggaran ke-$warningCount: ${violationLabel(type)}. Layar dikunci $minutes menit."
        AlertPlayer.play(getApplication())
        val occurredAt = System.currentTimeMillis() - (durationMs ?: 0)
        viewModelScope.launch { repo.recordViolation(s.sessionId, type, occurredAt, durationMs); syncNow() }
    }

    // ── Suspension (forced exit from screen pinning) ──
    /**
     * The student left screen pinning. The exam closes on this phone at once, the event is reported, and the login is
     * cleared once the server knows. Coming back needs a supervisor to reopen the session and a fresh sign-in.
     */
    private fun suspendLocally(sid: String, occurredAt: Long) {
        loop?.cancel()
        finishing = false; session = null
        releaseGuards()
        screen = Screen.Suspended
        suspendedDelivered = false
        AlertPlayer.play(getApplication())
        viewModelScope.launch {
            repo.setSuspended(true)
            repo.recordViolation(sid, "PINNING_EXITED", occurredAt, null)
            deliverSuspension()
        }
    }

    /** The server says this session is suspended (for example reported from another path): same end state. */
    private fun suspendRemote() {
        loop?.cancel()
        finishing = false; session = null
        releaseGuards()
        screen = Screen.Suspended
        suspendedDelivered = false
        viewModelScope.launch { repo.setSuspended(true); deliverSuspension() }
    }

    /** Retries until the queued violation reached the server (or the server already revoked this login), then signs out. */
    private fun deliverSuspension() {
        if (delivering?.isActive == true) return
        delivering = viewModelScope.launch {
            while (isActive) {
                try { repo.syncAll(); break }
                catch (e: CancellationException) { throw e }
                catch (e: HttpException) { if (e.code() == 401) break; delay(5000) } // 401: the server already ended this login
                catch (e: Exception) { delay(5000) }
            }
            repo.logout() // also clears the suspended flag: the next start needs a fresh sign-in
            suspendedDelivered = true
        }
    }

    fun penaltyLeftMs(): Long = (penaltyUntilMs - nowMs).coerceAtLeast(0)
}
