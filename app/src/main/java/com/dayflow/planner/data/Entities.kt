package com.dayflow.planner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/** Delimiter-safe list-of-strings <-> single column conversion for tags. */
class StringListConverter {
    @TypeConverter
    fun fromList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList()
        else value.split('\u001F').map { it.trim() }.filter { it.isNotEmpty() }

    @TypeConverter
    fun toList(value: List<String>?): String = value?.joinToString("\u001F") { it } ?: ""
}

/** Low = 0, Medium = 1, High = 2. */
object Priorities {
    const val LOW = 0
    const val MEDIUM = 1
    const val HIGH = 2
    val LABELS = listOf("Low", "Medium", "High")
    fun label(p: Int): String = LABELS.getOrElse(p) { "Medium" }
}

@Entity(
    tableName = "tasks",
    indices = [Index("dueDay"), Index("recurrence")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** See [Priorities]. */
    val priority: Int = Priorities.MEDIUM,
    /** Epoch day the one-off task is due on; ignored for recurring templates. */
    val dueDay: Long? = null,
    /** Minutes after midnight; null = all-day. */
    val dueTime: Int? = null,
    /** Completion timestamp (epoch millis) for non-recurring tasks; null = open. */
    val completedAt: Long? = null,
    /** One of NONE / DAILY / WEEKLY / MONTHLY. */
    val recurrence: String = "NONE",
    /** Recurrence anchor (epoch day). Also the "created on" day for one-offs. */
    val anchorDay: Long = 0L,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val isRecurring: Boolean get() = recurrence != "NONE"
}

/** Completion of a recurring occurrence (one row per task per day). */
@Entity(
    tableName = "task_completions",
    primaryKeys = ["taskId", "dayEpoch"],
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class TaskCompletionEntity(
    val taskId: Long,
    val dayEpoch: Long,
    val completedAt: Long,
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskId")],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val position: Int = 0,
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** Small emoji shown on the chip. */
    val emoji: String = "✅",
    /** ARGB color picked from a preset palette. */
    val colorArgb: Long = 0xFF5B5BD6L,
    /** Bit 0 = Monday … bit 6 = Sunday (see HabitSchedule). */
    val daysMask: Int = 0b1111111,
    val archived: Boolean = false,
    val createdAt: Long = 0L,
)

@Entity(
    tableName = "habit_checkins",
    primaryKeys = ["habitId", "dayEpoch"],
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class HabitCheckInEntity(
    val habitId: Long,
    val dayEpoch: Long,
    val createdAt: Long = 0L,
)

@Entity(tableName = "notes", indices = [Index("pinned"), Index("updatedAt")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "diary_entries", primaryKeys = ["dayEpoch"])
data class DiaryEntryEntity(
    /** The day this entry belongs to (one entry per day). */
    val dayEpoch: Long,
    /** 0..4 (rough → great), null = no mood picked. */
    val mood: Int? = null,
    val text: String = "",
    val updatedAt: Long = 0L,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    /** Optional deadline (epoch day). */
    val deadlineDay: Long? = null,
    /** 0..100, adjusted manually with a slider. */
    val progress: Int = 0,
    val done: Boolean = false,
    val createdAt: Long = 0L,
)
