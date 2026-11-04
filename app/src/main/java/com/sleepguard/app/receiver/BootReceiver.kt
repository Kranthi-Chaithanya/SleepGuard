package com.sleepguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.sleepguard.app.core.SessionManager
import com.sleepguard.app.core.SleepScheduler
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.data.SleepSession

/**
 * After reboot, app update or a clock change: re-arm alarms and resume a running session,
 * so restarting the phone doesn't escape sleep mode.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val p = SleepPrefs(context)
        val now = System.currentTimeMillis()
        if (p.sessionActive) {
            if (now >= p.sessionEnd) {
                SessionManager.end(context, SleepSession.REASON_COMPLETED)
            } else {
                val on = context.getSystemService(PowerManager::class.java).isInteractive
                p.screenOnSince = if (on) now else 0L
                SleepScheduler.scheduleEnd(context, p.sessionEnd)
                SessionManager.ensureRunning(context)
            }
        } else {
            SessionManager.startIfInWindow(context)
        }
        SleepScheduler.reschedule(context)
    }
}
