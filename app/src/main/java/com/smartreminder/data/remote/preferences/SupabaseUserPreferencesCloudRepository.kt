package com.smartreminder.data.remote.preferences

import android.util.Log
import com.smartreminder.domain.model.preferences.OnboardingPreferencesSnapshot
import com.smartreminder.domain.repository.UserPreferencesCloudRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

/**
 * Supabase PostgREST implementation of [UserPreferencesCloudRepository].
 * Interacts with public.user_preferences table under Row Level Security (RLS).
 */
class SupabaseUserPreferencesCloudRepository(
    private val supabase: SupabaseClient
) : UserPreferencesCloudRepository {

    override suspend fun getForUser(userId: String): OnboardingPreferencesSnapshot? {
        Log.d("SmartReminderAuth", "SupabaseCloudRepo: Querying table '$TABLE_NAME' for user_id=$userId...")
        val dto = supabase.from(TABLE_NAME)
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeSingleOrNull<UserPreferencesRemoteDto>()

        Log.d("SmartReminderAuth", "SupabaseCloudRepo: Fetched DTO=$dto")
        if (dto == null) return null

        return UserPreferencesRemoteMapper.toDomain(dto)
    }

    override suspend fun upsertForUser(
        userId: String,
        snapshot: OnboardingPreferencesSnapshot
    ) {
        val dto = UserPreferencesRemoteMapper.toDto(userId, snapshot)
        Log.d("SmartReminderAuth", "SupabaseCloudRepo: Upserting DTO=$dto")
        supabase.from(TABLE_NAME).upsert(dto)
        Log.d("SmartReminderAuth", "SupabaseCloudRepo: Upsert successful")
    }

    companion object {
        const val TABLE_NAME = "user_preferences"
    }
}
