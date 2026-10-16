package com.dayflow.planner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY completedAt IS NOT NULL, COALESCE(dueTime, 1440), priority DESC, id DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun clear()

    @Query("UPDATE tasks SET completedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun setCompleted(id: Long, at: Long?)

    @Query("UPDATE tasks SET dueDay = :day, updatedAt = :now WHERE id = :id")
    suspend fun moveToDay(id: Long, day: Long?, now: Long)

    // Completions -------------------------------------------------------------

    @Query("SELECT * FROM task_completions WHERE dayEpoch >= :fromDay")
    fun observeCompletionsSince(fromDay: Long): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions")
    fun observeAllCompletions(): Flow<List<TaskCompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCompletion(row: TaskCompletionEntity)

    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND dayEpoch = :day")
    suspend fun removeCompletion(taskId: Long, day: Long)

    @Query("DELETE FROM task_completions")
    suspend fun clearCompletions()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletions(rows: List<TaskCompletionEntity>)

    // Stats -------------------------------------------------------------------

    @Query("SELECT COUNT(*) FROM tasks WHERE completedAt IS NOT NULL AND completedAt >= :fromMillis AND completedAt < :toMillis AND recurrence = 'NONE'")
    fun countCompletedBetween(fromMillis: Long, toMillis: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM task_completions WHERE dayEpoch >= :fromDay AND dayEpoch <= :toDay")
    fun countRecurringCompletionsBetween(fromDay: Long, toDay: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun totalTasks(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE completedAt IS NOT NULL OR recurrence != 'NONE'")
    suspend fun totalDoneOrRecurring(): Int
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks ORDER BY position, id")
    fun observeAll(): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position, id")
    suspend fun forTask(taskId: Long): List<SubtaskEntity>

    @Insert
    suspend fun insert(subtask: SubtaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subtasks: List<SubtaskEntity>)

    @Update
    suspend fun update(subtask: SubtaskEntity)

    @Query("UPDATE subtasks SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun clearForTask(taskId: Long)

    @Query("DELETE FROM subtasks")
    suspend fun clear()
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY id")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY id")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun byId(id: Long): HabitEntity?

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(habits: List<HabitEntity>)

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("UPDATE habits SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM habits")
    suspend fun clear()

    // Check-ins ---------------------------------------------------------------

    @Query("SELECT * FROM habit_checkins WHERE dayEpoch >= :fromDay")
    fun observeCheckInsSince(fromDay: Long): Flow<List<HabitCheckInEntity>>

    @Query("SELECT * FROM habit_checkins")
    fun observeAllCheckIns(): Flow<List<HabitCheckInEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun checkIn(row: HabitCheckInEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckIns(rows: List<HabitCheckInEntity>)

    @Query("DELETE FROM habit_checkins WHERE habitId = :habitId AND dayEpoch = :day")
    suspend fun uncheck(habitId: Long, day: Long)

    @Query("DELETE FROM habit_checkins")
    suspend fun clearCheckIns()

    @Query("SELECT COUNT(*) FROM habit_checkins")
    suspend fun totalCheckIns(): Int
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes")
    suspend fun clear()

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries")
    fun observeAll(): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE dayEpoch = :day")
    suspend fun byDay(day: Long): DiaryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE dayEpoch = :day")
    suspend fun deleteByDay(day: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DiaryEntryEntity>)

    @Query("DELETE FROM diary_entries")
    suspend fun clear()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY done, id DESC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun byId(id: Long): GoalEntity?

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<GoalEntity>)

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET progress = :progress, done = (:progress >= 100) WHERE id = :id")
    suspend fun setProgress(id: Long, progress: Int)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM goals")
    suspend fun clear()
}
