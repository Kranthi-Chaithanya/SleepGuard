package com.sleepguard.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Calendar

enum class BlockMode { BASIC, STRICT }

/**
 * All settings and the live sleep-session state, stored in SharedPreferences so every
 * component (UI, alarms, accessibility service, foreground service) sees the same values
 * and can listen for changes.
 */
class SleepPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------------- Schedule settings ----------------

    var scheduleEnabled: Boolean
        get() = sp.getBoolean(K_ENABLED, false)
        set(v) = sp.edit { putBoolean(K_ENABLED, v) }

    var bedHour: Int
        get() = sp.getInt(K_BED_H, 23)
        set(v) = sp.edit { putInt(K_BED_H, v) }

    var bedMinute: Int
        get() = sp.getInt(K_BED_M, 0)
        set(v) = sp.edit { putInt(K_BED_M, v) }

    var wakeHour: Int
        get() = sp.getInt(K_WAKE_H, 6)
        set(v) = sp.edit { putInt(K_WAKE_H, v) }

    var wakeMinute: Int
        get() = sp.getInt(K_WAKE_M, 30)
        set(v) = sp.edit { putInt(K_WAKE_M, v) }

    /** Days (Calendar.SUNDAY..SATURDAY) on which the bedtime starts. */
    var days: Set<Int>
        get() = sp.getStringSet(K_DAYS, null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: ALL_DAYS
        set(v) = sp.edit { putStringSet(K_DAYS, v.map { it.toString() }.toSet()) }

    /** Mode used for the *next* session. A running session keeps the mode it started with. */
    var mode: BlockMode
        get() = parseMode(sp.getString(K_MODE, null))
        set(v) = sp.edit { putString(K_MODE, v.name) }

    /** Extra apps the user allows during sleep (on top of phone, clock, keyboard, launcher). */
    var allowedPackages: Set<String>
        get() = sp.getStringSet(K_ALLOWED, null)?.toSet() ?: emptySet()
        set(v) = sp.edit { putStringSet(K_ALLOWED, v.toSet()) }

    // ---------------- Live session state ----------------

    val sessionActive: Boolean get() = sp.getBoolean(K_ACTIVE, false)
    val sessionStart: Long get() = sp.getLong(K_START, 0L)
    val sessionEnd: Long get() = sp.getLong(K_END, 0L)
    val sessionMode: BlockMode get() = parseMode(sp.getString(K_SESSION_MODE, null))

    var screenOnCount: Int
        get() = sp.getInt(K_SCREEN_COUNT, 0)
        set(v) = sp.edit { putInt(K_SCREEN_COUNT, v) }

    var screenOnMillis: Long
        get() = sp.getLong(K_SCREEN_MS, 0L)
        set(v) = sp.edit { putLong(K_SCREEN_MS, v) }

    /** Wall-clock time the screen last turned on, or 0 if it is off. */
    var screenOnSince: Long
        get() = sp.getLong(K_SCREEN_SINCE, 0L)
        set(v) = sp.edit { putLong(K_SCREEN_SINCE, v) }

    var blockedAttempts: Int
        get() = sp.getInt(K_BLOCKED, 0)
        set(v) = sp.edit { putInt(K_BLOCKED, v) }

    var snoozesUsed: Int
        get() = sp.getInt(K_SNOOZES, 0)
        set(v) = sp.edit { putInt(K_SNOOZES, v) }

    var tempAllowPackage: String?
        get() = sp.getString(K_TEMP_PKG, null)
        set(v) = sp.edit { putString(K_TEMP_PKG, v) }

    var tempAllowUntil: Long
        get() = sp.getLong(K_TEMP_UNTIL, 0L)
        set(v) = sp.edit { putLong(K_TEMP_UNTIL, v) }

    /** After ending a scheduled night early, don't auto-restart it (e.g. after reboot) before this time. */
    var skipUntil: Long
        get() = sp.getLong(K_SKIP_UNTIL, 0L)
        set(v) = sp.edit { putLong(K_SKIP_UNTIL, v) }

    /** True while a strict session runs: settings in the app are read-only. */
    val isLockedNow: Boolean get() = sessionActive && sessionMode == BlockMode.STRICT

    fun beginSession(start: Long, end: Long, mode: BlockMode, screenOn: Boolean) = sp.edit {
        putLong(K_START, start)
        putLong(K_END, end)
        putString(K_SESSION_MODE, mode.name)
        putInt(K_SCREEN_COUNT, 0)
        putLong(K_SCREEN_MS, 0L)
        putLong(K_SCREEN_SINCE, if (screenOn) start else 0L)
        putInt(K_BLOCKED, 0)
        putInt(K_SNOOZES, 0)
        remove(K_TEMP_PKG)
        putLong(K_TEMP_UNTIL, 0L)
        putBoolean(K_ACTIVE, true)
    }

    fun clearSession() = sp.edit {
        putBoolean(K_ACTIVE, false)
        remove(K_TEMP_PKG)
        putLong(K_TEMP_UNTIL, 0L)
        putLong(K_SCREEN_SINCE, 0L)
    }

    fun register(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregister(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val FILE = "sleepguard"
        const val MAX_SNOOZES = 3
        const val SNOOZE_MS = 5 * 60_000L

        const val K_ENABLED = "schedule_enabled"
        const val K_BED_H = "bed_h"
        const val K_BED_M = "bed_m"
        const val K_WAKE_H = "wake_h"
        const val K_WAKE_M = "wake_m"
        const val K_DAYS = "days"
        const val K_MODE = "mode"
        const val K_ALLOWED = "allowed_packages"

        const val K_ACTIVE = "session_active"
        const val K_START = "session_start"
        const val K_END = "session_end"
        const val K_SESSION_MODE = "session_mode"
        const val K_SCREEN_COUNT = "screen_on_count"
        const val K_SCREEN_MS = "screen_on_ms"
        const val K_SCREEN_SINCE = "screen_on_since"
        const val K_BLOCKED = "blocked_attempts"
        const val K_SNOOZES = "snoozes_used"
        const val K_TEMP_PKG = "temp_allow_pkg"
        const val K_TEMP_UNTIL = "temp_allow_until"
        const val K_SKIP_UNTIL = "skip_until"

        val ALL_DAYS: Set<Int> = (Calendar.SUNDAY..Calendar.SATURDAY).toSet()

        private fun parseMode(s: String?): BlockMode =
            BlockMode.entries.firstOrNull { it.name == s } ?: BlockMode.BASIC
    }
}
