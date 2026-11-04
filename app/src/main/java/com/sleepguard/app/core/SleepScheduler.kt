package com.sleepguard.app.core

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sleepguard.app.data.ScheduleCalc
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.receiver.AlarmReceiver

/** Sets the alarms that start sleep mode at bedtime and end it at wake-up. */
object SleepScheduler {
    private const val REQ_START = 100
    private const val REQ_END = 101

    /** (Re)arms the alarm for the next scheduled bedtime. Safe to call any time. */
    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val startPi = pendingIntent(ctx, AlarmReceiver.ACTION_START, REQ_START)
        am.cancel(startPi)
        val next = ScheduleCalc.nextWindow(System.currentTimeMillis(), SleepPrefs(ctx)) ?: return
        setExact(ctx, am, next.first, startPi)
    }

    fun scheduleEnd(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        setExact(ctx, am, at, pendingIntent(ctx, AlarmReceiver.ACTION_END, REQ_END))
    }

    fun cancelEnd(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(ctx, AlarmReceiver.ACTION_END, REQ_END))
    }

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    @SuppressLint("ScheduleExactAlarm", "MissingPermission") // guarded by canScheduleExact()
    private fun setExact(ctx: Context, am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pendingIntent(ctx: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            req,
            Intent(ctx, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
