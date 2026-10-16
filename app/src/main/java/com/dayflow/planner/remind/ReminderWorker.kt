package com.dayflow.planner.remind

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dayflow.planner.MainActivity
import com.dayflow.planner.Notifications
import com.dayflow.planner.R

/** Fires the "task due" notification when its scheduled time arrives. */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val day = inputData.getLong(KEY_DAY, -1L)

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return Result.success()

        val tap = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_OPEN_DAY, day)
            putExtra(MainActivity.EXTRA_TASK_ID, taskId)
        }
        val pi = PendingIntent.getActivity(
            applicationContext, taskId.toInt(), tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(applicationContext, Notifications.CHANNEL_REMINDERS)
            .setContentTitle("Time for: $title")
            .setContentText("Tap to open DayFlow Planner")
            .setSmallIcon(R.drawable.ic_stat_dayflow)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
        nm.notify(NOTIF_ID_BASE + taskId.toInt(), builder.build())
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
        const val KEY_TITLE = "title"
        const val KEY_DAY = "day"
        private const val NOTIF_ID_BASE = 4000
    }
}

/** Rebuilds every pending reminder from the database (boot / update / resync). */
class ResyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? com.dayflow.planner.DayFlowApp ?: return Result.success()
        val repo = app.container.repository
        val tasks = repo.upcomingTimedTasks(java.time.LocalDate.now().toEpochDay())
        tasks.forEach { ReminderScheduler.schedule(applicationContext, it) }
        return Result.success()
    }
}
