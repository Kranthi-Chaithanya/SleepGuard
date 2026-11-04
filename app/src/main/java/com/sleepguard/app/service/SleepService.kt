package com.sleepguard.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import com.sleepguard.app.core.Notifications
import com.sleepguard.app.core.ScreenTracker
import com.sleepguard.app.core.SessionManager
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.data.SleepSession

/**
 * Foreground service that keeps the process alive overnight, shows the ongoing
 * "Sleep mode is on" notification and acts as a watchdog that ends the session on time.
 */
class SleepService : Service() {

    private lateinit var prefs: SleepPrefs
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!prefs.sessionActive) {
                stopSelf()
                return
            }
            if (System.currentTimeMillis() >= prefs.sessionEnd) {
                SessionManager.end(this@SleepService, SleepSession.REASON_COMPLETED)
                return
            }
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = SleepPrefs(this)
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must always be called promptly after startForegroundService.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, Notifications.ID_ACTIVE, Notifications.active(this, prefs), type)

        if (!prefs.sessionActive) {
            stopSelf()
            return START_NOT_STICKY
        }
        ScreenTracker.ensureRegistered(this)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
