package com.dayflow.planner.remind

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dayflow.planner.data.TaskEntity
import com.dayflow.planner.logic.Dates
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns tasks that have a due date + time into one-shot WorkManager jobs that
 * fire a system notification at the due moment. WorkManager keeps the jobs
 * across reboots, and [ResyncWorker] rebuilds them from the database after
 * boot or an app update.
 */
object ReminderScheduler {

    private const val NAME_PREFIX = "dayflow-task-"

    private fun workName(taskId: Long) = NAME_PREFIX + taskId

    fun schedule(context: Context, task: TaskEntity) {
        val wm = WorkManager.getInstance(context)
        val day = task.dueDay ?: return
        val time = task.dueTime ?: return
        if (task.completedAt != null || task.isRecurring) {
            wm.cancelUniqueWork(workName(task.id))
            return
        }
        val dueMillis = LocalDate.ofEpochDay(day)
            .atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(time.toLong())
            .toInstant()
            .toEpochMilli()
        val delay = dueMillis - System.currentTimeMillis()
        if (delay < 0) {
            // already in the past: don't nag; user can re-date the task.
            wm.cancelUniqueWork(workName(task.id))
            return
        }
        val input = Data.Builder()
            .putLong(ReminderWorker.KEY_TASK_ID, task.id)
            .putString(ReminderWorker.KEY_TITLE, task.title)
            .putLong(ReminderWorker.KEY_DAY, day)
            .build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(input)
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        wm.enqueueUniqueWork(workName(task.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
    }

    /**
     * Called on cold start, boot and app update: rebuilds all pending
     * reminders so cancelled/expired jobs stay consistent with the database.
     */
    fun resyncAll(context: Context) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<ResyncWorker>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("dayflow-resync", ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** Rebuilds reminders after the user toggles the master switch. */
    fun cancelAll(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(TAG) }
    }

    const val TAG = "dayflow-reminders"

    fun enabledForToday(task: TaskEntity, today: LocalDate): Boolean {
        val day = task.dueDay ?: return false
        return day == Dates.toEpochDay(today)
    }
}
