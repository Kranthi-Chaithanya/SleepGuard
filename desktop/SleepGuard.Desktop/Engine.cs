namespace SleepGuard.Desktop;

/// <summary>
/// The whole sleep-mode brain. Runs on the UI thread from a 500 ms timer: starts and ends
/// sessions on schedule, tracks PC use, and keeps non-allowed apps minimized behind the
/// "Time to sleep" overlay.
/// </summary>
sealed class Engine : IDisposable
{
    public const int MaxSnoozes = 3;

    public Settings Settings { get; }
    public SessionState State { get; private set; }
    public List<Night> History { get; }

    public event Action? Changed;
    public event Action<string, string>? Notify;

    readonly OverlayManager overlay;
    DateTime lastTick = DateTime.Now;
    DateTime lastSave = DateTime.MinValue;
    DateTime lastSweep = DateTime.MinValue;
    DateTime? remindedFor;
    bool wasIdleLong;
    bool idleSleepDone;

    string? lastCountedName;
    DateTime lastCountedAt;
    string? lastBlockedName;
    IntPtr lastBlockedHwnd;

    public Engine()
    {
        Settings = Store.Load<Settings>("settings.json");
        State = Store.Load<SessionState>("state.json");
        History = Store.Load<List<Night>>("history.json");
        overlay = new OverlayManager(this);
        Policy.SetStartup(Settings.StartWithWindows);
    }

    public string? LastBlockedLabel { get; private set; }
    public bool IsStrictActive => State.Active && State.Mode == BlockMode.Strict;
    public int SnoozesLeft => Math.Max(0, MaxSnoozes - State.SnoozesUsed);

    public bool CanSnooze =>
        State.Active && State.Mode == BlockMode.Basic && SnoozesLeft > 0 &&
        lastBlockedName != null && lastBlockedHwnd != IntPtr.Zero;

    // ------------------------------------------------------------------ lifecycle

    public void Startup()
    {
        var now = DateTime.Now;
        if (State.Active)
        {
            if (now >= State.End)
            {
                End(Reasons.Completed);
            }
            else
            {
                // PC restarted during the night: resume, including strict protections.
                if (State.Mode == BlockMode.Strict && Settings.StrictBlockTaskManager)
                {
                    Policy.SetTaskManagerDisabled(true);
                    State.TaskMgrPolicySet = true;
                }
                SweepWindows();
                if (State.Mode == BlockMode.Strict) overlay.ShowAll();
            }
        }
        else if (State.TaskMgrPolicySet)
        {
            // Left over from a crash: give Task Manager back.
            Policy.SetTaskManagerDisabled(false);
            State.TaskMgrPolicySet = false;
            SaveState();
        }
        Changed?.Invoke();
    }

    public void Tick()
    {
        var now = DateTime.Now;
        var dt = now - lastTick;
        lastTick = now;

        if (!State.Active)
        {
            if (now >= State.SkipUntil && Schedule.ActiveWindowEnd(now, Settings) is DateTime end)
            {
                Start(end);
                return;
            }
            CheckWindDown(now);
            return;
        }

        if (now >= State.End)
        {
            End(Reasons.Completed);
            return;
        }

        Track(dt);
        CheckForeground(now);
        if ((now - lastSweep).TotalSeconds >= 5)
        {
            lastSweep = now;
            SweepWindows();
        }
        overlay.Refresh();
        if ((now - lastSave).TotalSeconds >= 30) SaveState();
    }

    public void StartNow() => Start(Schedule.NextWakeAfter(DateTime.Now, Settings));

    public void Start(DateTime end)
    {
        if (State.Active) return;
        State = new SessionState
        {
            Active = true,
            Start = DateTime.Now,
            End = end,
            Mode = Settings.Mode,
            SkipUntil = State.SkipUntil,
        };
        wasIdleLong = false;
        idleSleepDone = false;
        lastBlockedName = null;
        lastBlockedHwnd = IntPtr.Zero;
        LastBlockedLabel = null;

        if (State.Mode == BlockMode.Strict && Settings.StrictBlockTaskManager)
        {
            Policy.SetTaskManagerDisabled(true);
            State.TaskMgrPolicySet = true;
        }
        SaveState();
        Notify?.Invoke("Sleep mode is on",
            $"{ModeName(State.Mode)} mode until {Fmt.Time(end)}. Calls through Phone Link still work.");
        SweepWindows();
        if (State.Mode == BlockMode.Strict) overlay.ShowAll();
        Changed?.Invoke();
    }

    public void End(string reason)
    {
        if (!State.Active) return;
        var now = DateTime.Now;
        // If the PC was off past wake-up, count the night only until the planned end.
        var endTime = reason == Reasons.Completed && State.End < now ? State.End : now;
        var night = new Night
        {
            Start = State.Start,
            End = endTime,
            PlannedEnd = State.End,
            Mode = State.Mode,
            ActiveMs = State.ActiveMs,
            Wakes = State.Wakes,
            Blocked = State.Blocked,
            EndReason = reason,
        };
        History.Add(night);
        if (History.Count > 365) History.RemoveRange(0, History.Count - 365);
        Store.Save("history.json", History);

        if (State.TaskMgrPolicySet) Policy.SetTaskManagerDisabled(false);
        State = new SessionState { SkipUntil = reason == Reasons.Completed ? DateTime.MinValue : State.End };
        overlay.HideAll();
        SaveState();

        var title = reason switch
        {
            Reasons.Emergency => "Sleep mode unlocked",
            Reasons.EndedEarly => "Sleep mode ended early",
            _ => "Good morning",
        };
        Notify?.Invoke(title, $"Estimated sleep: {Fmt.Duration(night.EstimatedSleep)}. Open SleepGuard to rate your night.");
        Changed?.Invoke();
    }

