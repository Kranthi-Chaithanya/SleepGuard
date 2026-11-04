using System.Diagnostics;
using System.Runtime.InteropServices;
using Microsoft.Win32;

namespace SleepGuard.Desktop;

static class Native
{
    public const int SW_MINIMIZE = 6;
    public const int SW_RESTORE = 9;
    public const uint GW_OWNER = 4;
    public const int GWL_EXSTYLE = -20;
    public const int WS_EX_TOOLWINDOW = 0x80;
    const int DWMWA_CLOAKED = 14;
    const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
    [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc callback, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool EnumChildWindows(IntPtr hWndParent, EnumWindowsProc callback, IntPtr lParam);
    [DllImport("user32.dll")] public static extern IntPtr GetWindow(IntPtr hWnd, uint uCmd);
    [DllImport("user32.dll", EntryPoint = "GetWindowLongW")] public static extern int GetWindowLong(IntPtr hWnd, int nIndex);
    [DllImport("user32.dll", EntryPoint = "GetWindowTextLengthW")] public static extern int GetWindowTextLength(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern bool LockWorkStation();

    [StructLayout(LayoutKind.Sequential)]
    struct LASTINPUTINFO
    {
        public uint cbSize;
        public uint dwTime;
    }

    [DllImport("user32.dll")] static extern bool GetLastInputInfo(ref LASTINPUTINFO plii);
    [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);
    [DllImport("dwmapi.dll")] static extern int DwmGetWindowAttribute(IntPtr hwnd, int attr, out int value, int size);

    /// <summary>Time since the last keyboard or mouse input.</summary>
    public static TimeSpan IdleTime()
    {
        var lii = new LASTINPUTINFO { cbSize = (uint)Marshal.SizeOf<LASTINPUTINFO>() };
        if (!GetLastInputInfo(ref lii)) return TimeSpan.Zero;
        uint ms = unchecked((uint)Environment.TickCount - lii.dwTime);
        return TimeSpan.FromMilliseconds(ms);
    }

    public static bool IsCloaked(IntPtr hwnd)
    {
        try
        {
            return DwmGetWindowAttribute(hwnd, DWMWA_CLOAKED, out int cloaked, sizeof(int)) == 0 && cloaked != 0;
        }
        catch
        {
            return false;
        }
    }

    public static void DarkTitleBar(IntPtr hwnd)
    {
        try
        {
            int on = 1;
            DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, ref on, sizeof(int));
        }
        catch
        {
            // older Windows: keep the default title bar
        }
    }
}

/// <summary>Window and process helpers.</summary>
static class Win
{
    static readonly Dictionary<uint, (string Name, DateTime At)> NameCache = new();

    public static string NameOfPid(uint pid)
    {
        if (pid == 0) return "";
        if (NameCache.TryGetValue(pid, out var c) && (DateTime.Now - c.At).TotalSeconds < 30) return c.Name;
        string name;
        try
        {
            using var p = Process.GetProcessById((int)pid);
            name = p.ProcessName.ToLowerInvariant();
        }
        catch
        {
            name = "";
        }
        if (NameCache.Count > 500) NameCache.Clear();
        NameCache[pid] = (name, DateTime.Now);
        return name;
    }

    /// <summary>The app that owns a window. Store (UWP) apps are resolved through ApplicationFrameHost.</summary>
    public static (uint Pid, string Name) AppForWindow(IntPtr hwnd)
    {
        Native.GetWindowThreadProcessId(hwnd, out uint pid);
        var name = NameOfPid(pid);
        if (name == "applicationframehost")
        {
            uint realPid = 0;
            Native.EnumChildWindows(hwnd, (child, _) =>
            {
                Native.GetWindowThreadProcessId(child, out uint childPid);
                if (childPid != 0 && childPid != pid)
                {
                    realPid = childPid;
                    return false;
                }
                return true;
            }, IntPtr.Zero);
            if (realPid != 0) return (realPid, NameOfPid(realPid));
        }
        return (pid, name);
    }

    /// <summary>Visible, non-minimized top-level app windows (what you'd see in Alt+Tab).</summary>
    public static List<IntPtr> TopLevelAppWindows()
    {
        var list = new List<IntPtr>();
        Native.EnumWindows((h, _) =>
        {
            if (IsAppWindow(h)) list.Add(h);
            return true;
        }, IntPtr.Zero);
        return list;
    }

