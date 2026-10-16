package com.dayflow.planner.data

/** Repository façade over the Room database. */
class Repository(private val db: DayFlowDatabase) {
    suspend fun taskCount(): Int = db.taskDao().count()
}
