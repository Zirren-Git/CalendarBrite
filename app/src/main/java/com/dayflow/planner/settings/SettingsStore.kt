package com.dayflow.planner.settings

import android.content.Context
import androidx.annotation.IntDef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences kept in SharedPreferences and mirrored in StateFlows so
 * Compose recomposes immediately when something changes.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("dayflow_settings", Context.MODE_PRIVATE)

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    private val _themeMode = MutableStateFlow(ThemeMode.entries[prefs.getInt(KEY_THEME, 0)])
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentIndex = MutableStateFlow(prefs.getInt(KEY_ACCENT, 0).coerceIn(0, ACCENT_COUNT - 1))
    val accentIndex: StateFlow<Int> = _accentIndex.asStateFlow()

    private val _remindersEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS, true))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    private val _showCompletedInToday = MutableStateFlow(prefs.getBoolean(KEY_SHOW_DONE, true))
    val showCompletedInToday: StateFlow<Boolean> = _showCompletedInToday.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putInt(KEY_THEME, mode.ordinal).apply()
        _themeMode.value = mode
    }

    fun setAccent(index: Int) {
        val clamped = index.coerceIn(0, ACCENT_COUNT - 1)
        prefs.edit().putInt(KEY_ACCENT, clamped).apply()
        _accentIndex.value = clamped
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS, enabled).apply()
        _remindersEnabled.value = enabled
    }

    fun setShowCompletedInToday(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DONE, show).apply()
        _showCompletedInToday.value = show
    }

    /** Snapshot for the JSON export. */
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("themeMode", themeMode.value.name)
        put("accentIndex", accentIndex.value)
        put("remindersEnabled", remindersEnabled.value)
        put("showCompletedInToday", showCompletedInToday.value)
    }

    fun importJson(json: org.json.JSONObject?) {
        json ?: return
        val mode = json.optString("themeMode", null)?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        }
        mode?.let { setThemeMode(it) }
        if (json.has(KEY_ACCENT)) setAccent(json.optInt("accentIndex", 0))
        if (json.has("remindersEnabled")) setRemindersEnabled(json.optBoolean("remindersEnabled", true))
        if (json.has("showCompletedInToday")) setShowCompletedInToday(json.optBoolean("showCompletedInToday", true))
    }

    companion object {
        const val ACCENT_COUNT = 6
        private const val KEY_THEME = "themeMode"
        private const val KEY_ACCENT = "accentIndex"
        private const val KEY_REMINDERS = "remindersEnabled"
        private const val KEY_SHOW_DONE = "showCompletedInToday"
    }
}
