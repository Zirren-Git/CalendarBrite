package com.dayflow.planner

import android.app.Application
import android.content.Context
import androidx.work.Configuration

/**
 * DayFlow Planner application class.
 *
 * The app is fully offline: no accounts, no networking, no analytics.
 * WorkManager uses the on-demand (Configuration.Provider) startup path; the
 * default ContentProvider initializer is removed in the manifest.
 */
class DayFlowApp : Application(), Configuration.Provider {

    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    companion object {
        fun from(context: Context): DayFlowApp = context.applicationContext as DayFlowApp

        /** buildConfig generation is disabled for this build; keep a local flag. */
        private const val DEBUG = false
    }
}
