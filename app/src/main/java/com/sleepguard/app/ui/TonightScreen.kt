package com.sleepguard.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sleepguard.app.core.PermissionState
import com.sleepguard.app.core.Perms
import com.sleepguard.app.core.SessionManager
import com.sleepguard.app.data.BlockMode
import com.sleepguard.app.data.ScheduleCalc
import com.sleepguard.app.data.SessionStore
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.data.SleepSession
import com.sleepguard.app.util.formatDay
import com.sleepguard.app.util.formatDuration
import com.sleepguard.app.util.formatTime
import com.sleepguard.app.util.relativeDay
import java.util.Calendar

@Composable
fun TonightScreen(onOpenSchedule: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { SleepPrefs(ctx) }
    val version = rememberPrefsVersion(prefs)
    val now = rememberNow()

    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val perms = remember(resumeTick, version) { PermissionState.read(ctx) }
    val lastSession = remember(version) { SessionStore.all(ctx).firstOrNull() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick++
    }

    var confirmStrictStart by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var showEmergency by remember { mutableStateOf(false) }

    // Read everything here and pass plain values down (keeps children recomposing correctly).
    val active = prefs.sessionActive
    val sessionStrict = prefs.sessionMode == BlockMode.STRICT
    val sessionEnd = prefs.sessionEnd
    val wakes = prefs.screenOnCount
    val blocked = prefs.blockedAttempts
    val nextWindow = ScheduleCalc.nextWindow(now, prefs)
    val nextMode = prefs.mode
    val manualEnd = ScheduleCalc.nextWakeAfter(now, prefs)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonGraphic(Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("SleepGuard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(greeting(now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (active) {
            ActiveCard(
                strict = sessionStrict,
                endLabel = formatTime(ctx, sessionEnd),
                remaining = formatDuration(sessionEnd - now),
                wakes = wakes,
                blocked = blocked,
                blockingOn = perms.accessibility,
                onEnd = { confirmEnd = true },
                onEmergency = { showEmergency = true },
            )
        } else {
            IdleCard(
                nextBed = nextWindow?.let { "${relativeDay(it.first, now)} · ${formatTime(ctx, it.first)}" },
                nextRange = nextWindow?.let { "until ${formatTime(ctx, it.second)} (${formatDuration(it.second - it.first)})" },
                strict = nextMode == BlockMode.STRICT,
                manualEndLabel = formatTime(ctx, manualEnd),
                onStart = {
                    if (nextMode == BlockMode.STRICT) confirmStrictStart = true else SessionManager.startNow(ctx)
                },
                onOpenSchedule = onOpenSchedule,
            )
        }

        SetupCard(
            perms = perms,
            onAccessibility = { Perms.openAccessibilitySettings(ctx) },
            onAppInfo = { Perms.openAppInfo(ctx) },
            onNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onExact = { Perms.openExactAlarmSettings(ctx) },
            onBattery = { Perms.requestBatteryUnrestricted(ctx) },
        )

        lastSession?.let { LastNightCard(it) }
    }

    if (confirmStrictStart) {
        AlertDialog(
            onDismissRequest = { confirmStrictStart = false },
            title = { Text("Start strict sleep mode?") },
            text = {
                Text(
                    "Until ${formatTime(ctx, manualEnd)}, every app except calls, clock and your allowed apps " +
                        "will be blocked. Settings are blocked too, and you can't end it early — only an emergency code unlocks it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStrictStart = false
                    SessionManager.startNow(ctx)
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { confirmStrictStart = false }) { Text("Cancel") } },
        )
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End sleep mode early?") },
            text = { Text("Apps will be unblocked and tonight will be saved as ended early.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    SessionManager.end(ctx, SleepSession.REASON_ENDED_EARLY)
                }) { Text("End now") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep sleeping") } },
        )
    }

    if (showEmergency) {
        EmergencyUnlockDialog(
            onDismiss = { showEmergency = false },
            onUnlock = {
                showEmergency = false
                SessionManager.end(ctx, SleepSession.REASON_EMERGENCY)
            },
        )
    }
}

private fun greeting(now: Long): String {
    val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "It's getting late"
    }
}

@Composable
private fun ActiveCard(
    strict: Boolean,
    endLabel: String,
    remaining: String,
    wakes: Int,
    blocked: Int,
    blockingOn: Boolean,
    onEnd: () -> Unit,
    onEmergency: () -> Unit,
) {
    SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (strict) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
            }
            Text("Sleep mode is on", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            "Until $endLabel · $remaining left",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (strict) "Strict" else "Basic")
            Pill("Screen wakes: $wakes")
            Pill("Blocked: $blocked")
        }
        if (!blockingOn) {
            Text(
                "App blocking is off — enable it below so apps actually get blocked.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (strict) {
            Text(
                "Strict mode can't be ended early.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(onClick = onEmergency) { Text("Emergency unlock") }
        } else {
            OutlinedButton(onClick = onEnd) { Text("End sleep mode") }
        }
    }
}

@Composable
private fun IdleCard(
    nextBed: String?,
    nextRange: String?,
    strict: Boolean,
    manualEndLabel: String,
    onStart: () -> Unit,
    onOpenSchedule: () -> Unit,
) {
    SectionCard {
        if (nextBed != null && nextRange != null) {
            Text("Next bedtime", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(nextBed, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(nextRange, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Pill(if (strict) "Strict mode" else "Basic mode")
        } else {
            Text("No schedule yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Set a bedtime and wake-up time and SleepGuard turns on by itself every night.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onOpenSchedule) { Text("Set schedule") }
        }
        Spacer(Modifier.size(4.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start sleep now · until $manualEndLabel")
        }
    }
}

@Composable
private fun SetupCard(
    perms: PermissionState,
    onAccessibility: () -> Unit,
    onAppInfo: () -> Unit,
    onNotifications: () -> Unit,
    onExact: () -> Unit,
    onBattery: () -> Unit,
) {
    SectionCard {
        if (perms.allGood) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(10.dp))
                Text("Everything is set up", style = MaterialTheme.typography.titleMedium)
            }
            return@SectionCard
        }
        Text("Finish setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SetupRow(
            title = "App blocking",
            desc = "Accessibility service that sends other apps home during sleep. Required.",
            ok = perms.accessibility,
            action = "Enable",
            onClick = onAccessibility,
        )
        if (!perms.accessibility) {
            Text(
                "Switch greyed out? Open App info → ⋮ menu → “Allow restricted settings”, then come back and enable it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAppInfo) { Text("Open App info") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SetupRow("Notifications", "Sleep status and your morning summary.", perms.notifications, "Allow", onNotifications)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SetupRow("Exact alarms", "Starts and ends sleep mode right on time.", perms.exactAlarm, "Allow", onExact)
        }
        SetupRow("Unrestricted battery", "Stops the system from killing sleep mode overnight.", perms.battery, "Allow", onBattery)
    }
}

@Composable
private fun SetupRow(title: String, desc: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun LastNightCard(s: SleepSession) {
    val ctx = LocalContext.current
    SectionCard {
        Text("Last night · ${formatDay(s.start)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(s.estimatedSleepMillis), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("estimated sleep", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${formatTime(ctx, s.start)} → ${formatTime(ctx, s.end)} · ${s.screenOnCount} screen wakes · ${s.blockedAttempts} blocked",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
