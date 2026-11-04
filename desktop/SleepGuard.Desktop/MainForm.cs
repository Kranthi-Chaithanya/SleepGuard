using System.ComponentModel;

namespace SleepGuard.Desktop;

sealed class MainForm : Form
{
    readonly Engine engine;
    readonly Panel content;
    readonly Button[] tabButtons;
    readonly Control[] views;
    readonly TonightView tonight;
    readonly ScheduleView schedule;
    readonly HistoryView history;
    readonly System.Windows.Forms.Timer refresh;
    int current = -1;

    public MainForm(Engine engine)
    {
        this.engine = engine;
        Dialogs.StyleDialog(this);
        Text = "SleepGuard";
        ClientSize = new Size(760, 680);
        MinimumSize = new Size(720, 520);

        var bar = new FlowLayoutPanel
        {
            Dock = DockStyle.Top,
            Height = 62,
            BackColor = Theme.Card,
            Padding = new Padding(16, 12, 16, 0),
            WrapContents = false,
        };
        var moon = new MoonBox { Size = new Size(34, 34), BackColor = Theme.Card, Margin = new Padding(0, 0, 8, 0) };
        var title = Theme.Lbl("SleepGuard", 15, FontStyle.Bold);
        title.Margin = new Padding(0, 2, 28, 0);
        bar.Controls.Add(moon);
        bar.Controls.Add(title);

        string[] names = { "Tonight", "Schedule", "History" };
        tabButtons = new Button[names.Length];
        for (int i = 0; i < names.Length; i++)
        {
            int index = i;
            var b = Theme.Btn(names[i]);
            b.BackColor = Theme.Card;
            b.Margin = new Padding(0, 0, 6, 0);
            b.Click += (_, _) => ShowTab(index);
            tabButtons[i] = b;
            bar.Controls.Add(b);
        }

        content = new Panel { Dock = DockStyle.Fill, BackColor = Theme.Bg };
        Controls.Add(content);
        Controls.Add(bar);

        tonight = new TonightView(engine);
        tonight.OpenSchedule += () => ShowTab(1);
        schedule = new ScheduleView(engine);
        history = new HistoryView(engine);
        views = new Control[] { tonight, schedule, history };
        foreach (var v in views)
        {
            v.Dock = DockStyle.Fill;
            v.Visible = false;
            content.Controls.Add(v);
        }

        engine.Changed += OnEngineChanged;
        refresh = new System.Windows.Forms.Timer { Interval = 5000 };
        refresh.Tick += (_, _) => { if (Visible && current == 0) tonight.Reload(); };
        refresh.Start();

        ShowTab(0);
    }

    void ShowTab(int index)
    {
        if (index == current) return;
        current = index;
        for (int i = 0; i < views.Length; i++)
        {
            views[i].Visible = i == index;
            tabButtons[i].BackColor = i == index ? Theme.AccentDark : Theme.Card;
        }
        Reload(index);
    }

    void Reload(int index)
    {
        switch (index)
        {
            case 0: tonight.Reload(); break;
            case 1: schedule.Reload(); break;
            case 2: history.Reload(); break;
        }
    }

    void OnEngineChanged()
    {
        if (IsDisposed) return;
        tonight.Reload();
        schedule.Reload();
        history.Reload();
    }

    protected override void OnFormClosed(FormClosedEventArgs e)
    {
        engine.Changed -= OnEngineChanged;
        refresh.Stop();
        refresh.Dispose();
        base.OnFormClosed(e);
    }
}

/// <summary>Scrollable vertical page used by each tab.</summary>
static class Page
{
    public static FlowLayoutPanel Create() => new()
    {
        Dock = DockStyle.Fill,
        FlowDirection = FlowDirection.TopDown,
        WrapContents = false,
        AutoScroll = true,
        Padding = new Padding(28, 22, 28, 22),
        BackColor = Theme.Bg,
    };
}

// =====================================================================================
// Tonight
// =====================================================================================

