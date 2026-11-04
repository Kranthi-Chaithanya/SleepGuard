package com.sleepguard.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Device admin with no policies — its only job is to make uninstalling a two-step process. */
class SleepAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off removes SleepGuard's uninstall protection."
}
