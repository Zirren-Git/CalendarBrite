package com.dayflow.planner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** App-wide notification plumbing. */
object Notifications {
    const val CHANNEL_REMINDERS = "dayflow.reminders"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminders for tasks that are due"
            enableLights(true)
        }
        nm.createNotificationChannel(channel)
    }
}