    static bool IsAppWindow(IntPtr h)
    {
        if (!Native.IsWindowVisible(h) || Native.IsIconic(h)) return false;
        if (Native.GetWindow(h, Native.GW_OWNER) != IntPtr.Zero) return false;
        if ((Native.GetWindowLong(h, Native.GWL_EXSTYLE) & Native.WS_EX_TOOLWINDOW) != 0) return false;
        if (Native.GetWindowTextLength(h) == 0) return false;
        return !Native.IsCloaked(h);
    }

    public static string FriendlyName(uint pid, string name)
    {
        try
        {
            using var p = Process.GetProcessById((int)pid);
            var file = p.MainModule?.FileName;
            if (file != null)
            {
                var d = FileVersionInfo.GetVersionInfo(file).FileDescription;
                if (!string.IsNullOrWhiteSpace(d)) return d.Trim();
            }
        }
        catch
        {
            // elevated or exited process
        }
        return name.Length > 0 ? char.ToUpperInvariant(name[0]) + name[1..] : "This app";
    }

    /// <summary>Bring one of our windows to the front even when another app has focus.</summary>
    public static void ForceForeground(IntPtr h)
    {
        var fg = Native.GetForegroundWindow();
        uint fgThread = Native.GetWindowThreadProcessId(fg, out _);
        uint me = Native.GetCurrentThreadId();
        bool attached = fgThread != 0 && fgThread != me && Native.AttachThreadInput(me, fgThread, true);
        Native.BringWindowToTop(h);
        Native.SetForegroundWindow(h);
        if (attached) Native.AttachThreadInput(me, fgThread, false);
    }

    public static void Launch(string fileOrUri, string? args = null)
    {
        try
        {
            var psi = new ProcessStartInfo(fileOrUri) { UseShellExecute = true };
            if (args != null) psi.Arguments = args;
            Process.Start(psi);
        }
        catch
        {
            // not installed
        }
    }

    public static void OpenPhoneLink() =>
        Launch("explorer.exe", @"shell:AppsFolder\Microsoft.YourPhone_8wekyb3d8bbwe!App");
}

/// <summary>Per-user registry switches (no admin needed).</summary>
static class Policy
{
    const string SystemKey = @"Software\Microsoft\Windows\CurrentVersion\Policies\System";
    const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

    public static void SetTaskManagerDisabled(bool disabled)
    {
        try
        {
            using var k = Registry.CurrentUser.CreateSubKey(SystemKey);
            if (disabled) k.SetValue("DisableTaskMgr", 1, RegistryValueKind.DWord);
            else k.DeleteValue("DisableTaskMgr", false);
        }
        catch
        {
            // policy key not writable on this PC
        }
    }

    public static void SetStartup(bool enabled)
    {
        try
        {
            using var k = Registry.CurrentUser.CreateSubKey(RunKey);
            var exe = Environment.ProcessPath;
            if (enabled && exe != null) k.SetValue("SleepGuard", $"\"{exe}\" --tray");
            else k.DeleteValue("SleepGuard", false);
        }
        catch
        {
            // ignore
        }
    }
}

/// <summary>Which apps are always allowed / extra-blocked.</summary>
static class Rules
{
    public static readonly string Self = Process.GetCurrentProcess().ProcessName.ToLowerInvariant();

    /// <summary>Calls, alarms, lock screen and Windows sign-in/UAC prompts.</summary>
    public static readonly HashSet<string> AlwaysAllowed = new(StringComparer.OrdinalIgnoreCase)
    {
        "phoneexperiencehost", "yourphone", "crossdeviceservice", // Phone Link (calls from your phone)
        "time",                                                  // Windows Clock / Alarms
        "lockapp", "logonui", "consent", "credentialuibroker", "textinputhost", "dwm",
    };

    /// <summary>Desktop, taskbar, Start, search, File Explorer. Allowed in basic mode only.</summary>
    public static readonly HashSet<string> Shell = new(StringComparer.OrdinalIgnoreCase)
    {
        "explorer", "shellexperiencehost", "startmenuexperiencehost", "searchhost", "searchapp", "searchui", "shellhost",
    };

    /// <summary>Allowed in basic mode only.</summary>
    public static readonly HashSet<string> BasicExtra = new(StringComparer.OrdinalIgnoreCase)
    {
        "systemsettings", "taskmgr", "control",
    };

    /// <summary>Always blocked in strict mode, even if the user allowed them: ways to switch SleepGuard off.</summary>
    public static readonly HashSet<string> StrictBlocked = new(StringComparer.OrdinalIgnoreCase)
    {
        "taskmgr", "regedit", "mmc", "systemsettings", "control", "msconfig",
        "cmd", "powershell", "pwsh", "windowsterminal", "wt", "openconsole",
        "procexp", "procexp64", "processhacker", "systeminformer",
    };
}
