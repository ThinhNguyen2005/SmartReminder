package com.smartreminder.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.smartreminder.data.local.room.CueDatabase
import com.smartreminder.data.local.room.repository.RoomRoutineRepository
import com.smartreminder.data.local.room.repository.RoomScheduleGroupRepository
import com.smartreminder.data.local.datastore.DataStoreUserPreferencesRepository
import com.smartreminder.data.remote.SupabaseManager
import com.smartreminder.data.remote.preferences.SupabaseUserPreferencesCloudRepository
import com.smartreminder.data.sync.DefaultUserPreferencesSyncCoordinator
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.ScheduleGroupRepository
import com.smartreminder.domain.repository.UserPreferencesCloudRepository
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.sync.UserPreferencesSyncCoordinator

/**
 * Application-scoped manual DI container.
 * Manages singletons for DataStore, Room CueDatabase, and Supabase Sync.
 */
class AppContainer(private val context: Context) {

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        DataStoreUserPreferencesRepository(context.cueDataStore)
    }

    val userPreferencesCloudRepository: UserPreferencesCloudRepository by lazy {
        SupabaseUserPreferencesCloudRepository(SupabaseManager.client)
    }

    val userPreferencesSyncCoordinator: UserPreferencesSyncCoordinator by lazy {
        DefaultUserPreferencesSyncCoordinator(
            localRepository = userPreferencesRepository,
            cloudRepository = userPreferencesCloudRepository,
            supabase = SupabaseManager.client
        )
    }

    val cueDatabase: CueDatabase by lazy {
        CueDatabase.buildDatabase(context)
    }

    val scheduleGroupRepository: ScheduleGroupRepository by lazy {
        RoomScheduleGroupRepository(cueDatabase.scheduleGroupDao())
    }

    val routineRepository: RoutineRepository by lazy {
        RoomRoutineRepository(cueDatabase.routineDao())
    }
}

/** Top-level DataStore delegate — guarantees single instance per file name. */
private val Context.cueDataStore: DataStore<Preferences> by preferencesDataStore(name = "cue_settings")
