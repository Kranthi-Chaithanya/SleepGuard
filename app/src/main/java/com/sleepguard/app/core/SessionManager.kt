package com.sleepguard.app.core

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sleepguard.app.data.BlockMode
import com.sleepguard.app.data.ScheduleCalc
import com.sleepguard.app.data.SessionStore
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.data.SleepSession
import com.sleepguard.app.service.SleepService

/** Starts and ends sleep sessions. Every entry point (alarm, boot, UI) goes through here. */
object SessionManager {
    private const val TAG = "SleepGuard"

    /** Manual "Start sleep now": runs until the next wake-up time. */
    fun startNow(ctx: Context) {
        val p = SleepPrefs(ctx)
        start(ctx, ScheduleCalc.nextWakeAfter(System.currentTimeMillis(), p))
    }

    /** Starts a session if we're inside a scheduled window. Returns true if a session is running. */
    fun startIfInWindow(ctx: Context): Boolean {
        val p = SleepPrefs(ctx)
        if (p.sessionActive) {
            ensureRunning(ctx)
            return true
        }
        val now = System.currentTimeMillis()
        if (now < p.skipUntil) return false
        // small tolerance in case the alarm fires a moment early
        val end = ScheduleCalc.activeWindowEnd(now + 60_000L, p) ?: return false
        start(ctx, end)
        return true
    }

    fun start(ctx: Context, plannedEnd: Long) {
        val p = SleepPrefs(ctx)
        if (p.sessionActive) return
        val now = System.currentTimeMillis()
        val screenOn = ctx.getSystemService(PowerManager::class.java).isInteractive
        p.beginSession(now, plannedEnd, p.mode, screenOn)
        SleepScheduler.scheduleEnd(ctx, plannedEnd)
        ScreenTracker.ensureRegistered(ctx)
        ensureRunning(ctx)
    }

    fun ensureRunning(ctx: Context) {
        ScreenTracker.ensureRegistered(ctx)
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, SleepService::class.java))
        } catch (e: Exception) {
            // Android 12+ can refuse background FGS starts (e.g. inexact alarm). Blocking
            // still works through the accessibility service; only the notification is missing.
            Log.w(TAG, "Could not start foreground service", e)
        }
    }

    fun canEndEarly(p: SleepPrefs): Boolean = p.sessionActive && p.sessionMode == BlockMode.BASIC

    /** Ends the running session and saves it to history. */
    fun end(ctx: Context, reason: String) {
        val p = SleepPrefs(ctx)
        if (!p.sessionActive) return
        val now = System.currentTimeMillis()

        var screenMs = p.screenOnMillis
        val since = p.screenOnSince
        if (since > 0L) screenMs += (now - since).coerceAtLeast(0L)

        val session = SleepSession(
            start = p.sessionStart,
            end = now,
            plannedEnd = p.sessionEnd,
            mode = p.sessionMode,
            screenOnCount = p.screenOnCount,
            screenOnMillis = screenMs,
            blockedAttempts = p.blockedAttempts,
            endReason = reason,
        )
        SessionStore.add(ctx, session)

        if (reason != SleepSession.REASON_COMPLETED) p.skipUntil = p.sessionEnd
        p.clearSession()

        SleepScheduler.cancelEnd(ctx)
        ScreenTracker.unregister(ctx)
        ctx.stopService(Intent(ctx, SleepService::class.java))
        Notifications.showMorning(ctx, session)
        SleepScheduler.reschedule(ctx)
    }
}