sealed class TonightView : UserControl
{
    readonly Engine engine;
    readonly Label statusTitle, statusLine, stats, lastTitle, lastSleep, lastDetail;
    readonly Button btnStart, btnEnd, btnEmergency, btnSchedule;
    readonly FlowLayoutPanel lastCard;

    public event Action? OpenSchedule;

    public TonightView(Engine engine)
    {
        this.engine = engine;
        BackColor = Theme.Bg;
        var page = Page.Create();

        var card = Theme.CardPanel();
        statusTitle = Theme.Lbl("", 18, FontStyle.Bold, maxWidth: Theme.CardWidth - 40);
        statusLine = Theme.Lbl("", 11, color: Theme.Muted, maxWidth: Theme.CardWidth - 40);
        stats = Theme.Lbl("", 10, maxWidth: Theme.CardWidth - 40);
        var row = Theme.Row();
        btnStart = Theme.Btn("Start sleep now", true);
        btnEnd = Theme.Btn("End sleep mode");
        btnEmergency = Theme.Btn("Emergency unlock");
        btnSchedule = Theme.Btn("Set schedule");
        btnStart.Click += (_, _) => Dialogs.ConfirmStart(engine, FindForm());
        btnEnd.Click += (_, _) => Dialogs.ConfirmEnd(engine, FindForm());
        btnEmergency.Click += (_, _) => Dialogs.Emergency(engine, FindForm());
        btnSchedule.Click += (_, _) => OpenSchedule?.Invoke();
        row.Controls.AddRange(new Control[] { btnStart, btnEnd, btnEmergency, btnSchedule });
        card.Controls.AddRange(new Control[] { statusTitle, statusLine, stats, row });

        lastCard = Theme.CardPanel();
        lastTitle = Theme.Lbl("", 10, color: Theme.Muted);
        lastSleep = Theme.Lbl("", 26, FontStyle.Bold);
        lastSleep.Margin = new Padding(0);
        var est = Theme.Lbl("estimated sleep", 9, color: Theme.Muted);
        lastDetail = Theme.Lbl("", 10, maxWidth: Theme.CardWidth - 40);
        lastCard.Controls.AddRange(new Control[] { lastTitle, lastSleep, est, lastDetail });

        var tips = Theme.CardPanel();
        tips.Controls.Add(Theme.Lbl("How it works", 11, FontStyle.Bold));
        tips.Controls.Add(Theme.Lbl(
            "• At bedtime a full-screen sleep screen appears and any app you open is minimized.\n" +
            "• Phone Link stays allowed so you can still take calls from your phone on this PC. Clock and your allowed apps keep working too.\n" +
            "• SleepGuard lives in the tray (moon icon near the clock) and starts with Windows, so restarting doesn't skip a night.\n" +
            "• Your night is tracked from keyboard and mouse use: time in sleep mode, PC use, wake-ups and blocked apps.",
            10, color: Theme.Muted, maxWidth: Theme.CardWidth - 40));

        page.Controls.AddRange(new Control[] { card, lastCard, tips });
        Controls.Add(page);
    }

