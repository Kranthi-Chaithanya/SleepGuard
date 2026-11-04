# SleepGuard — sleep tracker + app blocker for Android

Kotlin · Jetpack Compose · Material 3 · minSdk 26 (Android 8) · targetSdk 35 (Android 15)

At bedtime SleepGuard turns on by itself, blocks every app except calls, and tracks the night.
In the morning you get a summary and can rate how you slept.

## Features

| | Basic mode | Strict mode |
|---|---|---|
| Blocks all apps except Phone/calls, Clock, Keyboard, Home screen and your allowed apps | ✅ | ✅ |
| "Use this app for 5 min" (3 per night) | ✅ | ❌ |
| End sleep mode early | ✅ | ❌ (emergency code only) |
| Settings & uninstall dialog reachable | ✅ | ❌ blocked |
| Survives reboot (session resumes) | ✅ | ✅ |
| Change schedule / mode during the night | ✅ (applies next night) | ❌ locked |
| Optional uninstall protection (device admin) | – | ✅ |

**Emergency unlock (strict):** type a random 24-character code by hand. Works in a real emergency,
too tedious for "just one more video". The night is saved as *Emergency unlock*.

**Tracking (per night):** time in sleep mode, number of screen wake-ups, total screen-on time,
blocked app opens, how it ended, and your 1–5★ rating.
*Estimated sleep* = time in sleep mode − screen-on time − 15 min to fall asleep.
History screen shows a 7-night chart, averages, and how often you kept the full night.

**Always allowed:** default dialer + in-call screens (incoming calls always work), emergency
dialer, clock apps (so alarms can ring and be dismissed), keyboards, the launcher, system UI.

## Build & install

1. Open this folder in **Android Studio** (Ladybug or newer). Let Gradle sync.
2. Plug in your phone with USB debugging on → press **Run ▶**.
   Or: **Build → Build APK(s)** and copy `app/build/outputs/apk/debug/app-debug.apk` to the phone.
3. Command line alternative: `./gradlew assembleDebug` (needs the Android SDK; set `sdk.dir` in `local.properties`).

## First-run setup (on the phone)

The *Tonight* tab shows a checklist. All four matter:

1. **App blocking (Accessibility)** – Settings → Accessibility → SleepGuard → On.
   **On Android 13+ with a sideloaded APK the switch is greyed out.** Fix: App info → ⋮ (top-right)
   → **Allow restricted settings**, then enable it again.
2. **Notifications** – status + morning summary.
3. **Exact alarms** – so bedtime starts on the minute.
4. **Unrestricted battery** – otherwise the system may kill it at night.

**Xiaomi / Oppo / Vivo / Realme / OnePlus:** also turn on *Autostart* for SleepGuard and lock it in
recents, or the OS will kill background services regardless of the battery setting.

Then go to *Schedule*, set bedtime/wake-up and days, pick Basic or Strict, and switch the schedule on.
"Start sleep now" on the Tonight tab runs a session until the next wake-up time.

## How it works

```
AlarmManager (exact) ──► AlarmReceiver ──► SessionManager.start()
                                              │  writes session state to SharedPreferences
                                              ├─► SleepService (foreground: notification + watchdog)
                                              └─► ScreenTracker (screen on/off → tracking)
BlockerService (AccessibilityService)
   listens to window changes ──► not allowed? ──► HOME + BlockActivity ("Time to sleep")
BootReceiver ──► resumes a running session after reboot / re-arms alarms
AlarmReceiver (wake time) ──► SessionManager.end() ──► SessionStore (JSON history) + morning notification
```

| File | Role |
|---|---|
| `data/SleepPrefs.kt` | Settings + live session state |
| `data/SessionStore.kt` | Night history (JSON in app storage) |
| `data/ScheduleCalc.kt` | Bedtime/wake window math (handles past-midnight, days of week) |
| `core/SessionManager.kt` | Start/stop sessions, save history |
| `core/SleepScheduler.kt` | Exact alarms for start/end |
| `core/AppRules.kt` | Always-allowed and strict-blocked packages |
| `service/BlockerService.kt` | The actual app blocking |
| `service/SleepService.kt` | Foreground service + end-of-night watchdog |
| `ui/*` | Tonight, Schedule, History screens, block screen, app picker |

## Honest limitations

- **No app can be 100% unescapable** on a normal (non-managed) phone. Strict mode closes the usual
  holes (Settings, uninstall, reboot), but someone determined can still use the accessibility
  shortcut (if they set one up), Safe Mode, or ADB. It's built to stop habits, not to be a jail.
- **Google Play:** Play restricts AccessibilityService use to accessibility purposes and
  requires a declaration form + prominent disclosure for anything else. For a Play release, the
  common alternative is `UsageStatsManager` polling + an overlay (`SYSTEM_ALERT_WINDOW`). Sideloading
  this build is fine.
- Sleep is *estimated* from phone usage, not measured with sensors. Adding Google's Sleep API
  (`ActivityRecognition`, Play Services) or Health Connect would give real sleep stages.
- Some OEM in-call or clock apps use unusual package names. If something you need gets blocked,
  add it under *Schedule → Allowed during sleep*, or add the package to `AppRules.CALL_PACKAGES`.

## Ideas for next steps

- Wind-down reminder 30 min before bedtime
- Grayscale / Do Not Disturb toggle at bedtime (`NotificationManager` policy access)
- Weekly report and streaks
- Health Connect export

---

# SleepGuard for Windows PC (`desktop/`)

C# · .NET 10 · WinForms · single self-contained `SleepGuard-PC.exe` (Windows 10/11 x64, no install, no admin).

| | Basic mode | Strict mode |
|---|---|---|
| Full-screen "Time to sleep" screen, other apps minimized | ✅ | ✅ |
| Phone Link (calls), Clock and your allowed apps keep working | ✅ | ✅ |
| "Back to desktop" / use an app for 5 min (3× a night) | ✅ | ❌ |
| End early / exit SleepGuard | ✅ | ❌ (emergency code only) |
| Task Manager, Settings, Registry Editor, command prompts | usable | blocked |
| Restarting the PC resumes sleep mode | ✅ | ✅ |

Also: a reminder 15 minutes before bedtime, an optional "put the PC to sleep after N idle minutes", and a
History tab with a 7-night chart and 1–5★ ratings. Night tracking uses keyboard/mouse activity:
PC use during sleep time, wake-ups (back on the PC after 5+ idle minutes) and blocked app opens.

Data lives in `%APPDATA%\SleepGuard` (settings, current night, history as JSON).

**Build:** `dotnet publish desktop/SleepGuard.Desktop -c Release -r win-x64 --self-contained -p:PublishSingleFile=true`
(or let GitHub Actions do it — every push to `main` publishes both the APK and the EXE as a release).

**Limits:** the PC blocker works at the window level without admin rights, so a determined person could still
sign out, use another Windows account, or boot into Safe Mode. Strict mode disables Task Manager through a
per-user policy and turns it back on at wake-up (or on the next start if SleepGuard was closed abnormally).
