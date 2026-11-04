package com.sleepguard.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sleepguard.app.MainActivity
import com.sleepguard.app.R
import com.sleepguard.app.data.BlockMode
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.data.SleepSession
import com.sleepguard.app.util.formatDuration
import com.sleepguard.app.util.formatTime

object Notifications {
    const val CH_ACTIVE = "sleep_active"
    const val CH_MORNING = "morning"
    const val ID_ACTIVE = 1
    const val ID_MORNING = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ACTIVE, "Sleep mode", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while sleep mode is on"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MORNING, "Morning summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "How long you slept"
            }
        )
    }

    fun active(ctx: Context, p: SleepPrefs): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val modeLabel = if (p.sessionMode == BlockMode.STRICT) "Strict mode" else "Basic mode"
        val text = if (p.sessionActive) "$modeLabel · until ${formatTime(ctx, p.sessionEnd)}" else "Starting…"
        return NotificationCompat.Builder(ctx, CH_ACTIVE)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("Sleep mode is on")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showMorning(ctx: Context, s: SleepSession) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_HISTORY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (s.endReason) {
            SleepSession.REASON_EMERGENCY -> "Sleep mode unlocked"
            SleepSession.REASON_ENDED_EARLY -> "Sleep mode ended early"
            else -> "Good morning"
        }
        val n = NotificationCompat.Builder(ctx, CH_MORNING)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(title)
            .setContentText("Estimated sleep: ${formatDuration(s.estimatedSleepMillis)}. Tap to rate your night.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_MORNING, n)
    }
}