    public void Reload()
    {
        var s = engine.State;
        var now = DateTime.Now;
        if (s.Active)
        {
            statusTitle.Text = s.Mode == BlockMode.Strict ? "🔒 Sleep mode is on" : "Sleep mode is on";
            statusLine.Text = $"Until {Fmt.Time(s.End)}  ·  {Fmt.Duration(s.End - now)} left  ·  {Engine.ModeName(s.Mode)} mode";
            stats.Text = $"PC use so far: {Fmt.Duration(TimeSpan.FromMilliseconds(s.ActiveMs))}   ·   Wake-ups: {s.Wakes}   ·   Blocked: {s.Blocked}";
            stats.Visible = true;
            btnStart.Visible = false;
            btnSchedule.Visible = false;
            btnEnd.Visible = s.Mode == BlockMode.Basic;
            btnEmergency.Visible = s.Mode == BlockMode.Strict;
        }
        else
        {
            if (Schedule.NextWindow(now, engine.Settings) is { } next)
            {
                statusTitle.Text = $"Next bedtime: {Fmt.RelDay(next.Start, now)} at {Fmt.Time(next.Start)}";
                statusLine.Text = $"Until {Fmt.Time(next.End)} ({Fmt.Duration(next.End - next.Start)})  ·  {Engine.ModeName(engine.Settings.Mode)} mode";
                btnSchedule.Visible = false;
            }
            else
            {
                statusTitle.Text = "No sleep schedule yet";
                statusLine.Text = "Set a bedtime and wake-up time and SleepGuard switches on by itself every night.";
                btnSchedule.Visible = true;
            }
            stats.Visible = false;
            btnStart.Text = $"Start sleep now · until {Fmt.Time(Schedule.NextWakeAfter(now, engine.Settings))}";
            btnStart.Visible = true;
            btnEnd.Visible = false;
            btnEmergency.Visible = false;
        }

        var last = engine.History.LastOrDefault();
        lastCard.Visible = last != null;
        if (last != null)
        {
            lastTitle.Text = $"Last night · {Fmt.Day(last.Start)}";
            lastSleep.Text = Fmt.Duration(last.EstimatedSleep);
            lastDetail.Text = $"{Fmt.Time(last.Start)} → {Fmt.Time(last.End)}   ·   PC use {Fmt.Duration(TimeSpan.FromMilliseconds(last.ActiveMs))}   ·   {last.Wakes} wake-ups   ·   {last.Blocked} blocked";
        }
    }
}

// =====================================================================================
// Schedule
// =====================================================================================

sealed record TimeOption(int H, int M)
{
    public override string ToString() => DateTime.Today.AddHours(H).AddMinutes(M).ToString("t");
}

sealed class ScheduleView : UserControl
{
    static readonly DayOfWeek[] DayOrder =
    {
        DayOfWeek.Monday, DayOfWeek.Tuesday, DayOfWeek.Wednesday, DayOfWeek.Thursday,
        DayOfWeek.Friday, DayOfWeek.Saturday, DayOfWeek.Sunday,
    };

    readonly Engine engine;
    readonly Label banner, duration;
    readonly CheckBox chkEnabled, chkTaskMgr, chkStartup, chkWindDown;
    readonly ComboBox cmbBed, cmbWake;
    readonly CheckBox[] days;
    readonly RadioButton rbBasic, rbStrict;
    readonly ListBox lstApps;
    readonly NumericUpDown numIdle;
    readonly FlowLayoutPanel scheduleCard, modeCard, appsCard, optionsCard;
    bool loading;

