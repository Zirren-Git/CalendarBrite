package com.dayflow.planner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TaskEntity::class,
        TaskCompletionEntity::class,
        SubtaskEntity::class,
        HabitEntity::class,
        HabitCheckInEntity::class,
        NoteEntity::class,
        DiaryEntryEntity::class,
        GoalEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(StringListConverter::class)
abstract class DayFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun habitDao(): HabitDao
    abstract fun noteDao(): NoteDao
    abstract fun diaryDao(): DiaryDao
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile
        private var instance: DayFlowDatabase? = null

        fun get(context: Context): DayFlowDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DayFlowDatabase::class.java,
                "dayflow.db",
            ).build().also { instance = it }
        }
    }
}
