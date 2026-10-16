package com.dayflow.planner.data

import com.dayflow.planner.logic.Dates
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Snapshot used for Undo of a task deletion. */
data class TaskSnapshot(
    val task: TaskEntity,
    val subtasks: List<SubtaskEntity>,
)

/**
 * Single repository façade over the Room DAOs (MVVM "data" layer).
 * View models never touch DAOs directly.
 */
class Repository(private val db: DayFlowDatabase) {

    private val nowMillis: Long get() = System.currentTimeMillis()

    // ---- Tasks ---------------------------------------------------------------

    fun observeTasks(): Flow<List<TaskEntity>> = db.taskDao().observeAll()
    fun observeSubtasks(): Flow<List<SubtaskEntity>> = db.subtaskDao().observeAll()
    fun observeCompletionsSince(fromDay: Long): Flow<List<TaskCompletionEntity>> =
        db.taskDao().observeCompletionsSince(fromDay)

    suspend fun loadTask(id: Long): TaskEntity? = db.taskDao().byId(id)
    suspend fun loadSubtasks(taskId: Long): List<SubtaskEntity> = db.subtaskDao().forTask(taskId)

    suspend fun saveTask(task: TaskEntity): Long {
        val stamp = nowMillis
        return if (task.id == 0L) {
            db.taskDao().insert(
                task.copy(
                    createdAt = stamp,
                    updatedAt = stamp,
                    anchorDay = if (task.anchorDay == 0L) Dates.toEpochDay(LocalDate.now()) else task.anchorDay,
                ),
            )
        } else {
            db.taskDao().update(task.copy(updatedAt = stamp))
            task.id
        }
    }

    suspend fun setTaskCompleted(task: TaskEntity, done: Boolean) {
        db.taskDao().setCompleted(task.id, if (done) nowMillis else null)
    }

    suspend fun setOccurrenceCompleted(taskId: Long, day: LocalDate, done: Boolean) {
        if (done) {
            db.taskDao().addCompletion(TaskCompletionEntity(taskId, Dates.toEpochDay(day), nowMillis))
        } else {
            db.taskDao().removeCompletion(taskId, Dates.toEpochDay(day))
        }
    }

    suspend fun moveTaskToDay(taskId: Long, day: LocalDate?) {
        db.taskDao().moveToDay(taskId, day?.let(Dates::toEpochDay), nowMillis)
    }

    suspend fun snapshotTask(taskId: Long): TaskSnapshot? {
        val task = db.taskDao().byId(taskId) ?: return null
        return TaskSnapshot(task = task, subtasks = db.subtaskDao().forTask(taskId))
    }

    suspend fun deleteTask(taskId: Long) {
        db.taskDao().deleteById(taskId) // subtasks + completions cascade
    }

    suspend fun restoreTask(snapshot: TaskSnapshot) {
        val newId = db.taskDao().insert(snapshot.task)
        val parent = if (newId != 0L) newId else snapshot.task.id
        db.subtaskDao().insertAll(snapshot.subtasks.map { it.copy(taskId = parent) })
    }

    suspend fun duplicateTask(taskId: Long): Long? {
        val src = db.taskDao().byId(taskId) ?: return null
        val copy = src.copy(
            id = 0,
            title = src.title + " (copy)",
            completedAt = null,
            createdAt = nowMillis,
            updatedAt = nowMillis,
        )
        val newId = db.taskDao().insert(copy)
        db.subtaskDao().insertAll(db.subtaskDao().forTask(taskId).map { it.copy(id = 0, taskId = newId) })
        return newId
    }

    // Subtasks ----------------------------------------------------------------
    suspend fun addSubtask(taskId: Long, title: String, position: Int): Long =
        db.subtaskDao().insert(SubtaskEntity(taskId = taskId, title = title, position = position))

    suspend fun renameSubtask(subtask: SubtaskEntity, title: String) =
        db.subtaskDao().update(subtask.copy(title = title))

    suspend fun setSubtaskDone(id: Long, done: Boolean) = db.subtaskDao().setDone(id, done)
    suspend fun deleteSubtask(id: Long) = db.subtaskDao().deleteById(id)

    /** Replace the full checklist of a task (editor save path). */
    suspend fun replaceSubtasks(taskId: Long, rows: List<SubtaskEntity>) {
        db.subtaskDao().clearForTask(taskId)
        if (rows.isNotEmpty()) {
            db.subtaskDao().insertAll(rows.map { it.copy(id = 0, taskId = taskId) })
        }
    }

    // ---- Habits --------------------------------------------------------------

    fun observeActiveHabits(): Flow<List<HabitEntity>> = db.habitDao().observeActive()
    fun observeAllHabits(): Flow<List<HabitEntity>> = db.habitDao().observeAll()
    fun observeCheckInsSince(fromDay: Long): Flow<List<HabitCheckInEntity>> =
        db.habitDao().observeCheckInsSince(fromDay)
    fun observeAllCheckIns(): Flow<List<HabitCheckInEntity>> = db.habitDao().observeAllCheckIns()

    suspend fun loadHabit(id: Long): HabitEntity? = db.habitDao().byId(id)

    suspend fun saveHabit(habit: HabitEntity): Long {
        if (habit.id == 0L) return db.habitDao().insert(habit.copy(createdAt = nowMillis))
        db.habitDao().update(habit)
        return habit.id
    }

    suspend fun deleteHabit(id: Long) = db.habitDao().deleteById(id)

    suspend fun setCheckedIn(habitId: Long, day: LocalDate, checked: Boolean) {
        if (checked) {
            db.habitDao().checkIn(HabitCheckInEntity(habitId, Dates.toEpochDay(day), nowMillis))
        } else {
            db.habitDao().uncheck(habitId, Dates.toEpochDay(day))
        }
    }