    /// <summary>Basic mode: allow the last blocked app for 5 minutes.</summary>
    public void Snooze()
    {
        if (!CanSnooze) return;
        State.SnoozesUsed++;
        State.TempAllowApp = lastBlockedName;
        State.TempAllowUntil = DateTime.Now.AddMinutes(5);
        overlay.HideAll();
        SaveState();
        Native.ShowWindowAsync(lastBlockedHwnd, Native.SW_RESTORE);
        Win.ForceForeground(lastBlockedHwnd);
        Changed?.Invoke();
    }

    /// <summary>Basic mode: hide the overlay and show the desktop (apps stay blocked).</summary>
    public void ReturnToDesktop()
    {
        if (State.Mode == BlockMode.Basic) overlay.HideAll();
    }

    public void UpdateSettings(Action<Settings> change)
    {
        change(Settings);
        Store.Save("settings.json", Settings);
        Policy.SetStartup(Settings.StartWithWindows);
        Changed?.Invoke();
    }

    public void Rate(Night night, int rating)
    {
        night.Rating = rating;
        Store.Save("history.json", History);
        Changed?.Invoke();
    }

    public void SaveState()
    {
        lastSave = DateTime.Now;
        Store.Save("state.json", State);
    }

    public void Dispose()
    {
        SaveState();
        overlay.Dispose();
    }

    public static string ModeName(BlockMode m) => m == BlockMode.Strict ? "Strict" : "Basic";

    // ------------------------------------------------------------------ tracking

    void Track(TimeSpan dt)
    {
        var idle = Native.IdleTime();

        // Ignore big gaps (the PC was asleep).
        if (dt > TimeSpan.Zero && dt < TimeSpan.FromSeconds(5) && idle < TimeSpan.FromSeconds(60))
            State.ActiveMs += (long)dt.TotalMilliseconds;

        if (idle >= TimeSpan.FromMinutes(5)) wasIdleLong = true;
        else if (wasIdleLong && idle < TimeSpan.FromSeconds(10))
        {
            State.Wakes++;
            wasIdleLong = false;
        }

        int mins = Settings.SleepPcAfterIdleMinutes;
        if (mins > 0 && idle >= TimeSpan.FromMinutes(mins) && !idleSleepDone)
        {
            idleSleepDone = true;
            SaveState();
            Application.SetSuspendState(PowerState.Suspend, false, false);
        }
        else if (idle < TimeSpan.FromMinutes(1))
        {
            idleSleepDone = false;
        }
    }

    void CheckWindDown(DateTime now)
    {
        if (!Settings.WindDownReminder) return;
        if (Schedule.NextWindow(now, Settings) is not { } next) return;
        var left = next.Start - now;
        if (left > TimeSpan.Zero && left <= TimeSpan.FromMinutes(15) && remindedFor != next.Start)
        {
            remindedFor = next.Start;
            Notify?.Invoke("Bedtime in 15 minutes",
                $"Sleep mode starts at {Fmt.Time(next.Start)}. Time to save your work and wrap up.");
        }
    }

    // ------------------------------------------------------------------ blocking

    public bool IsAllowed(string name, DateTime now)
    {
        bool strict = State.Mode == BlockMode.Strict;
        if (name.Length == 0 || name == Rules.Self) return true;
        if (Rules.AlwaysAllowed.Contains(name)) return true;
        if (strict && Rules.StrictBlocked.Contains(name)) return false;
        if (Settings.AllowedApps.Any(a => a.Name.Equals(name, StringComparison.OrdinalIgnoreCase))) return true;
        if (!strict)
        {
            if (Rules.Shell.Contains(name) || Rules.BasicExtra.Contains(name)) return true;
            if (State.TempAllowApp == name && now < State.TempAllowUntil) return true;
        }
        return false;
    }

    void CheckForeground(DateTime now)
    {
        var hwnd = Native.GetForegroundWindow();
        if (hwnd == IntPtr.Zero) return;
        var (pid, name) = Win.AppForWindow(hwnd);
        if (pid == (uint)Environment.ProcessId) return; // our own windows and dialogs
        if (IsAllowed(name, now))
        {
            if (overlay.Visible) overlay.HideAll();
            return;
        }
        Block(hwnd, pid, name, now);
    }

    void Block(IntPtr hwnd, uint pid, string name, DateTime now)
    {
        if (!Rules.Shell.Contains(name))
        {
            if (name != lastCountedName || (now - lastCountedAt).TotalSeconds > 3) State.Blocked++;
            lastCountedName = name;
            lastCountedAt = now;
            lastBlockedName = name;
            lastBlockedHwnd = hwnd;
            LastBlockedLabel = Win.FriendlyName(pid, name);
            Native.ShowWindowAsync(hwnd, Native.SW_MINIMIZE);
        }
        overlay.ShowAll();
    }

    /// <summary>Minimize every visible window of a blocked app (covers second monitors too).</summary>
    void SweepWindows()
    {
        if (!State.Active) return;
        var now = DateTime.Now;
        uint self = (uint)Environment.ProcessId;
        foreach (var h in Win.TopLevelAppWindows())
        {
            var (pid, name) = Win.AppForWindow(h);
            if (pid == self || name.Length == 0 || Rules.Shell.Contains(name)) continue;
            if (IsAllowed(name, now)) continue;
            Native.ShowWindowAsync(h, Native.SW_MINIMIZE);
        }
    }
}
