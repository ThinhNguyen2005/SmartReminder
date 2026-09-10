package com.smartreminder.data.local.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * DataStore preference key constants. Internal to data layer — domain never sees these.
 */
internal object PreferenceKeys {
    val WAKE_UP_MINUTE = intPreferencesKey("wake_up_minute")
    val SLEEP_MINUTE = intPreferencesKey("sleep_minute")
    val SELECTED_GOALS = stringSetPreferencesKey("selected_goals")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ROUTINE_REMINDERS = booleanPreferencesKey("routine_reminders")
    val TASK_REMINDERS = booleanPreferencesKey("task_reminders")
    val MORNING_BRIEFING = booleanPreferencesKey("morning_briefing")
    val QUIET_HOURS = booleanPreferencesKey("quiet_hours")
}
