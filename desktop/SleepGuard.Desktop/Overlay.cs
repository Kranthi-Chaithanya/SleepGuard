using System.ComponentModel;

namespace SleepGuard.Desktop;

/// <summary>One full-screen "Time to sleep" window per monitor.</summary>
sealed class OverlayManager : IDisposable
{
    readonly Engine engine;
    readonly List<OverlayForm> forms = new();
    OverlayForm? main;
    DateTime lastFocus;

    public OverlayManager(Engine engine) => this.engine = engine;

    public bool Visible { get; private set; }

    public void ShowAll()
    {
        EnsureForms();
        foreach (var f in forms)
        {
            if (!f.Visible) f.Show();
        }
        main?.UpdateContent();
        if (main != null && (DateTime.Now - lastFocus).TotalMilliseconds > 900)
        {
            lastFocus = DateTime.Now;
            main.TopMost = true;
            Win.ForceForeground(main.Handle);
        }
        Visible = true;
    }

    public void HideAll()
    {
        foreach (var f in forms) f.Hide();
        Visible = false;
    }

    public void Refresh()
    {
        if (Visible) main?.UpdateTimes();
    }

    void EnsureForms()
    {
        var screens = Screen.AllScreens;
        bool same = forms.Count == screens.Length &&
                    forms.Zip(screens).All(p => p.First.ScreenBounds == p.Second.Bounds);
        if (same) return;

        DisposeForms();
        bool hasPrimary = screens.Any(s => s.Primary);
        for (int i = 0; i < screens.Length; i++)
        {
            bool isMain = screens[i].Primary || (!hasPrimary && i == 0);
            var f = new OverlayForm(engine, screens[i], isMain);
            forms.Add(f);
            if (isMain) main = f;
        }
    }

    void DisposeForms()
    {
        foreach (var f in forms)
        {
            f.AllowClose = true;
            f.Close();
            f.Dispose();
        }
        forms.Clear();
        main = null;
    }

    public void Dispose() => DisposeForms();
}

sealed class OverlayForm : Form
{
    readonly Engine engine;
    readonly bool isMain;
    readonly FlowLayoutPanel stack = new();
    readonly FlowLayoutPanel buttons = new();
    readonly Label title;
    readonly Label sub;
    readonly Label blocked;
    readonly Label hint;
    string buttonsKey = "";

    public Rectangle ScreenBounds { get; }
    [DesignerSerializationVisibility(DesignerSerializationVisibility.Hidden)]
    public bool AllowClose { get; set; }

    public OverlayForm(Engine engine, Screen screen, bool isMain)
    {
        this.engine = engine;
        this.isMain = isMain;
        ScreenBounds = screen.Bounds;

        AutoScaleMode = AutoScaleMode.None;
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.Manual;
        Bounds = screen.Bounds;
        TopMost = true;
        ShowInTaskbar = false;
        BackColor = Theme.OverlayBg;
        ForeColor = Theme.Text;
        DoubleBuffered = true;
        Text = "SleepGuard";

        title = Theme.Lbl("Time to sleep", 34, FontStyle.Bold);
        sub = Theme.Lbl("", 14, color: Theme.Muted);
        blocked = Theme.Lbl("", 12, color: Theme.Accent, maxWidth: 820);
        hint = Theme.Lbl("", 10, color: Theme.Muted, maxWidth: 820);
        hint.Margin = new Padding(0, 18, 0, 0);

        if (!isMain) return;

        stack.FlowDirection = FlowDirection.TopDown;
        stack.WrapContents = false;
        stack.AutoSize = true;
        stack.AutoSizeMode = AutoSizeMode.GrowAndShrink;
        stack.BackColor = BackColor;

        buttons.FlowDirection = FlowDirection.LeftToRight;
        buttons.WrapContents = true;
        buttons.AutoSize = true;
        buttons.AutoSizeMode = AutoSizeMode.GrowAndShrink;
        buttons.MaximumSize = new Size(820, 0);
        buttons.BackColor = BackColor;
        buttons.Margin = new Padding(0, 26, 0, 0);

        var moon = new MoonBox { Size = new Size(130, 130), BackColor = BackColor, Margin = new Padding(0, 0, 0, 18) };
        stack.Controls.AddRange(new Control[] { moon, title, sub, blocked, buttons, hint });
        Controls.Add(stack);

        Resize += (_, _) => CenterStack();
        stack.SizeChanged += (_, _) => CenterStack();
    }

