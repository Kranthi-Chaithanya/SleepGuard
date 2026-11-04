package com.sleepguard.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepguard.app.core.SessionManager
import com.sleepguard.app.data.BlockMode
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.data.SleepSession
import com.sleepguard.app.util.AppInfoUtil
import com.sleepguard.app.util.formatDuration
import com.sleepguard.app.util.formatTime

@Composable
fun BlockScreen(
    blockedPkg: String?,
    onGoHome: () -> Unit,
    onLaunchApp: (Intent) -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SleepPrefs(ctx) }
    rememberPrefsVersion(prefs)
    val now = rememberNow(15_000L)

    val active = prefs.sessionActive
    LaunchedEffect(active) { if (!active) onGoHome() }

    val strict = prefs.sessionMode == BlockMode.STRICT
    val end = prefs.sessionEnd
    val snoozesLeft = (SleepPrefs.MAX_SNOOZES - prefs.snoozesUsed).coerceAtLeast(0)
    val appLabel = remember(blockedPkg) { blockedPkg?.let { AppInfoUtil.label(ctx, it) } }
    val launchIntent = remember(blockedPkg) { blockedPkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) } }

    var confirmEnd by remember { mutableStateOf(false) }
    var showEmergency by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF05060F), Color(0xFF14174A), Color(0xFF2A2265)))
            )
            .systemBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoonGraphic(Modifier.size(110.dp))
            Spacer(Modifier.height(8.dp))
            Text("Time to sleep", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "${appLabel ?: "This app"} is blocked until ${formatTime(ctx, end)}.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatDuration(end - now)} left · ${if (strict) "Strict" else "Basic"} mode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) { /* no dialer */ }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Call, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Phone")
            }

            if (!strict) {
                if (launchIntent != null && appLabel != null && snoozesLeft > 0) {
                    OutlinedButton(
                        onClick = {
                            prefs.snoozesUsed = prefs.snoozesUsed + 1
                            prefs.tempAllowPackage = blockedPkg
                            prefs.tempAllowUntil = System.currentTimeMillis() + SleepPrefs.SNOOZE_MS
                            onLaunchApp(launchIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use $appLabel for 5 min ($snoozesLeft left)")
                    }
                }
                TextButton(onClick = { confirmEnd = true }) { Text("End sleep mode") }
            } else {
                Text(
                    "Strict mode can't be turned off until morning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showEmergency = true }) { Text("Emergency unlock") }
            }
            TextButton(onClick = onGoHome) { Text("Back to home screen") }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End sleep mode early?") },
            text = { Text("All apps will be unblocked. Tonight will be saved as ended early.") },
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
