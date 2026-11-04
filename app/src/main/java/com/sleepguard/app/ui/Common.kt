package com.sleepguard.app.ui

import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepguard.app.data.SleepPrefs
import kotlinx.coroutines.delay

/** Returns a number that changes whenever any preference changes, forcing recomposition. */
@Composable
fun rememberPrefsVersion(prefs: SleepPrefs): Int {
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        prefs.register(l)
        onDispose { prefs.unregister(l) }
    }
    return version
}

/** Current time, refreshed every [periodMs]. */
@Composable
fun rememberNow(periodMs: Long = 30_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 6.dp, start = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.surfaceVariant, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
    )
}

/** Crescent moon drawn with a path difference (works on any background). */
@Composable
fun MoonGraphic(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondary) {
    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.9f
        val c = center
        val outer = Path().apply { addOval(Rect(c, r)) }
        val bite = Path().apply { addOval(Rect(Offset(c.x + r * 0.5f, c.y - r * 0.4f), r * 0.85f)) }
        val moon = Path().apply { op(outer, bite, PathOperation.Difference) }
        drawPath(moon, color)
    }
}
