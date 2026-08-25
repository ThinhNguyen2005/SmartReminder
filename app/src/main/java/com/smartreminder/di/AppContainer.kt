package com.smartreminder.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.smartreminder.data.preferences.DataStoreUserPreferencesRepository
import com.smartreminder.domain.repository.UserPreferencesRepository

/**
 * Application-scoped manual DI container.
 * Single DataStore instance per process as required by AndroidX DataStore.
 */
class AppContainer(private val context: Context) {

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        DataStoreUserPreferencesRepository(context.cueDataStore)
    }
}

/** Top-level DataStore delegate — guarantees single instance per file name. */
private val Context.cueDataStore: DataStore<Preferences> by preferencesDataStore(name = "cue_settings")
