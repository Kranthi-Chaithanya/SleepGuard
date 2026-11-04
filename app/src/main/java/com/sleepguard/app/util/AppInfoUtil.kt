package com.sleepguard.app.util

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.sleepguard.app.core.AppRules

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

object AppInfoUtil {

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    fun icon(ctx: Context, pkg: String, sizePx: Int = 96): ImageBitmap? = try {
        ctx.packageManager.getApplicationIcon(pkg).toBitmap(sizePx, sizePx).asImageBitmap()
    } catch (e: Exception) {
        null
    }

    /** Apps the user may add to the allow-list (excludes ones that are always allowed). */
    @Suppress("DEPRECATION")
    fun launchableApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val excluded = AppRules.systemAllowed(ctx) + AppRules.STRICT_BLOCKED + ctx.packageName
        return pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .filter { it !in excluded }
            .map { AppEntry(it, label(ctx, it), icon(ctx, it)) }
            .sortedBy { it.label.lowercase() }
    }
}