    // Keep the overlay out of Alt+Tab.
    protected override CreateParams CreateParams
    {
        get
        {
            var cp = base.CreateParams;
            cp.ExStyle |= Native.WS_EX_TOOLWINDOW;
            return cp;
        }
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        // Alt+F4 does nothing while sleep mode is on.
        if (!AllowClose && e.CloseReason == CloseReason.UserClosing && engine.State.Active)
        {
            e.Cancel = true;
            return;
        }
        base.OnFormClosing(e);
    }

    void CenterStack() =>
        stack.Location = new Point(
            Math.Max(0, (ClientSize.Width - stack.Width) / 2),
            Math.Max(0, (ClientSize.Height - stack.Height) / 2));

    public void UpdateTimes()
    {
        if (!isMain) return;
        var s = engine.State;
        sub.Text = $"SleepGuard is on until {Fmt.Time(s.End)}  ·  {Fmt.Duration(s.End - DateTime.Now)} left  ·  {Engine.ModeName(s.Mode)} mode";
    }

    public void UpdateContent()
    {
        if (!isMain) return;
        UpdateTimes();
        bool strict = engine.State.Mode == BlockMode.Strict;
        var label = engine.LastBlockedLabel;
        blocked.Text = label != null
            ? $"{label} was minimized. Other apps are blocked until morning."
            : "Apps are blocked until morning.";
        hint.Text = strict
            ? "Strict mode: this screen stays until wake-up. Phone Link (calls), Clock and your allowed apps still work."
            : "Basic mode: you can go back to the desktop, but other apps stay blocked until wake-up.";

        var allowed = engine.Settings.AllowedApps.Where(a => !string.IsNullOrEmpty(a.Path)).Take(4).ToList();
        var key = $"{strict}|{engine.CanSnooze}|{engine.SnoozesLeft}|{label}|{string.Join(",", allowed.Select(a => a.Name))}";
        if (key == buttonsKey) return;
        buttonsKey = key;

        buttons.SuspendLayout();
        var old = buttons.Controls.Cast<Control>().ToList();
        buttons.Controls.Clear();
        foreach (var c in old) c.Dispose();

        Add("Open Phone Link (calls)", true, Win.OpenPhoneLink);
        foreach (var a in allowed)
        {
            var path = a.Path!;
            Add($"Open {(string.IsNullOrWhiteSpace(a.Label) ? a.Name : a.Label)}", false, () => Win.Launch(path));
        }
        if (!strict)
        {
            if (engine.CanSnooze && label != null)
                Add($"Use {label} for 5 min ({engine.SnoozesLeft} left)", false, engine.Snooze);
            Add("Back to desktop", false, engine.ReturnToDesktop);
        }
        Add("Sleep PC", false, () => Application.SetSuspendState(PowerState.Suspend, false, false));
        Add("Lock PC", false, () => Native.LockWorkStation());
        if (strict) Add("Emergency unlock", false, EmergencyUnlock);
        else Add("End sleep mode", false, ConfirmEnd);

        buttons.ResumeLayout();
        CenterStack();
    }

    void Add(string text, bool primary, Action onClick)
    {
        var b = Theme.Btn(text, primary);
        b.Font = Theme.F(11, FontStyle.Bold);
        b.Padding = new Padding(16, 8, 16, 8);
        b.Click += (_, _) => onClick();
        buttons.Controls.Add(b);
    }

    void ConfirmEnd()
    {
        var r = MessageBox.Show(this,
            "End sleep mode early? All apps will be unblocked and tonight will be saved as ended early.",
            "SleepGuard", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
        if (r == DialogResult.Yes) engine.End(Reasons.EndedEarly);
    }

    void EmergencyUnlock()
    {
        using var f = new EmergencyForm();
        if (f.ShowDialog(this) == DialogResult.OK) engine.End(Reasons.Emergency);
    }
}
