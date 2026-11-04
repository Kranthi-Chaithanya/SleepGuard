package com.sleepguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sleepguard.app.core.SessionManager
import com.sleepguard.app.core.SleepScheduler
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.data.SleepSession

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START -> SessionManager.startIfInWindow(context)
            ACTION_END -> {
                val p = SleepPrefs(context)
                if (p.sessionActive && System.currentTimeMillis() >= p.sessionEnd - 60_000L) {
                    SessionManager.end(context, SleepSession.REASON_COMPLETED)
                }
            }
        }
        SleepScheduler.reschedule(context)
    }

    companion object {
        const val ACTION_START = "com.sleepguard.app.action.START_SLEEP"
        const val ACTION_END = "com.sleepguard.app.action.END_SLEEP"
    }
}
