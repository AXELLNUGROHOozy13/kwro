package com.kawrexam.student

import android.content.res.Configuration
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.kawrexam.student.guard.DndManager
import com.kawrexam.student.guard.ExamVpnService
import com.kawrexam.student.guard.GRACE_MS
import com.kawrexam.student.ui.AppRoot
import com.kawrexam.student.ui.KawrexamTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    // Leave tracking: a leave is "paused or unfocused" until the app is resumed AND focused again.
    private var leftAt = 0L
    private var leftType: String? = null
    private var resumed = false
    private var focused = false

    // The system's "Connection request" dialog for the exam network lock; the student approves it once in the checklist.
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) ExamVpnService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No screenshots, screen recording or recents thumbnails, anywhere in the app.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.filterTouchesWhenObscured = true // ignore taps while another app draws over us
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setHideOverlayWindows(true)

        val dnd = DndManager(this)
        setContent {
            KawrexamTheme {
                val inExam = vm.screen is Screen.InExam
                // Pin the screen while an exam is open; ask again if the student unpinned.
                LaunchedEffect(inExam, vm.pinRequest) {
                    if (inExam) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        runCatching { startLockTask() }
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        runCatching { stopLockTask() }
                    }
                }
                LaunchedEffect(vm.vpnRequest) {
                    if (vm.vpnRequest > 0) {
                        val consent = VpnService.prepare(this@MainActivity)
                        if (consent != null) vpnConsent.launch(consent) else ExamVpnService.start(this@MainActivity)
                    }
                }
                AppRoot(vm, dnd)
            }
        }
    }

    override fun onPause() { super.onPause(); resumed = false; markLeft("LEFT_APP") }

    override fun onResume() { super.onResume(); resumed = true; vm.resumeTick++; checkReturn() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        focused = hasFocus
        if (hasFocus) checkReturn() else markLeft("FOCUS_LOST")
    }

    @Deprecated("Deprecated in Java")
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode && vm.inExam) vm.onViolation("MULTI_WINDOW", null, SystemClock.elapsedRealtime())
    }

    private fun markLeft(type: String) {
        if (!vm.inExam) return
        // The app's own warning / confirm dialogs take window focus; that is not leaving the exam. A real pause still counts.
        if (type == "FOCUS_LOST" && vm.ownDialogOpen) return
        if (leftAt == 0L) { leftAt = SystemClock.elapsedRealtime(); leftType = type }
        else if (type == "LEFT_APP") leftType = type // a real pause outranks a plain focus loss
    }

    private fun checkReturn() {
        if (!(resumed && focused) || leftAt == 0L) return
        val started = leftAt
        val duration = (SystemClock.elapsedRealtime() - started).toInt()
        val type = leftType ?: "LEFT_APP"
        leftAt = 0L; leftType = null
        // Short interruptions (notification pull, call banner) are ignored; real leaves are reported.
        if (duration >= GRACE_MS) vm.onViolation(type, duration, started)
    }
}
