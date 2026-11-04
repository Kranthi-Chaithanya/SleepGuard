package com.sleepguard.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepguard.app.data.BlockMode
import com.sleepguard.app.data.ScheduleCalc
import com.sleepguard.app.data.SessionStore
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.data.SleepSession
import com.sleepguard.app.util.dayLetter
import com.sleepguard.app.util.formatDay
import com.sleepguard.app.util.formatDuration
import com.sleepguard.app.util.formatTime
import kotlin.math.ceil

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val prefs = remember { SleepPrefs(ctx) }
    val version = rememberPrefsVersion(prefs)
    var refresh by remember { mutableIntStateOf(0) }
    val sessions = remember(version, refresh) { SessionStore.all(ctx) }
    val goalMinutes = ScheduleCalc.durationMinutes(prefs)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

        if (sessions.isEmpty()) {
            item {
                SectionCard {
                    Text("No nights tracked yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Each sleep session is saved here with an estimated sleep time, how often you woke your phone, and how many app opens were blocked.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        val recent = sessions.take(7)
        val last30 = sessions.take(30)
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Avg sleep", formatDuration(recent.map { it.estimatedSleepMillis }.average().toLong()), Modifier.weight(1f))
                StatTile("Avg wakes", "%.1f".format(recent.map { it.screenOnCount }.average()), Modifier.weight(1f))
                StatTile(
                    "Kept",
                    "${(last30.count { it.endReason == SleepSession.REASON_COMPLETED } * 100 / last30.size)}%",
                    Modifier.weight(1f),
                )
            }
        }
        item {
            SectionCard {
                Text("Last 7 nights", style = MaterialTheme.typography.titleMedium)
                SleepChart(recent.reversed(), goalMinutes)
            }
        }
        items(sessions, key = { it.start }) { s ->
            SessionCard(s, onRate = { r ->
                SessionStore.setRating(ctx, s.start, r)
                refresh++
            })
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SleepChart(nights: List<SleepSession>, goalMinutes: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val goalColor = MaterialTheme.colorScheme.secondary
    val slots = 7
    val maxHours = ceil(
        maxOf(goalMinutes / 60f, nights.maxOfOrNull { it.estimatedSleepMillis / 3_600_000f } ?: 0f, 8f) + 1f
    )
    val padStart = slots - nights.size

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val slot = size.width / slots
            val barW = slot * 0.46f
            val radius = CornerRadius(barW / 2f, barW / 2f)
            for (i in 0 until slots) {
                val x = slot * i + (slot - barW) / 2f
                drawRoundRect(track, topLeft = Offset(x, 0f), size = Size(barW, size.height), cornerRadius = radius)
                val s = nights.getOrNull(i - padStart) ?: continue
                val h = (s.estimatedSleepMillis / 3_600_000f / maxHours).coerceIn(0f, 1f) * size.height
                if (h > 0f) {
                    drawRoundRect(
                        primary,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = radius,
                    )
                }
            }
            val gy = size.height - (goalMinutes / 60f / maxHours) * size.height
            drawLine(
                goalColor,
                start = Offset(0f, gy),
                end = Offset(size.width, gy),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for (i in 0 until slots) {
                val s = nights.getOrNull(i - padStart)
                Text(
                    s?.let { dayLetter(it.start) } ?: "",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            "Bars: estimated sleep · Dashed line: scheduled ${formatDuration(goalMinutes * 60_000L)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionCard(s: SleepSession, onRate: (Int) -> Unit) {
    val ctx = LocalContext.current
    val (statusText, statusColor) = when (s.endReason) {
        SleepSession.REASON_COMPLETED -> "Completed" to MaterialTheme.colorScheme.tertiary
        SleepSession.REASON_EMERGENCY -> "Emergency unlock" to MaterialTheme.colorScheme.error
        else -> "Ended early" to MaterialTheme.colorScheme.secondary
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDay(s.start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatTime(ctx, s.start)} → ${formatTime(ctx, s.end)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDuration(s.estimatedSleepMillis), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("est. sleep", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "In bed ${formatDuration(s.durationMillis)} · screen on ${formatDuration(s.screenOnMillis)} (${s.screenOnCount}×) · ${s.blockedAttempts} blocked",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(statusText, textColor = statusColor)
            Pill(if (s.mode == BlockMode.STRICT) "Strict" else "Basic")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (s.rating == 0) "How did you sleep?" else "Your rating",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            for (star in 1..5) {
                IconButton(onClick = { onRate(star) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Rate $star",
                        tint = if (star <= s.rating) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
