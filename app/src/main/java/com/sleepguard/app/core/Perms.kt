package com.sleepguard.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.sleepguard.app.receiver.SleepAdminReceiver
import com.sleepguard.app.service.BlockerService

data class PermissionState(
    val accessibility: Boolean,
    val notifications: Boolean,
    val exactAlarm: Boolean,
    val battery: Boolean,
) {
    val allGood: Boolean get() = accessibility && notifications && exactAlarm && battery

    companion object {
        fun read(ctx: Context) = PermissionState(
            accessibility = Perms.accessibilityEnabled(ctx),
            notifications = Perms.notificationsGranted(ctx),
            exactAlarm = SleepScheduler.canScheduleExact(ctx),
            battery = Perms.batteryUnrestricted(ctx),
        )
    }
}

object Perms {

    fun accessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(ctx, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun notificationsGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun openAccessibilitySettings(ctx: Context) =
        safeStart(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppInfo(ctx: Context) = safeStart(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
    )

    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            safeStart(
                ctx,
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")),
            )
        }
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryUnrestricted(ctx: Context) {
        val ok = safeStart(
            ctx,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        )
        if (!ok) safeStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    // ---- Device admin (uninstall protection) ----

    private fun adminComponent(ctx: Context) = ComponentName(ctx, SleepAdminReceiver::class.java)

    fun adminActive(ctx: Context): Boolean =
        ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(ctx))

    fun requestAdmin(ctx: Context) = safeStart(
        ctx,
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(ctx))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "While this is on, SleepGuard can't be uninstalled without first turning it off in Settings — which strict mode blocks at night.",
            ),
    )

    fun removeAdmin(ctx: Context) {
        ctx.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(adminComponent(ctx))
    }

    private fun safeStart(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
