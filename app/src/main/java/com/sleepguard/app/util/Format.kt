package com.sleepguard.app.util

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatTime(ctx: Context, millis: Long): String =
    DateFormat.getTimeFormat(ctx).format(Date(millis))

fun formatClock(ctx: Context, hour: Int, minute: Int): String {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return formatTime(ctx, c.timeInMillis)
}

fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(0L)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h == 0L) "${m}m" else "${h}h ${m}m"
}

fun formatDay(millis: Long): String =
    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(millis))

fun dayLetter(millis: Long): String =
    SimpleDateFormat("EEEEE", Locale.getDefault()).format(Date(millis))

/** "Tonight", "Tomorrow" or a weekday name, relative to [now]. */
fun relativeDay(millis: Long, now: Long): String {
    val a = Calendar.getInstance().apply { timeInMillis = now }
    val b = Calendar.getInstance().apply { timeInMillis = millis }
    val diff = (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 366 +
        b.get(Calendar.DAY_OF_YEAR) - a.get(Calendar.DAY_OF_YEAR)
    return when (diff) {
        0 -> if (b.get(Calendar.HOUR_OF_DAY) >= 17) "Tonight" else "Today"
        1 -> "Tomorrow"
        else -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(millis))
    }
}
