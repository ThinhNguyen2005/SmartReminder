package com.smartreminder.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.smartreminder.data.local.room.CueDatabase
import com.smartreminder.data.local.room.repository.RoomRoutineRepository
import com.smartreminder.data.local.room.repository.RoomScheduleGroupRepository
import com.smartreminder.data.local.room.repository.RoomCollaborationCacheDataSource
import com.smartreminder.data.local.datastore.DataStoreUserPreferencesRepository
import com.smartreminder.data.remote.collaboration.SupabaseCollaborationRemoteDataSource
import com.smartreminder.data.repository.collaboration.DefaultCollaborationRepository
import com.smartreminder.data.remote.SupabaseManager
import com.smartreminder.data.remote.preferences.SupabaseUserPreferencesCloudRepository
import com.smartreminder.data.sync.DefaultUserPreferencesSyncCoordinator
import com.smartreminder.domain.repository.CollaborationRepository
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.ScheduleGroupRepository
import com.smartreminder.domain.repository.UserPreferencesCloudRepository
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.sync.UserPreferencesSyncCoordinator
import com.smartreminder.ui.groups.GroupsViewModelFactory
import kotlinx.coroutines.flow.map

/**
 * Application-scoped manual DI container.
 * Manages singletons for DataStore, Room CueDatabase, and Supabase Sync.
 */
class AppContainer(private val context: Context) {

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        DataStoreUserPreferencesRepository(context.cueDataStore)
    }

    val userPreferencesCloudRepository: UserPreferencesCloudRepository by lazy {
        SupabaseUserPreferencesCloudRepository { SupabaseManager.client }
    }

    val userPreferencesSyncCoordinator: UserPreferencesSyncCoordinator by lazy {
        DefaultUserPreferencesSyncCoordinator(
            localRepository = userPreferencesRepository,
            cloudRepository = userPreferencesCloudRepository,
            getCurrentUserId = { SupabaseManager.currentUserIdOrNull() },
            signOutAuth = { SupabaseManager.signOut() },
            clearCollaborationCache = { collaborationRepository.clearSessionCache() }
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

    val collaborationRepository: CollaborationRepository by lazy {
        DefaultCollaborationRepository(
            cache = RoomCollaborationCacheDataSource(cueDatabase),
            remote = SupabaseCollaborationRemoteDataSource.configured(),
            network = ::hasValidatedNetwork,
            getCurrentUserId = { SupabaseManager.currentUserIdOrNull()?.let(::UserId) },
            observeUserId = {
                SupabaseManager.observeCurrentUserId().map { it?.let(::UserId) }
            }
        )
    }

    val groupsViewModelFactory: GroupsViewModelFactory by lazy {
        GroupsViewModelFactory(collaborationRepository)
    }

    private fun hasValidatedNetwork(): Boolean {
        return hasValidatedNetworkOrAssumeAvailable {
            context.getSystemService(ConnectivityManager::class.java)
        }
    }
}

/**
 * Connectivity is an optimization only. If the normal permission is absent, let the repository
 * attempt the remote call so transport/configuration errors remain typed instead of crashing.
 */
internal fun hasValidatedNetworkOrAssumeAvailable(
    connectivityManagerProvider: () -> ConnectivityManager?
): Boolean = try {
    val connectivityManager = connectivityManagerProvider() ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
} catch (_: SecurityException) {
    true
}

/** Top-level DataStore delegate — guarantees single instance per file name. */
private val Context.cueDataStore: DataStore<Preferences> by preferencesDataStore(name = "cue_settings")