    public ScheduleView(Engine engine)
    {
        this.engine = engine;
        BackColor = Theme.Bg;
        var page = Page.Create();
        const int textW = Theme.CardWidth - 40;

        banner = Theme.Lbl("", 10, FontStyle.Bold, Theme.Gold, Theme.CardWidth);
        banner.Margin = new Padding(0, 0, 0, 12);

        // ---- schedule
        scheduleCard = Theme.CardPanel();
        scheduleCard.Controls.Add(Theme.Lbl("Sleep schedule", 12, FontStyle.Bold));
        chkEnabled = new CheckBox { Text = "Turn on sleep mode automatically on the days below" };
        Theme.Style(chkEnabled);
        chkEnabled.CheckedChanged += (_, _) => Apply(s => s.ScheduleEnabled = chkEnabled.Checked);

        cmbBed = TimeCombo();
        cmbWake = TimeCombo();
        cmbBed.SelectedIndexChanged += (_, _) =>
        {
            if (cmbBed.SelectedItem is TimeOption t) Apply(s => { s.BedHour = t.H; s.BedMinute = t.M; });
        };
        cmbWake.SelectedIndexChanged += (_, _) =>
        {
            if (cmbWake.SelectedItem is TimeOption t) Apply(s => { s.WakeHour = t.H; s.WakeMinute = t.M; });
        };
        var times = Theme.Row();
        times.Controls.AddRange(new Control[]
        {
            FieldLabel("Bedtime"), cmbBed, FieldLabel("Wake up"), cmbWake,
        });
        duration = Theme.Lbl("", 10, color: Theme.Accent);

        var dayRow = Theme.Row();
        days = new CheckBox[DayOrder.Length];
        for (int i = 0; i < DayOrder.Length; i++)
        {
            var day = DayOrder[i];
            var c = new CheckBox
            {
                Text = day.ToString()[..3],
                Appearance = Appearance.Button,
                FlatStyle = FlatStyle.Flat,
                TextAlign = ContentAlignment.MiddleCenter,
                Size = new Size(64, 34),
                ForeColor = Theme.Text,
                BackColor = Theme.Card2,
                Font = Theme.F(10, FontStyle.Bold),
                Margin = new Padding(0, 0, 8, 6),
                Cursor = Cursors.Hand,
            };
            c.FlatAppearance.BorderSize = 0;
            c.FlatAppearance.CheckedBackColor = Theme.Accent;
            c.CheckedChanged += (_, _) =>
            {
                c.ForeColor = c.Checked ? Theme.OnAccent : Theme.Text;
                Apply(s =>
                {
                    s.Days.Remove(day);
                    if (c.Checked) s.Days.Add(day);
                });
            };
            days[i] = c;
            dayRow.Controls.Add(c);
        }
        scheduleCard.Controls.AddRange(new Control[] { chkEnabled, times, duration, FieldLabel("Repeat on"), dayRow });

        // ---- mode
        modeCard = Theme.CardPanel();
        modeCard.Controls.Add(Theme.Lbl("Blocking mode", 12, FontStyle.Bold));
        rbBasic = new RadioButton { Text = "Basic" };
        Theme.Style(rbBasic);
        var basicDesc = Theme.Lbl(
            "Blocks apps but stays forgiving: go back to the desktop, use a blocked app for 5 minutes (3× a night), or end sleep mode early. Settings and Task Manager stay usable.",
            9.5f, color: Theme.Muted, maxWidth: textW);
        basicDesc.Margin = new Padding(20, 0, 0, 8);
        rbStrict = new RadioButton { Text = "Strict" };
        Theme.Style(rbStrict);
        var strictDesc = Theme.Lbl(
            "The sleep screen can't be dismissed and there's no ending early. Task Manager, Settings, Registry Editor and command prompts are blocked, SleepGuard can't be exited, and restarting the PC brings sleep mode straight back. Only a long emergency code unlocks.",
            9.5f, color: Theme.Muted, maxWidth: textW);
        strictDesc.Margin = new Padding(20, 0, 0, 8);
        rbBasic.CheckedChanged += (_, _) => { if (rbBasic.Checked) Apply(s => s.Mode = BlockMode.Basic); };
        rbStrict.CheckedChanged += (_, _) => OnStrictChecked();
        chkTaskMgr = new CheckBox { Text = "Strict: also disable Task Manager (re-enabled at wake-up)" };
        Theme.Style(chkTaskMgr);
        chkTaskMgr.Margin = new Padding(20, 0, 0, 0);
        chkTaskMgr.CheckedChanged += (_, _) => Apply(s => s.StrictBlockTaskManager = chkTaskMgr.Checked);
        modeCard.Controls.AddRange(new Control[] { rbBasic, basicDesc, rbStrict, strictDesc, chkTaskMgr });

        // ---- allowed apps
        appsCard = Theme.CardPanel();
        appsCard.Controls.Add(Theme.Lbl("Allowed during sleep", 12, FontStyle.Bold));
        appsCard.Controls.Add(Theme.Lbl(
            "Always allowed: Phone Link (calls from your phone), Clock & alarms, the lock screen. Add anything else you need at night, like a music or meditation app.",
            9.5f, color: Theme.Muted, maxWidth: textW));
        lstApps = new ListBox
        {
            Width = textW,
            Height = 110,
            BackColor = Theme.Card2,
            ForeColor = Theme.Text,
            BorderStyle = BorderStyle.None,
            Font = Theme.F(10),
            IntegralHeight = false,
            Margin = new Padding(0, 4, 0, 4),
        };
        var appButtons = Theme.Row();
        var btnRunning = Theme.Btn("Add running app…");
        var btnExe = Theme.Btn("Add program (.exe)…");
        var btnRemove = Theme.Btn("Remove");
        btnRunning.Click += (_, _) => AddRunningApp();
        btnExe.Click += (_, _) => AddExe();
        btnRemove.Click += (_, _) => RemoveApp();
        appButtons.Controls.AddRange(new Control[] { btnRunning, btnExe, btnRemove });
        appsCard.Controls.AddRange(new Control[] { lstApps, appButtons });

        // ---- options
        optionsCard = Theme.CardPanel();
        optionsCard.Controls.Add(Theme.Lbl("Options", 12, FontStyle.Bold));
        chkStartup = new CheckBox { Text = "Start SleepGuard with Windows (recommended)" };
        Theme.Style(chkStartup);
        chkStartup.CheckedChanged += (_, _) => Apply(s => s.StartWithWindows = chkStartup.Checked);
        chkWindDown = new CheckBox { Text = "Remind me 15 minutes before bedtime" };
        Theme.Style(chkWindDown);
        chkWindDown.CheckedChanged += (_, _) => Apply(s => s.WindDownReminder = chkWindDown.Checked);
        var idleRow = Theme.Row();
        numIdle = new NumericUpDown
        {
            Minimum = 0,
            Maximum = 240,
            Width = 64,
            BackColor = Theme.Card2,
            ForeColor = Theme.Text,
            BorderStyle = BorderStyle.FixedSingle,
            Font = Theme.F(10),
            Margin = new Padding(0, 2, 8, 0),
        };
        numIdle.ValueChanged += (_, _) => Apply(s => s.SleepPcAfterIdleMinutes = (int)numIdle.Value);
        var idleLabel = Theme.Lbl("idle minutes during sleep time → put the PC to sleep (0 = never)", 10);
        idleLabel.Margin = new Padding(0, 5, 0, 0);
        idleRow.Controls.AddRange(new Control[] { numIdle, idleLabel });
        optionsCard.Controls.AddRange(new Control[] { chkStartup, chkWindDown, idleRow });

        page.Controls.AddRange(new Control[] { banner, scheduleCard, modeCard, appsCard, optionsCard });
        Controls.Add(page);
    }

