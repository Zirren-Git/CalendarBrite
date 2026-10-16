package com.dayflow.planner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter

/** String-list <-> delimited-text storage for tags. */
object Converters {
    @TypeConverter
    fun fromTags(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList()
        else value.split('\u001F').filter { it.isNotBlank() }

    @TypeConverter
    fun toTags(tags: List<String>): String = tags.joinToString("\u001F")
}

@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DayFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun get(context: Context): DayFlowDatabase =
            Room.databaseBuilder(context, DayFlowDatabase::class.java, "dayflow.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
