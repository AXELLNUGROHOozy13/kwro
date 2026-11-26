package com.kawrexam.student.guard

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

/** A leave shorter than this is treated as a system interruption (notification pull, call banner) and ignored. */
const val GRACE_MS = 3000

/**
 * Do Not Disturb during exams. "Alarms only" silences notifications and calls but still lets
 * our own warning sound through. The student's previous setting is restored afterwards.
 */
class DndManager(private val ctx: Context) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val prefs = ctx.getSharedPreferences("kawrexam_guard", Context.MODE_PRIVATE)

    fun hasAccess(): Boolean = nm.isNotificationPolicyAccessGranted

    fun openAccessSettings() {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun ignoresBatteryOptimization(): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun openBatterySettings() {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun enableAlarmsOnly() {
        if (!hasAccess()) return
        if (!prefs.contains("prev")) prefs.edit().putInt("prev", nm.currentInterruptionFilter).apply()
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
    }

    fun restore() {
        if (!hasAccess()) return
        val prev = prefs.getInt("prev", -1)
        if (prev > 0) nm.setInterruptionFilter(prev)
        prefs.edit().remove("prev").apply()
    }
}

/** Screen pinning state as reported by Android. */
fun isPinned(ctx: Context): Boolean =
    ctx.getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

object AlertPlayer {
    /** Loud enough to notice, capped at 70% tone volume to stay hearing-safe. Plays on the alarm stream. */
    fun play(ctx: Context) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 70)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 1800)
        }
        runCatching {
            ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

/**
 * True while an external display is attached: HDMI / USB-C monitor, Miracast ("Smart View", "Screen Cast") or an
 * overlay display. Uses the PRESENTATION category so a foldable's built-in cover screen is not mistaken for a cast.
 * Limit: some mirroring paths (MediaProjection / Google Cast of the whole screen) create a private virtual display that
 * apps cannot see. FLAG_SECURE is what keeps the exam out of those: the cast shows a black screen.
 */
fun externalDisplayPresent(ctx: Context): Boolean {
    val dm = ctx.getSystemService(DisplayManager::class.java) ?: return false
    return dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).any { it.displayId != android.view.Display.DEFAULT_DISPLAY }
}
