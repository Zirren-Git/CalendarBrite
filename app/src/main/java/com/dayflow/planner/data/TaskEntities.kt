package com.dayflow.planner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@androidx.room.Entity(tableName = "tasks")
data class TaskEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
)

@Dao
interface TaskDao {
    @Insert suspend fun insert(task: TaskEntity): Long
    @Query("SELECT COUNT(*) FROM tasks") suspend fun count(): Int
}
