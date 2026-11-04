package com.sleepguard.app.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sleepguard.app.core.Perms
import com.sleepguard.app.core.SleepScheduler
import com.sleepguard.app.data.BlockMode
import com.sleepguard.app.data.ScheduleCalc
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.util.AppInfoUtil
import com.sleepguard.app.util.formatClock
import com.sleepguard.app.util.formatDuration
import com.sleepguard.app.util.formatTime
import java.util.Calendar

private val DAY_ORDER = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
)
private val DAY_LETTERS = mapOf(
    Calendar.MONDAY to "M", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "W",
    Calendar.THURSDAY to "T", Calendar.FRIDAY to "F", Calendar.SATURDAY to "S", Calendar.SUNDAY to "S",
)

@Composable
fun ScheduleScreen() {
    val ctx = LocalContext.current
    val prefs = remember { SleepPrefs(ctx) }
    rememberPrefsVersion(prefs)

    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val adminActive = remember(resumeTick) { Perms.adminActive(ctx) }

    var showPicker by remember { mutableStateOf(false) }
    var confirmStrict by remember { mutableStateOf(false) }

    val locked = prefs.isLockedNow
    val enabled = prefs.scheduleEnabled
    val bedH = prefs.bedHour
    val bedM = prefs.bedMinute
    val wakeH = prefs.wakeHour
    val wakeM = prefs.wakeMinute
    val days = prefs.days
    val mode = prefs.mode
    val allowed = prefs.allowedPackages.sorted()
    val durationMs = ScheduleCalc.durationMinutes(prefs) * 60_000L
    val sessionEndLabel = formatTime(ctx, prefs.sessionEnd)
    val sessionActive = prefs.sessionActive

    fun changed() = SleepScheduler.reschedule(ctx)

    fun pickTime(h: Int, m: Int, onPicked: (Int, Int) -> Unit) {
        TimePickerDialog(ctx, { _, hh, mm -> onPicked(hh, mm) }, h, m, DateFormat.is24HourFormat(ctx)).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Schedule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (locked) {
            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Strict sleep mode is on. Settings unlock at $sessionEndLabel.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        } else if (sessionActive) {
            Text(
                "Changes apply from the next night.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(Modifier.alpha(if (locked) 0.6f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sleep schedule", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) "Turns on automatically every selected night" else "Off — only manual start",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = !locked,
                    onCheckedChange = { prefs.scheduleEnabled = it; changed() },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TimeRow("Bedtime", formatClock(ctx, bedH, bedM), enabled = !locked) {
                pickTime(bedH, bedM) { h, m -> prefs.bedHour = h; prefs.bedMinute = m; changed() }
            }
            TimeRow("Wake up", formatClock(ctx, wakeH, wakeM), enabled = !locked) {
                pickTime(wakeH, wakeM) { h, m -> prefs.wakeHour = h; prefs.wakeMinute = m; changed() }
            }
            Text(
                "${formatDuration(durationMs)} of sleep time",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Repeat on", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DAY_ORDER.forEach { d ->
                    DayToggle(DAY_LETTERS.getValue(d), selected = d in days, enabled = !locked) {
                        prefs.days = if (d in days) days - d else days + d
                        changed()
                    }
                }
            }
        }

        SectionTitle("Blocking mode")
        ModeOption(
            title = "Basic",
            desc = "Blocks apps, but stays forgiving: allow an app for 5 minutes (3× a night) or end sleep early. System settings stay reachable.",
            selected = mode == BlockMode.BASIC,
            enabled = !locked,
            onClick = { prefs.mode = BlockMode.BASIC },
        )
        ModeOption(
            title = "Strict",
            desc = "No snoozes and no ending early. Settings and the uninstaller are blocked too, and rebooting doesn't escape it. Only a long emergency code unlocks.",
            selected = mode == BlockMode.STRICT,
            enabled = !locked,
            onClick = { if (mode != BlockMode.STRICT) confirmStrict = true },
        )

        if (mode == BlockMode.STRICT) {
            SectionCard(Modifier.alpha(if (locked) 0.6f else 1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Uninstall protection", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Makes SleepGuard a device admin, so it must be turned off in Settings before uninstalling — which strict mode blocks at night.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = adminActive,
                        enabled = !locked,
                        onCheckedChange = {
                            if (it) Perms.requestAdmin(ctx) else Perms.removeAdmin(ctx)
                            resumeTick++
                        },
                    )
                }
            }
        }

        SectionTitle("Allowed during sleep")
        SectionCard {
            Text(
                "Always allowed: Phone & calls, Clock & alarms, Keyboard, Home screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            allowed.forEach { pkg -> AllowedAppRow(pkg, removable = !locked) { prefs.allowedPackages = prefs.allowedPackages - pkg } }
            TextButton(onClick = { showPicker = true }, enabled = !locked) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add app")
            }
        }
        Spacer(Modifier.size(12.dp))
    }

    if (showPicker) {
        AppPickerDialog(
            current = prefs.allowedPackages,
            onDismiss = { showPicker = false },
            onConfirm = {
                prefs.allowedPackages = it
                showPicker = false
            },
        )
    }

    if (confirmStrict) {
        AlertDialog(
            onDismissRequest = { confirmStrict = false },
            title = { Text("Switch to strict mode?") },
            text = {
                Text(
                    "From the next sleep session on, you won't be able to end sleep mode early, snooze apps, open Settings or uninstall the app until wake-up. Calls and alarms keep working."
                )
            },
            confirmButton = {
                TextButton(onClick = { prefs.mode = BlockMode.STRICT; confirmStrict = false }) { Text("Use strict") }
            },
            dismissButton = { TextButton(onClick = { confirmStrict = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TimeRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DayToggle(letter: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ModeOption(title: String, desc: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.padding(top = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(4.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AllowedAppRow(pkg: String, removable: Boolean, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    val label = remember(pkg) { AppInfoUtil.label(ctx, pkg) }
    val icon = remember(pkg) { AppInfoUtil.icon(ctx, pkg) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        } else {
            Spacer(Modifier.size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        if (removable) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
        }
    }
}
