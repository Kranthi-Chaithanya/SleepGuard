using System.ComponentModel;
using System.Diagnostics;

namespace SleepGuard.Desktop;

static class Dialogs
{
    public static void ConfirmStart(Engine engine, IWin32Window? owner)
    {
        if (engine.State.Active) return;
        var until = Fmt.Time(Schedule.NextWakeAfter(DateTime.Now, engine.Settings));
        if (engine.Settings.Mode == BlockMode.Strict)
        {
            var r = MessageBox.Show(owner,
                $"Start STRICT sleep mode until {until}?\n\n" +
                "A full-screen sleep screen will cover the PC. Every app except Phone Link (calls), Clock and your " +
                "allowed apps will be blocked. Task Manager, Settings and command prompts are blocked too, " +
                "and you can't end it early — only a long emergency code unlocks it.",
                "SleepGuard", MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
            if (r != DialogResult.Yes) return;
        }
        engine.StartNow();
    }

    public static void ConfirmEnd(Engine engine, IWin32Window? owner)
    {
        if (!engine.State.Active || engine.State.Mode != BlockMode.Basic) return;
        var r = MessageBox.Show(owner,
            "End sleep mode early? All apps will be unblocked and tonight will be saved as ended early.",
            "SleepGuard", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
        if (r == DialogResult.Yes) engine.End(Reasons.EndedEarly);
    }

    public static void Emergency(Engine engine, IWin32Window? owner)
    {
        using var f = new EmergencyForm();
        if (f.ShowDialog(owner) == DialogResult.OK) engine.End(Reasons.Emergency);
    }

    public static void StyleDialog(Form f)
    {
        f.AutoScaleDimensions = new SizeF(96F, 96F);
        f.AutoScaleMode = AutoScaleMode.Dpi;
        f.BackColor = Theme.Bg;
        f.ForeColor = Theme.Text;
        f.Font = Theme.F(10);
        f.Icon = AppIcon.Get();
        f.StartPosition = FormStartPosition.CenterScreen;
        f.HandleCreated += (_, _) => Native.DarkTitleBar(f.Handle);
    }
}

/// <summary>Strict mode's only way out: type a random code by hand (no pasting).</summary>
sealed class EmergencyForm : Form
{
    const string Chars = "abcdefghjkmnpqrstuvwxyz23456789";
    readonly string code;

    public EmergencyForm()
    {
        var rnd = Random.Shared;
        code = string.Join("-", Enumerable.Range(0, 5).Select(group =>
            new string(Enumerable.Range(0, 4).Select(i => Chars[rnd.Next(Chars.Length)]).ToArray())));

        Dialogs.StyleDialog(this);
        Text = "Emergency unlock";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        TopMost = true;
        ClientSize = new Size(500, 270);

        var col = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            Padding = new Padding(20),
            BackColor = Theme.Bg,
        };
        var info = Theme.Lbl("Type this code exactly to end strict sleep mode. Tonight will be saved as an emergency unlock.",
            10, color: Theme.Muted, maxWidth: 450);
        var codeLabel = Theme.Lbl(code, 18, FontStyle.Bold, Theme.Gold);
        codeLabel.Font = new Font("Consolas", 18, FontStyle.Bold);
        codeLabel.Margin = new Padding(0, 8, 0, 12);

        var input = new TextBox
        {
            Width = 450,
            Font = new Font("Consolas", 14),
            BackColor = Theme.Card2,
            ForeColor = Theme.Text,
            BorderStyle = BorderStyle.FixedSingle,
            ShortcutsEnabled = false, // no paste
            CharacterCasing = CharacterCasing.Lower,
        };

        var row = Theme.Row(460);
        row.Margin = new Padding(0, 16, 0, 0);
        var unlock = Theme.Btn("Unlock", true);
        unlock.Enabled = false;
        var cancel = Theme.Btn("Keep sleeping");
        unlock.Click += (_, _) => { DialogResult = DialogResult.OK; Close(); };
        cancel.Click += (_, _) => { DialogResult = DialogResult.Cancel; Close(); };
        input.TextChanged += (_, _) => unlock.Enabled = input.Text.Trim() == code;
        row.Controls.AddRange(new Control[] { unlock, cancel });

        col.Controls.AddRange(new Control[] { info, codeLabel, input, row });
        Controls.Add(col);
        CancelButton = cancel;
    }
}

/// <summary>Pick one of the apps that currently has a window open.</summary>
sealed class PickAppForm : Form
{
    readonly ListBox list;
    [DesignerSerializationVisibility(DesignerSerializationVisibility.Hidden)]
    public AllowedApp? Result { get; private set; }

    public PickAppForm()
    {
        Dialogs.StyleDialog(this);
        Text = "Allow a running app";
        ClientSize = new Size(480, 440);
        MinimizeBox = false;
        MaximizeBox = false;

        var top = Theme.Lbl("Choose an app to keep allowed during sleep time:", 10, color: Theme.Muted);
        top.Dock = DockStyle.Top;
        top.Padding = new Padding(14, 12, 14, 8);
        top.AutoSize = false;
        top.Height = 40;

        list = new ListBox
        {
            Dock = DockStyle.Fill,
            BackColor = Theme.Card,
            ForeColor = Theme.Text,
            BorderStyle = BorderStyle.None,
            Font = Theme.F(11),
            IntegralHeight = false,
        };

        var bottom = new FlowLayoutPanel
        {
            Dock = DockStyle.Bottom,
            Height = 56,
            FlowDirection = FlowDirection.RightToLeft,
            Padding = new Padding(10),
            BackColor = Theme.Bg,
        };
        var add = Theme.Btn("Allow", true);
        var cancel = Theme.Btn("Cancel");
        add.Click += (_, _) => Accept();
        cancel.Click += (_, _) => { DialogResult = DialogResult.Cancel; Close(); };
        list.DoubleClick += (_, _) => Accept();
        bottom.Controls.AddRange(new Control[] { add, cancel });

        Controls.Add(list);
        Controls.Add(bottom);
        Controls.Add(top);
        CancelButton = cancel;

        foreach (var app in RunningApps()) list.Items.Add(app);
    }

    void Accept()
    {
        if (list.SelectedItem is not AllowedApp a) return;
        Result = a;
        DialogResult = DialogResult.OK;
        Close();
    }

    static List<AllowedApp> RunningApps()
    {
        var apps = new Dictionary<string, AllowedApp>();
        foreach (var p in Process.GetProcesses())
        {
            try
            {
                if (p.Id == Environment.ProcessId || p.MainWindowHandle == IntPtr.Zero || string.IsNullOrWhiteSpace(p.MainWindowTitle))
                    continue;
                var name = p.ProcessName.ToLowerInvariant();
                if (name == "applicationframehost" || apps.ContainsKey(name)) continue;
                string? path = null;
                string label = p.MainWindowTitle;
                try
                {
                    path = p.MainModule?.FileName;
                    if (path != null)
                    {
                        var d = FileVersionInfo.GetVersionInfo(path).FileDescription;
                        if (!string.IsNullOrWhiteSpace(d)) label = d.Trim();
                    }
                }
                catch
                {
                    // elevated process: no path
                }
                apps[name] = new AllowedApp { Name = name, Path = path, Label = label };
            }
            catch
            {
                // process exited
            }
            finally
            {
                p.Dispose();
            }
        }
        return apps.Values.OrderBy(a => a.Label, StringComparer.CurrentCultureIgnoreCase).ToList();
    }
}
