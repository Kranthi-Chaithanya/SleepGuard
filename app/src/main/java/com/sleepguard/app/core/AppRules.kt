package com.sleepguard.app.core

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/** Which packages are always allowed, and which are extra-blocked in strict mode. */
object AppRules {

    /** Calling, in-call screens and emergency — never blocked. */
    private val CALL_PACKAGES = setOf(
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.dialer",
        "com.android.incallui",
        "com.android.emergency",
        "com.google.android.dialer",
        "com.google.android.apps.safetyhub",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
    )

    /** Status bar, power menu, system dialogs. */
    private val SYSTEM_UI = setOf("com.android.systemui", "android")

    /** Allowed in basic mode only (settings, permission dialogs). */
    val BASIC_EXTRA_ALLOWED = setOf(
        "com.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
    )

    /**
     * Blocked in strict mode even though they are system apps: Settings (where the
     * accessibility service could be switched off) and the installer (uninstall dialog).
     */
    val STRICT_BLOCKED = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
    )

    fun systemAllowed(ctx: Context): Set<String> {
        val s = mutableSetOf<String>()
        s += CALL_PACKAGES
        s += SYSTEM_UI
        ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { s += it }
        s += resolve(ctx, Intent(Intent.ACTION_DIAL))
        // Clock apps must stay usable so alarms can ring and be dismissed.
        s += resolve(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        s += resolve(ctx, Intent(AlarmClock.ACTION_SET_ALARM))
        ctx.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList?.forEach { s += it.packageName }
        s -= STRICT_BLOCKED
        return s
    }

    fun launchers(ctx: Context): Set<String> =
        resolve(ctx, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) - STRICT_BLOCKED

    @Suppress("DEPRECATION")
    private fun resolve(ctx: Context, intent: Intent): Set<String> =
        ctx.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
}
