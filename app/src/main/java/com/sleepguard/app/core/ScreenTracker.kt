package com.sleepguard.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.sleepguard.app.data.SleepPrefs

/**
 * Counts how often and how long the screen is on during a session — the basis of the
 * sleep estimate. Registered by whichever long-lived component is alive (foreground
 * service or accessibility service); registration is idempotent.
 */
object ScreenTracker {
    @Volatile
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val p = SleepPrefs(c)
            if (!p.sessionActive) return
            val now = System.currentTimeMillis()
            when (i.action) {
                Intent.ACTION_SCREEN_ON -> if (p.screenOnSince == 0L) {
                    p.screenOnCount = p.screenOnCount + 1
                    p.screenOnSince = now
                }
                Intent.ACTION_SCREEN_OFF -> {
                    val since = p.screenOnSince
                    if (since > 0L) {
                        p.screenOnMillis = p.screenOnMillis + (now - since).coerceAtLeast(0L)
                        p.screenOnSince = 0L
                    }
                }
            }
        }
    }

    @Synchronized
    fun ensureRegistered(ctx: Context) {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            ctx.applicationContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    @Synchronized
    fun unregister(ctx: Context) {
        if (!registered) return
        runCatching { ctx.applicationContext.unregisterReceiver(receiver) }
        registered = false
    }
}
