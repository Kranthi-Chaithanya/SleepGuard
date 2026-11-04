package com.sleepguard.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.sleepguard.app.BlockActivity
import com.sleepguard.app.core.AppRules
import com.sleepguard.app.core.ScreenTracker
import com.sleepguard.app.core.SessionManager
import com.sleepguard.app.data.BlockMode
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.data.SleepSession

/**
 * Watches which app comes to the foreground. During a sleep session, anything that isn't
 * the phone/dialer, clock, keyboard, launcher, SleepGuard itself or a user-allowed app is
 * sent home and the "Time to sleep" screen is shown.
 */
class BlockerService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SleepPrefs
    private val handler = Handler(Looper.getMainLooper())

    private var systemAllowed: Set<String> = emptySet()
    private var launchers: Set<String> = emptySet()
    private var lastBlockedPkg: String? = null
    private var lastBlockedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = SleepPrefs(this)
        prefs.register(this)
        refreshAllowed()
        if (prefs.sessionActive) {
            SessionManager.ensureRunning(this)
            handler.postDelayed({ checkCurrentWindow() }, 500)
        }
    }

    private val recheck = Runnable { checkCurrentWindow() }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (!prefs.sessionActive) return
        when (key) {
            SleepPrefs.K_ACTIVE -> {
                // A session just started: refresh lists and block whatever is open right now.
                refreshAllowed()
                ScreenTracker.ensureRegistered(this)
                handler.removeCallbacks(recheck)
                handler.postDelayed(recheck, 400)
            }
            SleepPrefs.K_TEMP_UNTIL -> {
                // A 5-minute snooze started: re-check the open app when it runs out.
                val delay = (prefs.tempAllowUntil - System.currentTimeMillis()).coerceAtLeast(0L) + 1_000L
                handler.removeCallbacks(recheck)
                handler.postDelayed(recheck, delay)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::prefs.isInitialized || !prefs.sessionActive) return
        val pkg = event.packageName?.toString() ?: return
        evaluate(pkg, event.className?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::prefs.isInitialized) prefs.unregister(this)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun checkCurrentWindow() {
        if (!prefs.sessionActive) return
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return
        evaluate(pkg, null)
    }

    private fun evaluate(pkg: String, cls: String?) {
        val now = System.currentTimeMillis()
        if (now >= prefs.sessionEnd) {
            // Safety net in case the wake-up alarm was delayed.
            SessionManager.end(this, SleepSession.REASON_COMPLETED)
            return
        }
        val strict = prefs.sessionMode == BlockMode.STRICT
        if (strict && pkg in AppRules.STRICT_BLOCKED) {
            block(pkg)
            return
        }
        if (isAllowed(pkg, strict, now)) return
        if (!looksLikeApp(pkg, cls)) return
        block(pkg)
    }

    private fun isAllowed(pkg: String, strict: Boolean, now: Long): Boolean {
        if (pkg == packageName) return true
        if (pkg in systemAllowed || pkg in launchers) return true
        if (pkg in prefs.allowedPackages) return true
        if (!strict) {
            if (pkg in AppRules.BASIC_EXTRA_ALLOWED) return true
            if (pkg == prefs.tempAllowPackage && now < prefs.tempAllowUntil) return true
        }
        return false
    }

    /** Ignore popups/overlays that aren't real app screens. */
    private fun looksLikeApp(pkg: String, cls: String?): Boolean {
        if (cls != null) {
            try {
                packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // not an activity of that package — fall through
            }
        }
        return packageManager.getLaunchIntentForPackage(pkg) != null
    }

    private fun block(pkg: String) {
        val t = SystemClock.elapsedRealtime()
        if (pkg != lastBlockedPkg || t - lastBlockedAt > 3_000L) {
            prefs.blockedAttempts = prefs.blockedAttempts + 1
        }
        lastBlockedPkg = pkg
        lastBlockedAt = t

        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(BlockActivity.EXTRA_PKG, pkg)
        handler.postDelayed({
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Going home alone still blocks the app.
            }
        }, 150)
    }

    private fun refreshAllowed() {
        systemAllowed = AppRules.systemAllowed(this)
        launchers = AppRules.launchers(this)
    }
}
