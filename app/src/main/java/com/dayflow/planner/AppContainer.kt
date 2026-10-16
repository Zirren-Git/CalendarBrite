package com.dayflow.planner

import android.content.Context
import com.dayflow.planner.data.DayFlowDatabase
import com.dayflow.planner.data.Repository
import com.dayflow.planner.settings.SettingsStore

/** Tiny hand-rolled dependency container (no DI framework needed). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val db: DayFlowDatabase = DayFlowDatabase.get(appContext)
    val repository: Repository = Repository(db)
    val settings: SettingsStore = SettingsStore(appContext)
}
