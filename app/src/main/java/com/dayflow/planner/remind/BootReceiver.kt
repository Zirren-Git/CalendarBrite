package com.dayflow.planner.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms reminders after device boot / app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.resyncAll(context)
    }
}
