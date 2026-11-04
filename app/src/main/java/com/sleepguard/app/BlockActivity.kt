package com.sleepguard.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sleepguard.app.data.SleepPrefs
import com.sleepguard.app.ui.BlockScreen
import com.sleepguard.app.ui.SleepTheme

/** The "Time to sleep" screen shown over a blocked app. */
class BlockActivity : ComponentActivity() {

    private var blockedPkg by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        blockedPkg = intent.getStringExtra(EXTRA_PKG)
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            SleepTheme {
                BlockScreen(
                    blockedPkg = blockedPkg,
                    onGoHome = { goHome() },
                    onLaunchApp = { launch ->
                        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedPkg = intent.getStringExtra(EXTRA_PKG)
    }

    override fun onResume() {
        super.onResume()
        if (!SleepPrefs(this).sessionActive) finish()
    }

    private fun goHome() {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // ignore
        }
        finish()
    }

    companion object {
        const val EXTRA_PKG = "blocked_pkg"
    }
}
