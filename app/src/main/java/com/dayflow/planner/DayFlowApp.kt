package com.dayflow.planner

import android.app.Application
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
            .setMinimumLoggingLevel(if (BuildConfig_DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    private companion object {
        /** buildConfig generation is disabled; keep a tiny local flag instead. */
        const val BuildConfig_DEBUG = false
    }
}
