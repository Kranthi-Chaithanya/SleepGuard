package com.sleepguard.app.data

import java.util.Calendar

/** Pure time math for the bedtime → wake-up window. */
object ScheduleCalc {

    /** The window that starts at bedtime on [day]. Wake time rolls to the next day if needed. */
    private fun windowStarting(day: Calendar, p: SleepPrefs): Pair<Long, Long> {
        val start = (day.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, p.bedHour)
            set(Calendar.MINUTE, p.bedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, p.wakeHour)
            set(Calendar.MINUTE, p.wakeMinute)
            if (!after(start)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return start.timeInMillis to end.timeInMillis
    }

    private fun dayAt(now: Long, offset: Int): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, offset)
        }

    /** If [now] falls inside a scheduled window, returns that window's end time. */
    fun activeWindowEnd(now: Long, p: SleepPrefs): Long? {
        if (!p.scheduleEnabled) return null
        val days = p.days
        for (offset in -1..0) {
            val day = dayAt(now, offset)
            if (day.get(Calendar.DAY_OF_WEEK) !in days) continue
            val (s, e) = windowStarting(day, p)
            if (now in s until e) return e
        }
        return null
    }

    /** Next scheduled window starting strictly after [now]. */
    fun nextWindow(now: Long, p: SleepPrefs): Pair<Long, Long>? {
        if (!p.scheduleEnabled) return null
        val days = p.days
        if (days.isEmpty()) return null
        for (offset in 0..7) {
            val day = dayAt(now, offset)
            if (day.get(Calendar.DAY_OF_WEEK) !in days) continue
            val w = windowStarting(day, p)
            if (w.first > now) return w
        }
        return null
    }

    /** Next occurrence of the wake-up time after [now] (used by "Start sleep now"). */
    fun nextWakeAfter(now: Long, p: SleepPrefs): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, p.wakeHour)
            set(Calendar.MINUTE, p.wakeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now + 60_000L) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

    fun durationMinutes(p: SleepPrefs): Int {
        val bed = p.bedHour * 60 + p.bedMinute
        val wake = p.wakeHour * 60 + p.wakeMinute
        val d = (wake - bed + 1440) % 1440
        return if (d == 0) 1440 else d
    }
}