    // ---- Notes ---------------------------------------------------------------

    fun observeNotes(): Flow<List<NoteEntity>> = db.noteDao().observeAll()
    suspend fun loadNote(id: Long): NoteEntity? = db.noteDao().byId(id)

    suspend fun saveNote(note: NoteEntity): Long {
        val stamp = nowMillis
        return if (note.id == 0L) {
            db.noteDao().insert(note.copy(createdAt = stamp, updatedAt = stamp))
        } else {
            db.noteDao().update(note.copy(updatedAt = stamp))
            note.id
        }
    }

    suspend fun deleteNote(id: Long) = db.noteDao().deleteById(id)
    suspend fun setNotePinned(id: Long, pinned: Boolean) = db.noteDao().setPinned(id, pinned)
    suspend fun setNoteFavorite(id: Long, favorite: Boolean) = db.noteDao().setFavorite(id, favorite)

    // ---- Diary ---------------------------------------------------------------

    fun observeDiary(): Flow<List<DiaryEntryEntity>> = db.diaryDao().observeAll()
    suspend fun loadDiary(day: LocalDate): DiaryEntryEntity? = db.diaryDao().byDay(Dates.toEpochDay(day))

    suspend fun saveDiary(day: LocalDate, mood: Int?, text: String) {
        db.diaryDao().upsert(DiaryEntryEntity(Dates.toEpochDay(day), mood, text, nowMillis))
    }

    suspend fun deleteDiary(day: LocalDate) = db.diaryDao().deleteByDay(Dates.toEpochDay(day))

    // ---- Goals ---------------------------------------------------------------

    fun observeGoals(): Flow<List<GoalEntity>> = db.goalDao().observeAll()

    suspend fun saveGoal(goal: GoalEntity): Long {
        if (goal.id == 0L) return db.goalDao().insert(goal.copy(createdAt = nowMillis))
        db.goalDao().update(goal)
        return goal.id
    }

    suspend fun setGoalProgress(id: Long, progress: Int) =
        db.goalDao().setProgress(id, progress.coerceIn(0, 100))

    suspend fun deleteGoal(id: Long) = db.goalDao().deleteById(id)

    // ---- Stats ---------------------------------------------------------------

    /** Non-recurring tasks completed between two epoch days (inclusive start). */
    fun completedOneOffsBetween(fromDay: Long, toDay: Long): Flow<Int> {
        val start = dayToMillis(fromDay)
        val end = dayToMillis(toDay + 1)
        return db.taskDao().countCompletedBetween(start, end)
    }

    fun recurringCompletionsBetween(fromDay: Long, toDay: Long): Flow<Int> =
        db.taskDao().countRecurringCompletionsBetween(fromDay, toDay)

    fun totalTasks(): Int = kotlinx.coroutines.runBlocking { db.taskDao().totalTasks() }

    private fun dayToMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ---- Reminders -------------------------------------------------------------

    /**
     * Non-recurring, uncompleted tasks with a due time on or after [todayEpoch];
     * the scheduler turns these into notifications.
     */
    suspend fun upcomingTimedTasks(todayEpoch: Long): List<TaskEntity> =
        db.taskDao().observeAll().first().filter { t ->
            !t.isRecurring && t.completedAt == null && t.dueTime != null &&
                (t.dueDay ?: Long.MAX_VALUE) >= todayEpoch
        }

    // ---- Whole-database operations ---------------------------------------------

    suspend fun deleteAllData() {
        db.noteDao().clear()
        db.diaryDao().clear()
        db.goalDao().clear()
        db.taskDao().clearCompletions()
        db.subtaskDao().clear()
        db.taskDao().clear()
        db.habitDao().clearCheckIns()
        db.habitDao().clear()
    }

    // Bulk access for backup/restore ---------------------------------------------

    suspend fun exportAll(): DbSnapshot = DbSnapshot(
        tasks = db.taskDao().observeAll().first(),
        subtasks = db.subtaskDao().observeAll().first(),
        completions = db.taskDao().observeAllCompletions().first(),
        habits = db.habitDao().observeAll().first(),
        checkIns = db.habitDao().observeAllCheckIns().first(),
        notes = db.noteDao().observeAll().first(),
        diary = db.diaryDao().observeAll().first(),
        goals = db.goalDao().observeAll().first(),
    )

    suspend fun importAll(snapshot: DbSnapshot, wipeFirst: Boolean) {
        if (wipeFirst) deleteAllData()
        db.taskDao().insertAll(snapshot.tasks)
        db.subtaskDao().insertAll(snapshot.subtasks)
        db.taskDao().insertCompletions(snapshot.completions)
        db.habitDao().insertAll(snapshot.habits)
        db.habitDao().insertCheckIns(snapshot.checkIns)
        db.noteDao().insertAll(snapshot.notes)
        db.diaryDao().insertAll(snapshot.diary)
        db.goalDao().insertAll(snapshot.goals)
    }

    suspend fun habitStats(): Pair<Int, Int> = kotlinx.coroutines.runBlocking {
        db.habitDao().totalCheckIns() to 0
    }
}

data class DbSnapshot(
    val tasks: List<TaskEntity>,
    val subtasks: List<SubtaskEntity>,
    val completions: List<TaskCompletionEntity>,
    val habits: List<HabitEntity>,
    val checkIns: List<HabitCheckInEntity>,
    val notes: List<NoteEntity>,
    val diary: List<DiaryEntryEntity>,
    val goals: List<GoalEntity>,
)
