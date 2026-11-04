using System.Text.Json;
using System.Text.Json.Serialization;

namespace SleepGuard.Desktop;

public enum BlockMode { Basic, Strict }

/// <summary>An app the user allows during sleep time.</summary>
public sealed class AllowedApp
{
    /// <summary>Process name, lower case, without ".exe".</summary>
    public string Name { get; set; } = "";
    public string? Path { get; set; }
    public string Label { get; set; } = "";

    public override string ToString() =>
        string.IsNullOrWhiteSpace(Label) ? Name + ".exe" : $"{Label}   ({Name}.exe)";
}

public sealed class Settings
{
    public bool ScheduleEnabled { get; set; }
    public int BedHour { get; set; } = 23;
    public int BedMinute { get; set; }
    public int WakeHour { get; set; } = 6;
    public int WakeMinute { get; set; } = 30;
    public List<DayOfWeek> Days { get; set; } = Enum.GetValues<DayOfWeek>().ToList();
    public BlockMode Mode { get; set; } = BlockMode.Basic;
    public List<AllowedApp> AllowedApps { get; set; } = new();
    public bool StartWithWindows { get; set; } = true;
    public bool WindDownReminder { get; set; } = true;
    public bool StrictBlockTaskManager { get; set; } = true;
    /// <summary>Put the PC to sleep after this many idle minutes during sleep time (0 = never).</summary>
    public int SleepPcAfterIdleMinutes { get; set; }
}

/// <summary>Live state of the current night, saved so a restart or reboot resumes it.</summary>
public sealed class SessionState
{
    public bool Active { get; set; }
    public DateTime Start { get; set; }
    public DateTime End { get; set; }
    public BlockMode Mode { get; set; }
    public long ActiveMs { get; set; }
    public int Wakes { get; set; }
    public int Blocked { get; set; }
    public int SnoozesUsed { get; set; }
    public string? TempAllowApp { get; set; }
    public DateTime TempAllowUntil { get; set; }
    /// <summary>After ending a scheduled night early, don't restart it before this time.</summary>
    public DateTime SkipUntil { get; set; }
    public bool TaskMgrPolicySet { get; set; }
}

public static class Reasons
{
    public const string Completed = "completed";
    public const string EndedEarly = "ended_early";
    public const string Emergency = "emergency";
}

/// <summary>One tracked night.</summary>
public sealed class Night
{
    public DateTime Start { get; set; }
    public DateTime End { get; set; }
    public DateTime PlannedEnd { get; set; }
    public BlockMode Mode { get; set; }
    /// <summary>Time the PC was actively used (keyboard/mouse) during sleep mode.</summary>
    public long ActiveMs { get; set; }
    public int Wakes { get; set; }
    public int Blocked { get; set; }
    public string EndReason { get; set; } = Reasons.Completed;
    public int Rating { get; set; }

    [JsonIgnore]
    public TimeSpan Duration => End > Start ? End - Start : TimeSpan.Zero;

    /// <summary>Time in sleep mode minus PC use, minus ~15 minutes to fall asleep.</summary>
    [JsonIgnore]
    public TimeSpan EstimatedSleep
    {
        get
        {
            var t = Duration - TimeSpan.FromMilliseconds(ActiveMs) - TimeSpan.FromMinutes(15);
            return t < TimeSpan.Zero ? TimeSpan.Zero : t;
        }
    }
}

/// <summary>JSON files in %APPDATA%\SleepGuard.</summary>
public static class Store
{
    public static readonly string Dir = System.IO.Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "SleepGuard");

    static readonly JsonSerializerOptions Opts = new()
    {
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter() },
    };

    public static T Load<T>(string file) where T : new()
    {
        try
        {
            var p = System.IO.Path.Combine(Dir, file);
            if (File.Exists(p)) return JsonSerializer.Deserialize<T>(File.ReadAllText(p), Opts) ?? new T();
        }
        catch
        {
            // corrupt file: start fresh
        }
        return new T();
    }

    public static void Save<T>(string file, T value)
    {
        try
        {
            Directory.CreateDirectory(Dir);
            var p = System.IO.Path.Combine(Dir, file);
            var tmp = p + ".tmp";
            File.WriteAllText(tmp, JsonSerializer.Serialize(value, Opts));
            File.Move(tmp, p, true);
        }
        catch
        {
            // disk full / locked: try again next save
        }
    }
}
