package com.sleepguard.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.sleepguard.app.core.Notifications
import com.sleepguard.app.core.SleepScheduler
import com.sleepguard.app.ui.HistoryScreen
import com.sleepguard.app.ui.ScheduleScreen
import com.sleepguard.app.ui.SleepTheme
import com.sleepguard.app.ui.TonightScreen

class MainActivity : ComponentActivity() {

    private var requestedTab by mutableIntStateOf(TAB_TONIGHT)
    private var navNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        Notifications.ensureChannels(this)
        SleepScheduler.reschedule(this)
        handleIntent(intent)
        setContent {
            SleepTheme { MainScreen(requestedTab, navNonce) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val tab = intent?.getIntExtra(EXTRA_TAB, -1) ?: -1
        if (tab >= 0) {
            requestedTab = tab
            navNonce++
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_TONIGHT = 0
        const val TAB_SCHEDULE = 1
        const val TAB_HISTORY = 2
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Tonight", Icons.Filled.Home),
    Tab("Schedule", Icons.Filled.DateRange),
    Tab("History", Icons.AutoMirrored.Filled.List),
)

@Composable
private fun MainScreen(requestedTab: Int, navNonce: Int) {
    var tab by rememberSaveable { mutableIntStateOf(MainActivity.TAB_TONIGHT) }
    LaunchedEffect(navNonce) { if (navNonce > 0) tab = requestedTab }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                MainActivity.TAB_TONIGHT -> TonightScreen(onOpenSchedule = { tab = MainActivity.TAB_SCHEDULE })
                MainActivity.TAB_SCHEDULE -> ScheduleScreen()
                else -> HistoryScreen()
            }
        }
    }
}
