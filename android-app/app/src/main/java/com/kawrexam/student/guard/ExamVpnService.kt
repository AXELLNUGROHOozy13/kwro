package com.kawrexam.student.guard

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.FileInputStream

/**
 * A local VPN that goes nowhere. All traffic of every other app is routed into this tunnel and dropped, so no other
 * app (chat, browser, search, another VPN) has internet while an exam is open. KawRexam itself is excluded from the
 * tunnel and keeps using the real network. Nothing leaves the phone through this service and nothing is inspected.
 *
 * Android allows one VPN at a time: turning this on replaces any VPN the student had, and turning it off (or starting
 * another VPN) triggers onRevoke(), which the exam logic reports as VPN_DISABLED.
 */
class ExamVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var sink: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { close(); stopSelf(); return START_NOT_STICKY }
        if (tun == null) establish()
        return START_NOT_STICKY // never come back by itself after the app was killed
    }

    private fun establish() {
        val fd = try {
            Builder()
                .setSession("KAWREXAM ujian")
                .setMtu(1400)
                .addAddress("10.77.0.2", 32).addRoute("0.0.0.0", 0)
                .addAddress("fd77:77::2", 128).addRoute("::", 0)
                .addDisallowedApplication(packageName) // KawRexam bypasses the tunnel
                .setBlocking(true)
                .establish()
        } catch (_: Exception) { null }
        if (fd == null) { active = false; return }
        tun = fd
        active = true
        sink = Thread({
            val input = FileInputStream(fd.fileDescriptor)
            val buf = ByteArray(32 * 1024)
            try { while (!Thread.currentThread().isInterrupted && input.read(buf) >= 0) { /* drop every packet */ } } catch (_: Exception) { }
        }, "kawrexam-vpn-sink").apply { isDaemon = true; start() }
    }

    private fun close() {
        active = false
        sink?.interrupt(); sink = null
        runCatching { tun?.close() }; tun = null
    }

    override fun onRevoke() { close(); super.onRevoke() } // VPN turned off in Settings, or another VPN took over
    override fun onDestroy() { close(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.kawrexam.student.STOP_EXAM_VPN"

        /** Compose-observable: the checklist and the exam screen react to it. */
        var active by mutableStateOf(false)
            private set

        fun start(ctx: Context) { ctx.startService(Intent(ctx, ExamVpnService::class.java)) }
        fun stop(ctx: Context) { if (active) ctx.startService(Intent(ctx, ExamVpnService::class.java).setAction(ACTION_STOP)) }
    }
}
