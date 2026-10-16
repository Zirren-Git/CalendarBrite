package com.dayflow.planner.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms task reminders after a device reboot or an app update, since
 * pending WorkManager one-time jobs can be dropped by the system.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ReminderScheduler.resyncAll(context)
        }
    }
}
