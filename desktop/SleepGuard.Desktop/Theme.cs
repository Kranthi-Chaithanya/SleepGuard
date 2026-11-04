using System.Drawing.Drawing2D;

namespace SleepGuard.Desktop;

static class Theme
{
    public static readonly Color Bg = Color.FromArgb(11, 13, 30);
    public static readonly Color OverlayBg = Color.FromArgb(8, 9, 24);
    public static readonly Color Card = Color.FromArgb(21, 24, 46);
    public static readonly Color Card2 = Color.FromArgb(34, 38, 70);
    public static readonly Color Accent = Color.FromArgb(159, 168, 255);
    public static readonly Color AccentDark = Color.FromArgb(46, 49, 112);
    public static readonly Color OnAccent = Color.FromArgb(20, 21, 74);
    public static readonly Color Gold = Color.FromArgb(255, 213, 138);
    public static readonly Color Text = Color.FromArgb(228, 228, 244);
    public static readonly Color Muted = Color.FromArgb(180, 183, 211);
    public static readonly Color Good = Color.FromArgb(127, 216, 196);
    public static readonly Color Bad = Color.FromArgb(255, 180, 171);

    public const int CardWidth = 680;

    public static Font F(float size, FontStyle style = FontStyle.Regular) => new("Segoe UI", size, style);

    public static Label Lbl(string text, float size = 10, FontStyle style = FontStyle.Regular, Color? color = null, int maxWidth = 0) =>
        new()
        {
            Text = text,
            AutoSize = true,
            Font = F(size, style),
            ForeColor = color ?? Text,
            BackColor = Color.Transparent,
            Margin = new Padding(0, 0, 0, 6),
            MaximumSize = new Size(maxWidth, 0),
        };

    public static Button Btn(string text, bool primary = false)
    {
        var b = new Button
        {
            Text = text,
            AutoSize = true,
            AutoSizeMode = AutoSizeMode.GrowAndShrink,
            FlatStyle = FlatStyle.Flat,
            BackColor = primary ? Accent : Card2,
            ForeColor = primary ? OnAccent : Text,
            Font = F(10, FontStyle.Bold),
            Padding = new Padding(12, 6, 12, 6),
            Margin = new Padding(0, 0, 10, 10),
            Cursor = Cursors.Hand,
            UseVisualStyleBackColor = false,
        };
        b.FlatAppearance.BorderSize = 0;
        b.FlatAppearance.MouseOverBackColor = primary ? Color.FromArgb(185, 192, 255) : Color.FromArgb(48, 53, 95);
        return b;
    }

    /// <summary>Vertical stack that sizes to its content.</summary>
    public static FlowLayoutPanel CardPanel(int width = CardWidth) => new()
    {
        FlowDirection = FlowDirection.TopDown,
        WrapContents = false,
        AutoSize = true,
        AutoSizeMode = AutoSizeMode.GrowAndShrink,
        BackColor = Card,
        Padding = new Padding(18),
        Margin = new Padding(0, 0, 0, 14),
        MinimumSize = new Size(width, 0),
        MaximumSize = new Size(width, 0),
    };

    /// <summary>Horizontal row (wraps) for buttons and toggles.</summary>
    public static FlowLayoutPanel Row(int width = CardWidth - 36) => new()
    {
        FlowDirection = FlowDirection.LeftToRight,
        WrapContents = true,
        AutoSize = true,
        AutoSizeMode = AutoSizeMode.GrowAndShrink,
        BackColor = Color.Transparent,
        Margin = new Padding(0, 6, 0, 0),
        MaximumSize = new Size(width, 0),
    };

    public static void Style(CheckBox c)
    {
        c.AutoSize = true;
        c.ForeColor = Text;
        c.BackColor = Color.Transparent;
        c.Font = F(10);
        c.Margin = new Padding(0, 4, 0, 6);
        c.Cursor = Cursors.Hand;
    }

    public static void Style(RadioButton r)
    {
        r.AutoSize = true;
        r.ForeColor = Text;
        r.BackColor = Color.Transparent;
        r.Font = F(11, FontStyle.Bold);
        r.Margin = new Padding(0, 6, 0, 0);
        r.Cursor = Cursors.Hand;
    }
}

/// <summary>Crescent moon logo, drawn (no image files needed).</summary>
sealed class MoonBox : Control
{
    public MoonBox()
    {
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint |
                 ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
        Size = new Size(48, 48);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.Clear(BackColor);
        float r = Math.Min(Width, Height) / 2f * 0.9f;
        float cx = Width / 2f, cy = Height / 2f;
        using var gold = new SolidBrush(Theme.Gold);
        g.FillEllipse(gold, cx - r, cy - r, 2 * r, 2 * r);
        float br = r * 0.85f, bx = cx + r * 0.5f, by = cy - r * 0.4f;
        using var bg = new SolidBrush(BackColor);
        g.FillEllipse(bg, bx - br, by - br, 2 * br, 2 * br);
    }
}

static class AppIcon
{
    static Icon? icon;

    public static Icon Get()
    {
        if (icon != null) return icon;
        try
        {
            using var s = typeof(AppIcon).Assembly.GetManifestResourceStream("SleepGuard.Desktop.app.ico");
            if (s != null) icon = new Icon(s);
        }
        catch
        {
            // fall back below
        }
        return icon ??= SystemIcons.Application;
    }
}