    static Label FieldLabel(string text)
    {
        var l = Theme.Lbl(text, 10, color: Theme.Muted);
        l.Margin = new Padding(0, 7, 8, 0);
        return l;
    }

    static ComboBox TimeCombo()
    {
        var c = new ComboBox
        {
            DropDownStyle = ComboBoxStyle.DropDownList,
            FlatStyle = FlatStyle.Flat,
            BackColor = Theme.Card2,
            ForeColor = Theme.Text,
            Font = Theme.F(11),
            Width = 120,
            MaxDropDownItems = 16,
            Margin = new Padding(0, 0, 24, 6),
        };
        for (int m = 0; m < 24 * 60; m += 15) c.Items.Add(new TimeOption(m / 60, m % 60));
        return c;
    }

    static void SelectTime(ComboBox c, int h, int m)
    {
        var t = new TimeOption(h, m);
        if (!c.Items.Contains(t)) c.Items.Add(t);
        c.SelectedItem = t;
    }

    void Apply(Action<Settings> change)
    {
        if (loading) return;
        engine.UpdateSettings(change);
    }

    void OnStrictChecked()
    {
        if (loading || !rbStrict.Checked || engine.Settings.Mode == BlockMode.Strict) return;
        var r = MessageBox.Show(FindForm(),
            "Use strict mode from the next sleep session?\n\nYou won't be able to close the sleep screen, end sleep mode early, " +
            "exit SleepGuard or open Task Manager / Settings until wake-up. Calls through Phone Link and alarms keep working.",
            "SleepGuard", MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
        if (r == DialogResult.Yes)
        {
            Apply(s => s.Mode = BlockMode.Strict);
        }
        else
        {
            loading = true;
            rbBasic.Checked = true;
            loading = false;
        }
    }

    void AddRunningApp()
    {
        using var f = new PickAppForm();
        if (f.ShowDialog(FindForm()) == DialogResult.OK && f.Result is { } app) AddApp(app);
    }

    void AddExe()
    {
        using var dlg = new OpenFileDialog
        {
            Title = "Choose a program to allow during sleep",
            Filter = "Programs (*.exe)|*.exe",
            InitialDirectory = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
        };
        if (dlg.ShowDialog(FindForm()) != DialogResult.OK) return;
        var path = dlg.FileName;
        string label = Path.GetFileNameWithoutExtension(path);
        try
        {
            var d = System.Diagnostics.FileVersionInfo.GetVersionInfo(path).FileDescription;
            if (!string.IsNullOrWhiteSpace(d)) label = d.Trim();
        }
        catch
        {
            // keep file name
        }
        AddApp(new AllowedApp { Name = Path.GetFileNameWithoutExtension(path).ToLowerInvariant(), Path = path, Label = label });
    }

    void AddApp(AllowedApp app)
    {
        if (Rules.StrictBlocked.Contains(app.Name))
        {
            MessageBox.Show(FindForm(), $"{app.Label} can be used in basic mode, but strict mode always blocks it.",
                "SleepGuard", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
        Apply(s =>
        {
            s.AllowedApps.RemoveAll(a => a.Name == app.Name);
            s.AllowedApps.Add(app);
        });
    }

    void RemoveApp()
    {
        if (lstApps.SelectedItem is not AllowedApp app) return;
        Apply(s => s.AllowedApps.RemoveAll(a => a.Name == app.Name));
    }

    public void Reload()
    {
        loading = true;
        var s = engine.Settings;
        bool locked = engine.IsStrictActive;

        banner.Visible = engine.State.Active;
        banner.Text = locked
            ? $"🔒 Strict sleep mode is on. Settings unlock at {Fmt.Time(engine.State.End)}."
            : "Sleep mode is on. Changes apply from the next night.";

        chkEnabled.Checked = s.ScheduleEnabled;
        SelectTime(cmbBed, s.BedHour, s.BedMinute);
        SelectTime(cmbWake, s.WakeHour, s.WakeMinute);
        duration.Text = $"{Fmt.Duration(Schedule.Duration(s))} of sleep time";
        for (int i = 0; i < DayOrder.Length; i++)
        {
            days[i].Checked = s.Days.Contains(DayOrder[i]);
            days[i].ForeColor = days[i].Checked ? Theme.OnAccent : Theme.Text;
        }
        rbBasic.Checked = s.Mode == BlockMode.Basic;
        rbStrict.Checked = s.Mode == BlockMode.Strict;
        chkTaskMgr.Checked = s.StrictBlockTaskManager;
        chkTaskMgr.Visible = s.Mode == BlockMode.Strict;

        var selected = (lstApps.SelectedItem as AllowedApp)?.Name;
        lstApps.Items.Clear();
        foreach (var a in s.AllowedApps.OrderBy(a => a.Label, StringComparer.CurrentCultureIgnoreCase))
        {
            lstApps.Items.Add(a);
            if (a.Name == selected) lstApps.SelectedItem = a;
        }

        chkStartup.Checked = s.StartWithWindows;
        chkWindDown.Checked = s.WindDownReminder;
        numIdle.Value = Math.Clamp(s.SleepPcAfterIdleMinutes, 0, 240);

        scheduleCard.Enabled = !locked;
        modeCard.Enabled = !locked;
        appsCard.Enabled = !locked;
        optionsCard.Enabled = !locked;
        loading = false;
    }
}

// =====================================================================================
// History
// =====================================================================================

sealed class HistoryView : UserControl
{
    readonly Engine engine;
    readonly ChartPanel chart;
    readonly Label summary, empty;
    readonly ListView list;
    readonly FlowLayoutPanel chartCard;

    public HistoryView(Engine engine)
    {
        this.engine = engine;
        BackColor = Theme.Bg;
        var page = Page.Create();

        empty = Theme.Lbl(
            "No nights tracked yet. Each sleep session is saved here with estimated sleep, PC use, wake-ups and blocked apps.",
            10.5f, color: Theme.Muted, maxWidth: Theme.CardWidth);

        chartCard = Theme.CardPanel();
        chartCard.Controls.Add(Theme.Lbl("Last 7 nights", 12, FontStyle.Bold));
        summary = Theme.Lbl("", 10, color: Theme.Muted, maxWidth: Theme.CardWidth - 40);
        chart = new ChartPanel { Width = Theme.CardWidth - 36, Height = 170, BackColor = Theme.Card };
        var legend = Theme.Lbl("Bars: estimated sleep   ·   Dashed line: scheduled sleep time", 9, color: Theme.Muted);
        chartCard.Controls.AddRange(new Control[] { summary, chart, legend });

        list = new ListView
        {
            View = View.Details,
            FullRowSelect = true,
            OwnerDraw = true,
            BackColor = Theme.Card,
            ForeColor = Theme.Text,
            BorderStyle = BorderStyle.None,
            HeaderStyle = ColumnHeaderStyle.Nonclickable,
            Width = Theme.CardWidth,
            Height = 300,
            Font = Theme.F(9.5f),
            Margin = new Padding(0, 0, 0, 6),
        };
        (string Text, int Width)[] cols =
        {
            ("Night", 105), ("Time", 135), ("Est. sleep", 80), ("PC use", 65),
            ("Wake-ups", 70), ("Blocked", 62), ("Result", 95), ("Rating", 64),
        };
        foreach (var (text, width) in cols) list.Columns.Add(text, width);
        list.DrawColumnHeader += (_, e) =>
        {
            using var b = new SolidBrush(Theme.Card2);
            e.Graphics.FillRectangle(b, e.Bounds);
            TextRenderer.DrawText(e.Graphics, e.Header?.Text, list.Font, Rectangle.Inflate(e.Bounds, -6, 0),
                Theme.Muted, TextFormatFlags.VerticalCenter | TextFormatFlags.Left);
        };
        list.DrawItem += (_, e) => e.DrawDefault = true;
        list.DrawSubItem += (_, e) => e.DrawDefault = true;

        var rate = new ContextMenuStrip();
        for (int r = 1; r <= 5; r++)
        {
            int stars = r;
            rate.Items.Add($"Rate {new string('★', stars)}", null, (_, _) => RateSelected(stars));
        }
        list.ContextMenuStrip = rate;
        var hint = Theme.Lbl("Right-click a night to rate how you slept.", 9, color: Theme.Muted);

        page.Controls.AddRange(new Control[] { empty, chartCard, list, hint });
        Controls.Add(page);
    }

    void RateSelected(int stars)
    {
        if (list.SelectedItems.Count == 0 || list.SelectedItems[0].Tag is not Night n) return;
        engine.Rate(n, stars);
    }

    public void Reload()
    {
        var nights = engine.History.OrderByDescending(n => n.Start).ToList();
        empty.Visible = nights.Count == 0;
        chartCard.Visible = nights.Count > 0;
        list.Visible = nights.Count > 0;

        var recent = nights.Take(7).ToList();
        if (recent.Count > 0)
        {
            var avgSleep = TimeSpan.FromTicks((long)recent.Average(n => n.EstimatedSleep.Ticks));
            var avgUse = TimeSpan.FromMilliseconds(recent.Average(n => n.ActiveMs));
            var last30 = nights.Take(30).ToList();
            int kept = last30.Count(n => n.EndReason == Reasons.Completed) * 100 / last30.Count;
            summary.Text = $"Average sleep {Fmt.Duration(avgSleep)}   ·   average PC use at night {Fmt.Duration(avgUse)}   ·   full nights kept {kept}%";
        }
        chart.Nights = recent.AsEnumerable().Reverse().ToList();
        chart.Goal = Schedule.Duration(engine.Settings);
        chart.Invalidate();

        list.BeginUpdate();
        list.Items.Clear();
        foreach (var n in nights)
        {
            var result = n.EndReason switch
            {
                Reasons.Completed => "Completed",
                Reasons.Emergency => "Emergency",
                _ => "Ended early",
            };
            var item = new ListViewItem(new[]
            {
                Fmt.Day(n.Start),
                $"{Fmt.Time(n.Start)} – {Fmt.Time(n.End)}",
                Fmt.Duration(n.EstimatedSleep),
                Fmt.Duration(TimeSpan.FromMilliseconds(n.ActiveMs)),
                n.Wakes.ToString(),
                n.Blocked.ToString(),
                $"{result} ({Engine.ModeName(n.Mode)})",
                n.Rating > 0 ? new string('★', n.Rating) : "—",
            })
            { Tag = n };
            list.Items.Add(item);
        }
        list.EndUpdate();
    }
}

sealed class ChartPanel : Panel
{
    [DesignerSerializationVisibility(DesignerSerializationVisibility.Hidden)]
    public List<Night> Nights { get; set; } = new();

    [DesignerSerializationVisibility(DesignerSerializationVisibility.Hidden)]
    public TimeSpan Goal { get; set; } = TimeSpan.FromHours(8);

    public ChartPanel()
    {
        DoubleBuffered = true;
        ResizeRedraw = true;
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        var g = e.Graphics;
        g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        const int slots = 7;
        float labelH = 22;
        float h = Height - labelH;
        float slot = Width / (float)slots;
        float barW = slot * 0.46f;
        double top = Math.Max(Goal.TotalHours, 8);
        if (Nights.Count > 0) top = Math.Max(top, Nights.Max(n => n.EstimatedSleep.TotalHours));
        double maxH = Math.Ceiling(top + 1);
        int pad = slots - Nights.Count;

        using var track = new SolidBrush(Theme.Card2);
        using var bar = new SolidBrush(Theme.Accent);
        using var font = Theme.F(8.5f);
        for (int i = 0; i < slots; i++)
        {
            float x = slot * i + (slot - barW) / 2f;
            FillPill(g, track, x, 0, barW, h);
            int idx = i - pad;
            if (idx < 0 || idx >= Nights.Count) continue;
            var n = Nights[idx];
            float bh = (float)(n.EstimatedSleep.TotalHours / maxH * h);
            FillPill(g, bar, x, h - bh, barW, bh);
            var day = n.Start.ToString("ddd");
            var size = TextRenderer.MeasureText(day, font);
            TextRenderer.DrawText(g, day, font, new Point((int)(slot * i + (slot - size.Width) / 2f), (int)h + 4), Theme.Muted);
        }

        float gy = h - (float)(Goal.TotalHours / maxH * h);
        using var pen = new Pen(Theme.Gold, 2) { DashStyle = System.Drawing.Drawing2D.DashStyle.Dash };
        g.DrawLine(pen, 0, gy, Width, gy);
    }

    static void FillPill(Graphics g, Brush b, float x, float y, float w, float h)
    {
        if (h <= 1) return;
        float d = Math.Min(w, h);
        using var p = new System.Drawing.Drawing2D.GraphicsPath();
        p.AddArc(x, y, w, d, 180, 180);
        p.AddArc(x, y + h - d, w, d, 0, 180);
        p.CloseFigure();
        g.FillPath(b, p);
    }
}
