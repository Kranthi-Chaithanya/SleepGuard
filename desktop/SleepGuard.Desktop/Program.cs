namespace SleepGuard.Desktop;

static class Program
{
    [STAThread]
    static void Main(string[] args)
    {
        using var mutex = new Mutex(true, @"Local\SleepGuard.Desktop", out bool firstInstance);
        if (!firstInstance)
        {
            MessageBox.Show("SleepGuard is already running. Look for the moon icon near the clock.",
                "SleepGuard", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        ApplicationConfiguration.Initialize();
        Application.Run(new TrayApp(startHidden: args.Contains("--tray")));
        GC.KeepAlive(mutex);
    }
}

/// <summary>Lives in the notification area and drives the engine.</summary>
sealed class TrayApp : ApplicationContext
{
    readonly Engine engine;
    readonly NotifyIcon tray;
    readonly System.Windows.Forms.Timer timer;
    readonly ToolStripMenuItem miStatus;
    readonly ToolStripMenuItem miStart;
    readonly ToolStripMenuItem miEnd;
    readonly ToolStripMenuItem miExit;
    MainForm? main;
    bool exiting;

    public TrayApp(bool startHidden)
    {
        engine = new Engine();

        miStatus = new ToolStripMenuItem("SleepGuard") { Enabled = false };
        var miOpen = new ToolStripMenuItem("Open SleepGuard", null, (_, _) => ShowMain());
        miOpen.Font = new Font(miOpen.Font, FontStyle.Bold);
        miStart = new ToolStripMenuItem("Start sleep now", null, (_, _) => Dialogs.ConfirmStart(engine, null));
        miEnd = new ToolStripMenuItem("End sleep mode", null, (_, _) => Dialogs.ConfirmEnd(engine, null));
        miExit = new ToolStripMenuItem("Exit", null, (_, _) => ExitApp());

        var menu = new ContextMenuStrip();
        menu.Items.AddRange(new ToolStripItem[]
        {
            miStatus, new ToolStripSeparator(), miOpen, miStart, miEnd, new ToolStripSeparator(), miExit,
        });
        menu.Opening += (_, _) => UpdateMenu();

        tray = new NotifyIcon
        {
            Icon = AppIcon.Get(),
            Text = "SleepGuard",
            ContextMenuStrip = menu,
            Visible = true,
        };
        tray.DoubleClick += (_, _) => ShowMain();
        tray.BalloonTipClicked += (_, _) => ShowMain();

        engine.Notify += (title, text) => tray.ShowBalloonTip(6000, title, text, ToolTipIcon.Info);
        engine.Changed += UpdateMenu;

        timer = new System.Windows.Forms.Timer { Interval = 500 };
        timer.Tick += (_, _) => engine.Tick();

        Application.ApplicationExit += (_, _) => Cleanup();

        engine.Startup();
        timer.Start();
        UpdateMenu();
        if (!startHidden) ShowMain();
    }

    void UpdateMenu()
    {
        var s = engine.State;
        string status;
        if (s.Active)
        {
            status = $"Sleep mode on · until {Fmt.Time(s.End)}";
        }
        else if (Schedule.NextWindow(DateTime.Now, engine.Settings) is { } next)
        {
            status = $"Next bedtime: {Fmt.RelDay(next.Start, DateTime.Now)} {Fmt.Time(next.Start)}";
        }
        else
        {
            status = "No sleep schedule set";
        }
        miStatus.Text = status;
        tray.Text = ("SleepGuard · " + status).Length > 63 ? "SleepGuard" : "SleepGuard · " + status;
        miStart.Visible = !s.Active;
        miEnd.Visible = s.Active && s.Mode == BlockMode.Basic;
        miExit.Enabled = !engine.IsStrictActive;
        miExit.Text = engine.IsStrictActive ? "Exit (locked until wake-up)" : "Exit";
    }

    void ShowMain()
    {
        if (main == null || main.IsDisposed)
        {
            main = new MainForm(engine);
            main.FormClosed += (_, _) => main = null;
        }
        main.Show();
        if (main.WindowState == FormWindowState.Minimized) main.WindowState = FormWindowState.Normal;
        main.Activate();
    }

    void ExitApp()
    {
        if (engine.IsStrictActive) return;
        if (engine.State.Active)
        {
            var r = MessageBox.Show(
                "Sleep mode is on. Exiting ends it for tonight and unblocks your apps. Exit anyway?",
                "SleepGuard", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
            if (r != DialogResult.Yes) return;
            engine.End(Reasons.EndedEarly);
        }
        Cleanup();
        ExitThread();
    }

    void Cleanup()
    {
        if (exiting) return;
        exiting = true;
        timer.Stop();
        tray.Visible = false;
        main?.Close();
        engine.Dispose();
    }
}
