namespace SleepGuard.Desktop;

/// <summary>Bedtime → wake-up window math (handles windows that cross midnight).</summary>
static class Schedule
{
    static (DateTime Start, DateTime End) WindowStarting(DateTime day, Settings s)
    {
        var start = day.Date.AddHours(s.BedHour).AddMinutes(s.BedMinute);
        var end = day.Date.AddHours(s.WakeHour).AddMinutes(s.WakeMinute);
        if (end <= start) end = end.AddDays(1);
        return (start, end);
    }

    /// <summary>If now is inside a scheduled window, returns when it ends.</summary>
    public static DateTime? ActiveWindowEnd(DateTime now, Settings s)
    {
        if (!s.ScheduleEnabled) return null;
        for (int offset = -1; offset <= 0; offset++)
        {
            var day = now.Date.AddDays(offset);
            if (!s.Days.Contains(day.DayOfWeek)) continue;
            var (start, end) = WindowStarting(day, s);
            if (now >= start && now < end) return end;
        }
        return null;
    }

    public static (DateTime Start, DateTime End)? NextWindow(DateTime now, Settings s)
    {
        if (!s.ScheduleEnabled || s.Days.Count == 0) return null;
        for (int offset = 0; offset <= 7; offset++)
        {
            var day = now.Date.AddDays(offset);
            if (!s.Days.Contains(day.DayOfWeek)) continue;
            var w = WindowStarting(day, s);
            if (w.Start > now) return w;
        }
        return null;
    }

    /// <summary>Next wake-up time after now (used by "Start sleep now").</summary>
    public static DateTime NextWakeAfter(DateTime now, Settings s)
    {
        var t = now.Date.AddHours(s.WakeHour).AddMinutes(s.WakeMinute);
        if (t <= now.AddMinutes(1)) t = t.AddDays(1);
        return t;
    }

    public static TimeSpan Duration(Settings s)
    {
        int bed = s.BedHour * 60 + s.BedMinute;
        int wake = s.WakeHour * 60 + s.WakeMinute;
        int d = ((wake - bed) % 1440 + 1440) % 1440;
        return TimeSpan.FromMinutes(d == 0 ? 1440 : d);
    }
}

static class Fmt
{
    public static string Time(DateTime t) => t.ToString("t");

    public static string Duration(TimeSpan t)
    {
        if (t < TimeSpan.Zero) t = TimeSpan.Zero;
        int h = (int)t.TotalHours;
        int m = t.Minutes;
        return h == 0 ? $"{m}m" : $"{h}h {m}m";
    }

    public static string Day(DateTime t) => t.ToString("ddd, d MMM");

    public static string RelDay(DateTime t, DateTime now)
    {
        int diff = (t.Date - now.Date).Days;
        return diff switch
        {
            0 => t.Hour >= 17 ? "Tonight" : "Today",
            1 => "Tomorrow",
            _ => t.ToString("dddd"),
        };
    }
}
